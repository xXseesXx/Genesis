package genesis.oracle;

import genesis.core.Params;
import genesis.core.hash.Lattice;
import genesis.core.tectonics.PlateTopology;
import genesis.oracle.BoundaryForcing.Edge;
import genesis.oracle.BoundaryForcing.Plate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Bounded soft multi-plate forcing experiment, not terrain or certified Voronoi-face topology.
 * Continuous real-valued recipe with canonical floating-point reduction order. See research notes.
 */
public final class JunctionForcing {
    public static final String VERSION = "junction-forcing-v1";
    private static final Comparator<Plate> ORDER = (a,b)->Long.compareUnsigned(a.id(),b.id());
    public record Sample(double anomaly,double positive,double negative,int supportedPlates,int activePairs) {}
    private final PlateTopology topology;
    private final int transformRatio;

    public JunctionForcing(long seed,Params params) {
        if(params==null)throw new IllegalArgumentException("Parameters required");
        topology=new PlateTopology(seed,params);transformRatio=params.integer("transformRatio");
    }
    public Sample sample(long x,long z) {
        Lattice.check(x);Lattice.check(z);
        long i=Lattice.cell(x,topology.spacing),j=Lattice.cell(z,topology.spacing);
        Plate[] sites=new Plate[25];int p=0;
        for(int dz=-2;dz<=2;dz++)for(int dx=-2;dx<=2;dx++)sites[p++]=Plate.from(topology.site(i+dx,j+dz));
        return compose(Arrays.asList(sites),x,z,topology.spacing,transformRatio);
    }
    /** Finite fixture entry point. A world caller must supply the complete contributing neighborhood.
     * Inputs are copied/sorted; plate IDs and attributes, not caller order, control all pair reductions.
     */
    public static Sample compose(List<Plate> supplied,long x,long z,int spacing,int transformRatio) {
        Lattice.check(x);Lattice.check(z);
        if(supplied==null||supplied.isEmpty()||supplied.size()>121||spacing<8192||spacing>262144
            ||transformRatio<0||transformRatio>100)throw new IllegalArgumentException("Invalid composition inputs");
        Plate[] sites=supplied.toArray(Plate[]::new);
        for(Plate site:sites)if(site==null)throw new IllegalArgumentException("Null plate");
        Arrays.sort(sites,ORDER);
        long[] squared=new long[sites.length];long nearest=Long.MAX_VALUE;
        for(int k=0;k<sites.length;k++) {
            if(k>0&&sites[k].id()==sites[k-1].id())throw new IllegalArgumentException("Duplicate plate identity");
            long dx=sites[k].x()-x,dz=sites[k].z()-z;
            if(Math.abs(dx)>4194304||Math.abs(dz)>4194304)throw new IllegalArgumentException("Fixture exceeds local arithmetic support");
            squared[k]=dx*dx+dz*dz;nearest=Math.min(nearest,squared[k]);
        }
        // squared-distance excess, not viewport-relative normalization or a new plate partition.
        double band=spacing*(double)spacing/2;
        double[] weight=new double[sites.length];int supported=0,pairs=0;
        for(int k=0;k<sites.length;k++) {
            double t=(squared[k]-nearest)/band;
            if(t<1){weight[k]=(1-t)*(1-t)*(1+2*t);supported++;}
        }
        double positive=0,negative=0,total=0;
        for(int a=0;a<sites.length;a++)if(weight[a]>0)for(int b=a+1;b<sites.length;b++)if(weight[b]>0) {
            Edge edge=BoundaryForcing.describe(sites[a],sites[b],transformRatio);
            double w=weight[a]*weight[b];total+=w;pairs++;
            long dx=edge.second().x()-edge.first().x(),dz=edge.second().z()-edge.first().z();
            double length=StrictMath.sqrt(dx*dx+dz*dz);
            long projection=(2*(x-edge.first().x())-dx)*dx+(2*(z-edge.first().z())-dz)*dz;
            double offset=projection/(2*length*(spacing/4.0));
            double height=profile(edge,offset)*w;
            if(height>0)positive+=height;else negative+=height;
        }
        // A convex blend at crowded junctions; fades to zero rather than dividing by a vanishing pair weight.
        double denominator=Math.max(1,total);
        positive/=denominator;negative/=denominator;
        return new Sample(positive+negative,positive,negative,supported,pairs);
    }
    /** Unquantized counterpart of the v1 illustrative profile shapes; u is signed distance / half-width.
     * Integer plate classification/projections remain BoundaryForcing's versioned model decisions.
     */
    public static double profile(Edge edge,double u) {
        if(edge==null||!Double.isFinite(u))throw new IllegalArgumentException("Finite profile coordinate required");
        if(u<=-1||u>=1)return 0;
        double gain=Math.abs(edge.normalQ())/(double)BoundaryForcing.Q;
        return gain*switch(edge.regime()) {
            case QUIET,TRANSFORM -> 0;
            case COLLISION -> 8*bump(u,0,1);
            case SUBDUCTION -> -12*bump(u,edge.descendingSide()*.25,.2)+8*bump(u,-edge.descendingSide()*.3,.35);
            case CONTINENTAL_RIFT -> -6*bump(u,0,.25)+2*bump(u,-.45,.35)+2*bump(u,.45,.35);
            case OCEAN_SPREADING -> 5*bump(u,0,1)-2*bump(u,0,.15);
            case RIFTED_MARGIN -> -4*bump(u,0,.45)+2*bump(u,edge.first().crust()==1?-.45:.45,.35);
        };
    }
    private static double bump(double u,double center,double width) {
        double t=Math.abs(u-center)/width;
        return t>=1?0:(1-t)*(1-t)*(1+2*t);
    }
}
