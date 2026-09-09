package genesis.oracle;

import genesis.core.Params;
import genesis.core.hash.Lattice;
import genesis.core.tectonics.PlateTopology;

/** Reference forcing from existing plate vectors. Not final elevation or a drainage boundary.
 * Integer decisions/profiles; closest-edge switching is not a continuous multi-edge composition.
 */
public final class BoundaryForcing {
    public static final String VERSION = "boundary-forcing-v1";
    public static final int Q = 1024;
    public enum Regime { QUIET, COLLISION, SUBDUCTION, CONTINENTAL_RIFT, OCEAN_SPREADING, RIFTED_MARGIN, TRANSFORM }
    public record Plate(long id,long x,long z,int vx,int vz,int crust,int age) {
        public Plate {
            // Internal plate sites can lie beyond the public query guard; never clip their geometry.
            long limit=Lattice.MAX_COORDINATE+4194304L;
            if(x < -limit||x > limit||z < -limit||z > limit)throw new IllegalArgumentException("Unsupported internal plate coordinate");
            if(Math.abs((long)vx)>1024||Math.abs((long)vz)>1024||crust<0||crust>1||age<0||age>4500)
                throw new IllegalArgumentException("Unsupported plate attributes");
        }
        public static Plate from(PlateTopology.Site s){return new Plate(s.id,s.x,s.z,s.vx,s.vz,s.crust,s.age);}
    }
    /** Normal points from canonical first to second. Positive normalQ means convergence.
     * Signed shear uses the same canonical orientation; all values are model units, not cm/year.
     * descendingSide: -1 first, +1 second, 0 no subduction. Ocean/ocean age tie picks first.
     */
    public record Edge(Plate first,Plate second,long normalQ,long shearQ,Regime regime,int descendingSide) {
        public Edge {
            if(first==null||second==null||Long.compareUnsigned(first.id,second.id)>=0||regime==null
                ||normalQ < -4194304||normalQ > 4194304||shearQ < -4194304||shearQ > 4194304
                ||(regime==Regime.SUBDUCTION?Math.abs(descendingSide)!=1:descendingSide!=0))
                throw new IllegalArgumentException("Invalid bounded edge descriptor");
        }
    }
    public record Sample(Edge edge,int offsetPermille,int anomaly) {}
    private final PlateTopology topology;
    private final int transformRatio;
    public BoundaryForcing(long seed,Params params) {
        if(params==null)throw new IllegalArgumentException("Parameters required");
        topology=new PlateTopology(seed,params);transformRatio=params.integer("transformRatio");
    }
    public static Edge describe(Plate a,Plate b,int transformRatio) {
        if(a==null||b==null||a.id==b.id||transformRatio<0||transformRatio>100)throw new IllegalArgumentException("Distinct plates and valid threshold required");
        if(Long.compareUnsigned(a.id,b.id)>0){Plate swap=a;a=b;b=swap;}
        long dx=b.x-a.x,dz=b.z-a.z;
        if(Math.abs(dx)>4194304||Math.abs(dz)>4194304||(dx==0&&dz==0))throw new IllegalArgumentException("Unsupported boundary separation");
        long vx=(long)a.vx-b.vx,vz=(long)a.vz-b.vz;
        long normal=vx*dx+vz*dz,shear=vx*dz-vz*dx;
        long length=PlateTopology.sqrt(dx*dx+dz*dz);
        Regime regime;int descending=0;
        if(normal==0&&shear==0)regime=Regime.QUIET;
        else if(Math.abs(normal)*100<=Math.abs(shear)*transformRatio)regime=Regime.TRANSFORM;
        else if(normal>0) {
            if(a.crust==1&&b.crust==1)regime=Regime.COLLISION;
            else {
                regime=Regime.SUBDUCTION;
                descending=a.crust!=b.crust?(a.crust==0?-1:1):(a.age>=b.age?-1:1);
            }
        } else regime=a.crust!=b.crust?Regime.RIFTED_MARGIN:a.crust==1?Regime.CONTINENTAL_RIFT:Regime.OCEAN_SPREADING;
        return new Edge(a,b,normal*Q/length,shear*Q/length,regime,descending);
    }
    /** Signed model-height anomaly over a fixed width, not absolute altitude or simulated uplift history.
     * offsetPermille -1000..1000 spans one profile half-width on either side of the shared bisector.
     */
    public static int anomaly(Edge e,int u) {
        if(e==null)throw new IllegalArgumentException("Edge required");
        if(u<=-1000||u>=1000)return 0;
        long gain=Math.abs(e.normalQ);
        return switch(e.regime) {
            case QUIET,TRANSFORM -> 0; // Shear is retained, not invented as vertical displacement.
            case COLLISION -> scale(gain,8,bump(u,0,1000));
            case SUBDUCTION -> scale(gain,-12,bump(u,e.descendingSide*250,200))
                +scale(gain,8,bump(u,-e.descendingSide*300,350));
            case CONTINENTAL_RIFT -> scale(gain,-6,bump(u,0,250))
                +scale(gain,2,bump(u,-450,350))+scale(gain,2,bump(u,450,350));
            case OCEAN_SPREADING -> scale(gain,5,bump(u,0,1000))-scale(gain,2,bump(u,0,150));
            case RIFTED_MARGIN -> scale(gain,-4,bump(u,0,450))
                +scale(gain,2,bump(u,e.first.crust==1?-450:450,350));
        };
    }
    private static int scale(long gain,int coefficient,int shape){return (int)(gain*coefficient*shape/(Q*(long)Q));}
    private static int bump(int u,int center,int width) {
        int distance=Math.abs(u-center);if(distance>=width)return 0;
        long t=distance*(long)Q/width,v=Q-t;
        return (int)(v*v*(Q+2*t)/(Q*(long)Q));
    }
    public Sample sample(long x,long z) {
        var s=topology.sample(x,z);var e=describe(Plate.from(s.owner),Plate.from(s.neighbor),transformRatio);
        long dx=e.second.x-e.first.x,dz=e.second.z-e.first.z;
        long length=PlateTopology.sqrt(dx*dx+dz*dz);
        // Query belongs near these bounded-support sites; use local differences, not world-coordinate products.
        long twiceProjection=(2*(x-e.first.x)-dx)*dx+(2*(z-e.first.z)-dz)*dz;
        long offset=twiceProjection*1000/(2*length*(topology.spacing/4L));
        int u=(int)Math.max(-1000,Math.min(1000,offset));
        return new Sample(e,u,anomaly(e,u));
    }
}
