package genesis.oracle;

import genesis.core.Params;
import genesis.core.fields.Noise;
import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;
import genesis.oracle.BoundaryForcing.Plate;
import genesis.oracle.ContinentalGroups.Group;
import genesis.oracle.IrregularPlates.Scored;
import genesis.oracle.IrregularPlates.Site;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Experimental sparse-plate terrain; deterministic and bounded, with no hydrological terminals. */
public final class TectonicTerrain {
    public static final String VERSION="tectonic-terrain-v2";
    public record Settings(int coastBlendPermille,int seaThreshold,int landHeight,int oceanDepth,
                           int forcingPermille,int detailHeight,int seaLevel,int plateWarpPermille,
                           int plateRoughnessPermille,int plateRelief,int plateTilt) {
        public Settings {
            if(coastBlendPermille<40||coastBlendPermille>300||seaThreshold<200||seaThreshold>800
                ||landHeight<100||landHeight>6000||oceanDepth<100||oceanDepth>10000||forcingPermille<0||forcingPermille>3000
                ||detailHeight<0||detailHeight>1000||seaLevel< -2000||seaLevel>2000||plateWarpPermille<0||plateWarpPermille>300
                ||plateRoughnessPermille<0||plateRoughnessPermille>200||plateRelief<0||plateRelief>1200||plateTilt<0||plateTilt>1200)
                throw new IllegalArgumentException("Unsupported tectonic terrain settings");
        }
        public static Settings defaults(){return new Settings(120,500,1800,4200,1000,180,0,220,140,450,600);}
    }
    /** Retained as a supplied-fixture helper for comparison with the v1 experiment. */
    public record Crust(double fraction,int supportedSites) {}
    public record Sample(Plate owner,long continentId,int continentPlateCount,double crustFraction,
                         double baseElevation,double plateSurface,double detail,double positiveForcing,
                         double negativeForcing,double elevation,boolean land,int crustSites,int junctionSites,
                         int plateScalePermille,double plateDatum,double plateTiltX,double plateTiltZ,
                         boolean recentFracture,double boundaryDistance) {}
    private record CrustState(double fraction,double rawSigned,double envelope,Group group) {}
    private final IrregularPlates plates;
    private final ContinentalGroups groups;
    private final Noise detail;
    private final int transformRatio,coverage;
    public final Settings settings;
    public final Params plateParams;
    public final long seed;

    /** Research preset only: sixty-four times fewer nominal plates per area than the v1 preset. */
    public static Params defaultPlateParams(){return new Params(Map.of("plateSpacing",524288.0,"continentalPercent",53.0));}
    public TectonicTerrain(long seed,Params params,Settings settings) {
        if(params==null||settings==null)throw new IllegalArgumentException("Complete terrain configuration required");
        this.seed=seed;this.plateParams=params;this.settings=settings;transformRatio=params.integer("transformRatio");coverage=params.integer("continentalPercent");
        plates=new IrregularPlates(seed,params,settings.plateWarpPermille,settings.plateRoughnessPermille);groups=new ContinentalGroups(seed);
        detail=new Noise(Hash64.stream(seed,0x5445434445544149L),new Params(Map.of("wavelength",(double)Math.max(16,plates.spacing/8),"octaves",4.0)));
    }

    public Sample sample(long x,long z) {
        return sample(x,z,3);
    }
    /** Extended support is a diagnostic for comparison with the normal bounded query. */
    public Sample sample(long x,long z,int supportRadius) {
        Lattice.check(x);Lattice.check(z);var partition=plates.sample(x,z,supportRadius);Site site=partition.owner();
        CrustState crust=continental(site,partition.candidates(),x,z,true);
        double threshold=settings.seaThreshold/1000.0;
        double signed=(crust.fraction-threshold)/(crust.fraction<threshold?threshold:1-threshold);
        double envelope=crust.envelope;
        double plateSurface=plateSurface(partition.candidates(),x,z)*envelope;
        double base=signed*(signed<0?settings.oceanDepth:settings.landHeight)*envelope
            -Math.max(4000,settings.oceanDepth)*(1-envelope)+plateSurface;
        double small=detail.sample(x,z)*settings.detailHeight*signed*signed*envelope;
        var forcing=forcing(partition,x,z);double gain=settings.forcingPermille/1000.0;
        double positive=forcing.positive()*gain*envelope,negative=forcing.negative()*gain*envelope,elevation=base+small+positive+negative;
        Plate owner=site.plate(crust.rawSigned>=0?1:0);double boundary=boundaryDistance(partition);
        return new Sample(owner,crust.group.id(),crust.group.size(),crust.fraction,base,plateSurface,small,positive,negative,elevation,
            elevation>settings.seaLevel,crust.group.size(),forcing.supportedPlates(),site.scalePermille(),
            site.datumQ()*settings.plateRelief/1024.0,site.tiltXQ()*settings.plateTilt/1024.0,
            site.tiltZQ()*settings.plateTilt/1024.0,site.recentFracture(),boundary);
    }

