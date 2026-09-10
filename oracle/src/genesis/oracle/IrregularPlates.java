package genesis.oracle;

import genesis.core.Params;
import genesis.core.fields.Noise;
import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;
import genesis.core.tectonics.PlateTopology;
import genesis.oracle.BoundaryForcing.Plate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Sparse, bounded-support plate partition for the experimental terrain.
 *
 * Ownership is an additively weighted power diagram evaluated after a shared
 * smooth coordinate warp. A small plate-specific score perturbation roughens
 * individual edges. Neither term depends on a viewport, cache, or query order.
 */
public final class IrregularPlates {
    public static final String VERSION="irregular-plates-v1";
    private static final long DOMAIN=0x495252504c415445L;
    private static final int SCORE_Q=1024;
    private final long seed;
    private final int jitter,speed,ageMax,warpPermille,roughnessPermille;
    private final Noise warpX,warpZ;
    public final int spacing;

    public record Site(long i,long j,long id,long x,long z,int vx,int vz,int age,
                       int scalePermille,int powerWeightQ,int datumQ,int tiltXQ,int tiltZQ,
                       boolean recentFracture) {
        public Plate plate(int localCrust){return new Plate(id,x,z,vx,vz,localCrust,age);}
    }
    public record Scored(Site site,long score) {}
    public record Sample(Site owner,Site neighbor,long warpedX,long warpedZ,List<Scored> candidates) {}

    public IrregularPlates(long worldSeed,Params params,int warpPermille,int roughnessPermille) {
        if(params==null||warpPermille<0||warpPermille>300||roughnessPermille<0||roughnessPermille>200)
            throw new IllegalArgumentException("Invalid irregular plate configuration");
        seed=Hash64.stream(worldSeed,DOMAIN);spacing=params.integer("plateSpacing");
        jitter=params.integer("plateJitter");speed=params.integer("plateSpeed");ageMax=params.integer("crustAgeMax");
        this.warpPermille=warpPermille;this.roughnessPermille=roughnessPermille;
        int wavelength=Math.max(16,spacing/2);
        warpX=new Noise(Hash64.stream(seed,0x5741525058L),new Params(Map.of("wavelength",(double)wavelength,"octaves",3.0)));
        warpZ=new Noise(Hash64.stream(seed,0x574152505aL),new Params(Map.of("wavelength",(double)wavelength,"octaves",3.0)));
    }

    private long offset(long hash) {
        long extent=(long)spacing*jitter/(2*100);
        return spacing/2L+Math.floorMod(hash,2*extent+1)-extent;
    }
    public Site site(long i,long j) {
        long id=PlateTopology.key(i,j),h=Hash64.hash(seed,0,i,j);
        long x=Math.addExact(Math.multiplyExact(i,spacing),offset(h));
        long z=Math.addExact(Math.multiplyExact(j,spacing),offset(Hash64.mix(h)));
        int vx=(int)Math.floorMod(Hash64.hash(seed,1,i,j),2*speed+1)-speed;
        int vz=(int)Math.floorMod(Hash64.hash(seed,2,i,j),2*speed+1)-speed;
        int roll=(int)Math.floorMod(Hash64.hash(seed,3,i,j),100),scale,weight;boolean fracture=false;
        if(roll<12){scale=600+(int)Math.floorMod(Hash64.hash(seed,4,i,j),151);weight=-700+(scale-600)*200/150;fracture=true;}
        else if(roll<30){scale=760+(int)Math.floorMod(Hash64.hash(seed,4,i,j),141);weight=-350+(scale-760)*200/140;}
        else if(roll<78){scale=920+(int)Math.floorMod(Hash64.hash(seed,4,i,j),201);weight=-100+(scale-920)*320/200;}
        else if(roll<94){scale=1200+(int)Math.floorMod(Hash64.hash(seed,4,i,j),201);weight=450+(scale-1200)*300/200;}
        else {scale=1500+(int)Math.floorMod(Hash64.hash(seed,4,i,j),301);weight=900+(scale-1500)*350/300;}
        int age=fracture?(int)Math.floorMod(Hash64.hash(seed,5,i,j),Math.max(2,ageMax/12+1))
            :(int)Math.floorMod(Hash64.hash(seed,5,i,j),ageMax+1);
        int datum=(int)Math.floorMod(Hash64.hash(seed,6,i,j),2049)-1024;
        int tiltX=(int)Math.floorMod(Hash64.hash(seed,7,i,j),2049)-1024;
        int tiltZ=(int)Math.floorMod(Hash64.hash(seed,8,i,j),2049)-1024;
        return new Site(i,j,id,x,z,vx,vz,age,scale,weight,datum,tiltX,tiltZ,fracture);
    }

