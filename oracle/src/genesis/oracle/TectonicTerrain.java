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
import java.util.LinkedHashMap;

/** Experimental absolute terrain from shared crust and plate motion; no hydrological terminals. */
public final class TectonicTerrain {
    public static final String VERSION="tectonic-terrain-v1";
    public record Settings(int crustRadiusPermille,int seaThreshold,int landHeight,int oceanDepth,
                           int forcingPermille,int detailHeight,int seaLevel,int crustProvinceScale) {
        public Settings {
            if(crustRadiusPermille<1100||crustRadiusPermille>2500||seaThreshold<200||seaThreshold>800
                ||landHeight<100||landHeight>6000||oceanDepth<100||oceanDepth>10000||forcingPermille<0||forcingPermille>3000
                ||detailHeight<0||detailHeight>1000||seaLevel< -2000||seaLevel>2000||(crustProvinceScale!=1&&crustProvinceScale!=4))
                throw new IllegalArgumentException("Unsupported tectonic terrain settings");
        }
        public static Settings defaults(){return new Settings(1500,500,1800,4200,1000,180,0,4);}
    }
    public record Crust(double fraction,int supportedSites) {}
    public record Sample(Plate owner,double crustFraction,double baseElevation,double detail,
                         double positiveForcing,double negativeForcing,double elevation,boolean land,
                         int crustSites,int junctionSites) {}
    private final PlateTopology topology;
    private final Noise detail;
    private final int transformRatio;
    private final CrustProvinces provinces;
    private final Map<Long,List<Plate>> cache=new LinkedHashMap<>(16,1,true);
    public final Settings settings;
    public final Params plateParams;
    public final long seed;
    /** Research preset only: does not alter Params defaults or either production world model. */
    public static Params defaultPlateParams(){return new Params(Map.of("plateSpacing",65536.0,"continentalPercent",53.0));}
    public TectonicTerrain(long seed,Params params,Settings settings) {
        if(params==null||settings==null)throw new IllegalArgumentException("Complete terrain configuration required");
        this.seed=seed;this.plateParams=params;this.settings=settings;topology=new PlateTopology(seed,params);
        transformRatio=params.integer("transformRatio");
        provinces=new CrustProvinces(seed,topology.spacing*settings.crustProvinceScale,params.integer("continentalPercent"));
        detail=new Noise(Hash64.stream(seed,0x5445434445544149L),new Params(Map.of("wavelength",(double)(topology.spacing/8),"octaves",4.0)));
    }
    public Sample sample(long x,long z) {
        Lattice.check(x);Lattice.check(z);long i=Lattice.cell(x,topology.spacing),j=Lattice.cell(z,topology.spacing);
        List<Plate> sites=neighborhood(i,j);Plate owner=null;long nearest=Long.MAX_VALUE;
        for(var site:sites) {
            long dx=site.x()-x,dz=site.z()-z,distance=dx*dx+dz*dz;
            int order=owner==null?-1:Long.compareUnsigned(Hash64.mix(site.id()),Hash64.mix(owner.id()));
            if(distance<nearest||distance==nearest&&(order<0||order==0&&Long.compareUnsigned(site.id(),owner.id())<0)){owner=site;nearest=distance;}
        }
        Crust crust=crust(sites,x,z,topology.spacing,settings.crustRadiusPermille);
        double threshold=settings.seaThreshold/1000.0;
        double signed=(crust.fraction-threshold)/(crust.fraction<threshold?threshold:1-threshold);
        double base=signed*(signed<0?settings.oceanDepth:settings.landHeight);
        double small=detail.sample(x,z)*settings.detailHeight*signed*signed;
        var forcing=JunctionForcing.compose(sites,x,z,topology.spacing,transformRatio);
        double gain=settings.forcingPermille/1000.0;
        double positive=forcing.positive()*gain,negative=forcing.negative()*gain;
        double elevation=base+small+positive+negative;
        return new Sample(owner,crust.fraction,base,small,positive,negative,elevation,elevation>settings.seaLevel,
            crust.supportedSites,forcing.supportedPlates());
    }
    /** Candidate site preserves existing identity/position/motion/age; only crust may be correlated. */
    public Plate plate(long i,long j) {
        var s=topology.site(i,j);int crust=settings.crustProvinceScale==1?s.crust:provinces.sample(s.x,s.z).crust();
        return new Plate(s.id,s.x,s.z,s.vx,s.vz,crust,s.age);
    }
    /** Closest-boundary diagnostic with candidate crust, not a selector for composed height. */
    public BoundaryForcing.Sample boundary(long x,long z) {
        var s=topology.sample(x,z);var edge=BoundaryForcing.describe(plate(s.owner.i,s.owner.j),plate(s.neighbor.i,s.neighbor.j),transformRatio);
        long dx=edge.second().x()-edge.first().x(),dz=edge.second().z()-edge.first().z();
        double length=StrictMath.sqrt(dx*dx+dz*dz);
        long projection=(2*(x-edge.first().x())-dx)*dx+(2*(z-edge.first().z())-dz)*dz;
        int u=(int)Math.max(-1000,Math.min(1000,projection*1000/(2*length*(topology.spacing/4.0))));
        return new BoundaryForcing.Sample(edge,u,BoundaryForcing.anomaly(edge,u));
    }
    private synchronized List<Plate> neighborhood(long i,long j) {
        long key=PlateTopology.key(i,j);var found=cache.get(key);if(found!=null)return found;
        List<Plate> sites=new ArrayList<>(49);for(int dz=-3;dz<=3;dz++)for(int dx=-3;dx<=3;dx++)sites.add(plate(i+dx,j+dz));
        var result=List.copyOf(sites);cache.put(key,result);if(cache.size()>64)cache.remove(cache.keySet().iterator().next());return result;
    }
    /** Supplied-neighborhood reference entry point; complete support is the caller's responsibility. */
    public static Crust crust(List<Plate> supplied,long x,long z,int spacing,int radiusPermille) {
        Lattice.check(x);Lattice.check(z);
        if(supplied==null||supplied.isEmpty()||supplied.size()>121||spacing<8192||spacing>262144||radiusPermille<1100||radiusPermille>2500)
            throw new IllegalArgumentException("Invalid crust support");
        var sites=new ArrayList<>(supplied);
        for(var site:sites)if(site==null)throw new IllegalArgumentException("Null crust site");
        sites.sort(Comparator.comparing(Plate::id,Long::compareUnsigned));
        double radius=spacing*(double)radiusPermille/1000,radiusSquared=radius*radius,total=0,continental=0;int supported=0;
        for(int k=0;k<sites.size();k++) {
            var site=sites.get(k);if(k>0&&sites.get(k-1).id()==site.id())throw new IllegalArgumentException("Duplicate crust identity");
            long dx=site.x()-x,dz=site.z()-z;
            if(Math.abs(dx)>4194304||Math.abs(dz)>4194304)throw new IllegalArgumentException("Crust fixture outside arithmetic support");
            long distance=dx*dx+dz*dz;if(distance>=radiusSquared)continue;
            double t=1-distance/radiusSquared,weight=t*t*t;total+=weight;continental+=weight*site.crust();supported++;
        }
        if(total==0)throw new IllegalArgumentException("Incomplete crust support");
        return new Crust(continental/total,supported);
    }
}