    public Site plateSite(long i,long j){return plates.site(i,j);}
    /** Query-independent plate descriptor uses the local crust state at its own anchor. */
    public Plate plate(long i,long j) {
        Site site=plates.site(i,j);var candidates=plates.sample(site.x(),site.z()).candidates();
        return site.plate(continental(site,candidates,site.x(),site.z(),false).rawSigned>=0?1:0);
    }
    public Group continent(long i,long j){return groups.group(i,j);}

    /** Closest competing power cell with candidate-local crust. */
    public BoundaryForcing.Sample boundary(long x,long z) {
        var partition=plates.sample(x,z);Scored a=partition.candidates().get(0),b=partition.candidates().get(1);
        Plate pa=a.site().plate(continental(a.site(),partition.candidates(),x,z,false).rawSigned>=0?1:0);
        Plate pb=b.site().plate(continental(b.site(),partition.candidates(),x,z,false).rawSigned>=0?1:0);
        var edge=BoundaryForcing.describe(pa,pb,transformRatio);double u=pairOffset(edge,a,b);
        int quantized=(int)Math.max(-1000,Math.min(1000,Math.round(u*1000)));
        return new BoundaryForcing.Sample(edge,quantized,BoundaryForcing.anomaly(edge,quantized));
    }

    private JunctionForcing.Sample forcing(IrregularPlates.Sample partition,long x,long z) {
        List<Scored> active=new ArrayList<>();long nearest=partition.candidates().get(0).score();double band=plates.spacing*(double)plates.spacing*512;
        List<Double> weights=new ArrayList<>();
        for(Scored scored:partition.candidates()) {
            double t=(scored.score()-nearest)/band;if(t>=1)break;
            active.add(scored);weights.add((1-t)*(1-t)*(1+2*t));
        }
        double positive=0,negative=0,total=0;int pairs=0;
        for(int ai=0;ai<active.size();ai++)for(int bi=ai+1;bi<active.size();bi++) {
            Scored a=active.get(ai),b=active.get(bi);double weight=weights.get(ai)*weights.get(bi);if(weight==0)continue;
            int ac=continental(a.site(),partition.candidates(),x,z,false).rawSigned>=0?1:0;
            int bc=continental(b.site(),partition.candidates(),x,z,false).rawSigned>=0?1:0;
            var edge=BoundaryForcing.describe(a.site().plate(ac),b.site().plate(bc),transformRatio);
            double height=JunctionForcing.profile(edge,pairOffset(edge,a,b))*weight;total+=weight;pairs++;
            if(height>0)positive+=height;else negative+=height;
        }
        double denominator=Math.max(1,total);
        return new JunctionForcing.Sample((positive+negative)/denominator,positive/denominator,negative/denominator,active.size(),pairs);
    }
    private double pairOffset(BoundaryForcing.Edge edge,Scored a,Scored b) {
        long first=edge.first().id()==a.site().id()?a.score():b.score(),second=edge.second().id()==a.site().id()?a.score():b.score();
        double dx=edge.second().x()-edge.first().x(),dz=edge.second().z()-edge.first().z(),length=StrictMath.sqrt(dx*dx+dz*dz);
        return (first-second)/(2048.0*length*(plates.spacing/4.0));
    }
    private double boundaryDistance(IrregularPlates.Sample partition) {
        Scored a=partition.candidates().get(0),b=partition.candidates().get(1);
        double dx=b.site().x()-a.site().x(),dz=b.site().z()-a.site().z();
        return Math.max(0,(b.score()-a.score())/(2048.0*StrictMath.sqrt(dx*dx+dz*dz)));
    }

    private double plateSurface(List<Scored> candidates,long x,long z) {
        long nearest=candidates.get(0).score();double band=plates.spacing*(double)plates.spacing*384,total=0,value=0;
        for(Scored scored:candidates) {
            double t=(scored.score()-nearest)/band;if(t>=1)break;double w=(1-t)*(1-t)*(1+2*t);Site s=scored.site();
            double plane=s.datumQ()*settings.plateRelief/1024.0
                +((x-s.x())/(double)plates.spacing*s.tiltXQ()+(z-s.z())/(double)plates.spacing*s.tiltZQ())*settings.plateTilt/1024.0;
            total+=w;value+=plane*w;
        }
        return value/total;
    }

