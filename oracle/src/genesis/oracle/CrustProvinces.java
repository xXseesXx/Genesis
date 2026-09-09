package genesis.oracle;

import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;
import genesis.core.tectonics.PlateTopology;

/** Static correlated crustal provinces, not moving plates, watersheds or ocean reservations. */
public final class CrustProvinces {
    public static final String VERSION="crust-provinces-v1";
    public record Province(long id,long x,long z,int crust) {}
    private final long seed;
    private final int percent;
    public final int spacing;
    public CrustProvinces(long seed,int spacing,int percent) {
        if(spacing<8192||spacing>1048576||percent<0||percent>100)throw new IllegalArgumentException("Invalid province configuration");
        this.seed=Hash64.stream(seed,0x435255535450524fL);this.spacing=spacing;this.percent=percent;
    }
    public Province site(long i,long j) {
        long id=PlateTopology.key(i,j),x=Math.multiplyExact(i,spacing),z=Math.multiplyExact(j,spacing);
        long limit=Lattice.MAX_COORDINATE+4L*spacing;
        if(x< -limit||x>limit||z< -limit||z>limit)throw new IllegalArgumentException("Province outside internal halo");
        x+=spacing/4+Math.floorMod(Hash64.hash(seed,0,i,j),spacing/2+1);
        z+=spacing/4+Math.floorMod(Hash64.hash(seed,1,i,j),spacing/2+1);
        return new Province(id,x,z,Math.floorMod(Hash64.hash(seed,2,i,j),100)<percent?1:0);
    }
    /** Internal plate centers may be just outside the public world-coordinate guard. */
    public Province sample(long x,long z) {
        long limit=Lattice.MAX_COORDINATE+2L*spacing;
        if(x< -limit||x>limit||z< -limit||z>limit)throw new IllegalArgumentException("Province query outside internal support");
        long i=Math.floorDiv(x,spacing),j=Math.floorDiv(z,spacing),best=Long.MAX_VALUE;Province selected=null;
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++) {
            var p=site(i+dx,j+dz);long px=p.x-x,pz=p.z-z,d=px*px+pz*pz;
            if(d<best||d==best&&(selected==null||Long.compareUnsigned(p.id,selected.id)<0)){best=d;selected=p;}
        }
        return selected;
    }
}