    public Sample sample(long x,long z){return sample(x,z,3);}
    /** Cheap conservative rejection for a finite family. False means it cannot own this point. */
    public boolean mayOwn(List<ContinentalGroups.Cell> family,long x,long z) {
        Lattice.check(x);Lattice.check(z);long amplitude=(long)spacing*warpPermille/1000;
        long wx=x+Math.round(warpX.sample(x,z)*amplitude),wz=z+Math.round(warpZ.sample(x,z)*amplitude);
        long homeScore=score(site(Math.floorDiv(x,spacing),Math.floorDiv(z,spacing)),wx,wz,x,z);
        for(var cell:family)if(score(site(cell.i(),cell.j()),wx,wz,x,z)<=homeScore)return true;
        return false;
    }
    /** Larger radii are exposed only for completeness tests of the fixed support. */
    public Sample sample(long x,long z,int radius) {
        Lattice.check(x);Lattice.check(z);if(radius<3||radius>5)throw new IllegalArgumentException("Plate support radius must be 3..5");
        long amplitude=(long)spacing*warpPermille/1000;
        long wx=Math.addExact(x,Math.round(warpX.sample(x,z)*amplitude));
        long wz=Math.addExact(z,Math.round(warpZ.sample(x,z)*amplitude));
        long ci=Math.floorDiv(x,spacing),cj=Math.floorDiv(z,spacing);
        var candidates=new ArrayList<Scored>((2*radius+1)*(2*radius+1));
        for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++) {
            Site site=site(ci+dx,cj+dz);candidates.add(new Scored(site,score(site,wx,wz,x,z)));
        }
        candidates.sort(Comparator.<Scored>comparingLong(Scored::score).thenComparing((a,b)->precedes(a.site,b.site)?-1:precedes(b.site,a.site)?1:0));
        return new Sample(candidates.get(0).site,candidates.get(1).site,wx,wz,List.copyOf(candidates));
    }

    private long score(Site site,long wx,long wz,long x,long z) {
        long dx=site.x-wx,dz=site.z-wz,distance=Math.addExact(Math.multiplyExact(dx,dx),Math.multiplyExact(dz,dz));
        long spacing2=(long)spacing*spacing;
        return Math.addExact(Math.subtractExact(Math.multiplyExact(distance,SCORE_Q),Math.multiplyExact(site.powerWeightQ,spacing2)),
            Math.multiplyExact(edgeNoiseQ(site,x,z),spacing2));
    }
    private long edgeNoiseQ(Site site,long x,long z) {
        if(roughnessPermille==0)return 0;
        int wavelength=Math.max(16,spacing/6);long i=Math.floorDiv(x,wavelength),j=Math.floorDiv(z,wavelength);
        double u=fade(Math.floorMod(x,wavelength)/(double)wavelength),v=fade(Math.floorMod(z,wavelength)/(double)wavelength);
        long local=Hash64.stream(seed,site.id);
        double a=Hash64.signedUnit(Hash64.hash(local,0,i,j)),b=Hash64.signedUnit(Hash64.hash(local,0,i+1,j));
        double c=Hash64.signedUnit(Hash64.hash(local,0,i,j+1)),d=Hash64.signedUnit(Hash64.hash(local,0,i+1,j+1));
        return Math.round((lerp(lerp(a,b,u),lerp(c,d,u),v))*roughnessPermille);
    }
    private static boolean precedes(Site a,Site b) {
        int c=Long.compareUnsigned(Hash64.mix(a.id),Hash64.mix(b.id));
        return c<0||c==0&&Long.compareUnsigned(a.id,b.id)<0;
    }
    private static double fade(double t){return t*t*t*(t*(t*6-15)+10);}
    private static double lerp(double a,double b,double t){return a+(b-a)*t;}
}