    private CrustState continental(Site hypothetical,List<Scored> candidates,long x,long z,boolean protectGroupBoundary) {
        Group group=groups.group(hypothetical.i(),hypothetical.j());
        double width=plates.spacing*settings.coastBlendPermille/1000.0;
        double radius=plates.spacing*.66*StrictMath.sqrt(coverage/53.0),shape=Double.NEGATIVE_INFINITY;
        for(var cell:group.members())shape=Math.max(shape,lobe(plates.site(cell.i(),cell.j()),group.id(),x,z,radius,width));
        for(int a=0;a<group.members().size();a++)for(int b=a+1;b<group.members().size();b++) {
            var ca=group.members().get(a);var cb=group.members().get(b);
            if(Math.abs(ca.i()-cb.i())+Math.abs(ca.j()-cb.j())!=1)continue;
            shape=Math.max(shape,bridge(plates.site(ca.i(),ca.j()),plates.site(cb.i(),cb.j()),x,z,width));
        }
        if(coverage==0)shape=-1;
        double raw=shape,envelope=1;
        if(protectGroupBoundary) {
            long inside=Long.MAX_VALUE,outside=Long.MAX_VALUE;
            for(Scored scored:candidates) {
                Group other=groups.group(scored.site().i(),scored.site().j());
                if(same(group,other))inside=Math.min(inside,scored.score());else outside=Math.min(outside,scored.score());
            }
            if(inside!=Long.MAX_VALUE&&outside!=Long.MAX_VALUE) {
                // Family minima are invariant when ownership changes inside one family.
                // A zero-score-gap band has fixed deep bed, even at maximum relief and
                // minimum sea level. Every land path stays in a family of at most four plates.
                double gap=(outside-inside)/(1024.0*plates.spacing*plates.spacing);
                double t=Math.max(0,Math.min(1,(gap-.035)/.25));
                envelope=t*t*(3-2*t);
            }
        }
        double t=Math.max(0,Math.min(1,.5+.5*shape)),fraction=t*t*(3-2*t);
        return new CrustState(fraction,raw,envelope,group);
    }
    private double lobe(Site site,long groupId,long x,long z,double radius,double width) {
        long h=Hash64.stream(Hash64.stream(seed,groupId),site.id());double orientation=unit(Hash64.mix(h))*StrictMath.PI*2;
        double cos=StrictMath.cos(orientation),sin=StrictMath.sin(orientation),dx=x-site.x(),dz=z-site.z();
        double rx=dx*cos+dz*sin,rz=-dx*sin+dz*cos;
        double aspect=.72+1.28*unit(Hash64.mix(h+1)),q=StrictMath.sqrt((rx/aspect)*(rx/aspect)+(rz*aspect)*(rz*aspect));
        double theta=StrictMath.atan2(rz*aspect,rx/aspect),size=.84+.32*unit(Hash64.mix(h+2));
        double edge=radius*size*(1+.15*StrictMath.sin(3*theta+phase(h,3))+.08*StrictMath.sin(5*theta+phase(h,4))+.04*StrictMath.sin(9*theta+phase(h,5)));
        return (edge-q)/width;
    }
    private double bridge(Site a,Site b,long x,long z,double width) {
        double dx=b.x()-a.x(),dz=b.z()-a.z(),length2=dx*dx+dz*dz;
        double t=Math.max(0,Math.min(1,((x-a.x())*dx+(z-a.z())*dz)/length2));
        double px=a.x()+t*dx,pz=a.z()+t*dz,distance=StrictMath.hypot(x-px,z-pz);
        return (plates.spacing*.12-distance)/width;
    }
    private static boolean same(Group a,Group b){return a.tileI()==b.tileI()&&a.tileJ()==b.tileJ()&&a.part()==b.part();}
    private static double unit(long h){return (h>>>11)*0x1.0p-53;}
    private static double phase(long h,int stream){return unit(Hash64.mix(h+stream*0x9e3779b97f4a7c15L))*StrictMath.PI*2;}

    /** Supplied-neighborhood v1 reference; complete support remains the caller's responsibility. */
    public static Crust crust(List<Plate> supplied,long x,long z,int spacing,int radiusPermille) {
        Lattice.check(x);Lattice.check(z);
        if(supplied==null||supplied.isEmpty()||supplied.size()>121||spacing<8192||spacing>262144||radiusPermille<1100||radiusPermille>2500)
            throw new IllegalArgumentException("Invalid crust support");
        var sites=new ArrayList<>(supplied);for(var site:sites)if(site==null)throw new IllegalArgumentException("Null crust site");
        sites.sort(Comparator.comparing(Plate::id,Long::compareUnsigned));
        double radius=spacing*(double)radiusPermille/1000,radiusSquared=radius*radius,total=0,continental=0;int supported=0;
        for(int k=0;k<sites.size();k++) {
            var site=sites.get(k);if(k>0&&sites.get(k-1).id()==site.id())throw new IllegalArgumentException("Duplicate crust identity");
            long dx=site.x()-x,dz=site.z()-z;if(Math.abs(dx)>4194304||Math.abs(dz)>4194304)throw new IllegalArgumentException("Crust fixture outside arithmetic support");
            long distance=dx*dx+dz*dz;if(distance>=radiusSquared)continue;double t=1-distance/radiusSquared,weight=t*t*t;
            total+=weight;continental+=weight*site.crust();supported++;
        }
        if(total==0)throw new IllegalArgumentException("Incomplete crust support");return new Crust(continental/total,supported);
    }
}
