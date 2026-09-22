package genesis.oracle;

import java.util.Arrays;

/** Detachment-limited n=1, m=1/2 implicit stream-power incision followed by
 * locally mass-conserving gravity relaxation. Incised bedrock leaves the model;
 * collapsed slope material is deposited downslope. Flooded beds are protected
 * from stream incision and routes are recomputed between fixed passes.
 */
public final class HydraulicErosion {
    public static final String VERSION="continental-erosion-v3";
    public static final int PASSES=6;
    private HydraulicErosion() {}

    static record Evolution(ActiveHydrology.Result flow,MassWasting.Result massWasting) {}

    /** Dimensionless artistic exposure; synthetic plate age is not elapsed landscape age. */
    public static double exposure(int age) {return .2+.8*Math.min(4500,Math.max(0,age))/4500.0;}

    /** Closed form implicit update. Inputs and output share any consistent height unit. */
    public static double incise(double bed,double receiver,double rainCells,double hardness,double exposure,double strength,double distance) {
        if(!Double.isFinite(bed)||!Double.isFinite(receiver)||!Double.isFinite(rainCells)||rainCells<0
            ||!Double.isFinite(hardness)||hardness<0||hardness>1||!Double.isFinite(exposure)||exposure<0
            ||!Double.isFinite(strength)||strength<0||!Double.isFinite(distance)||distance<=0)
            throw new IllegalArgumentException("Invalid erosion inputs");
        double a=strength*exposure*(1-.95*hardness)*StrictMath.sqrt(rainCells)/distance;
        return bed<=receiver?bed:receiver+(bed-receiver)/(1+a);
    }

    static Evolution evolve(int width,int height,int[] bed,boolean[] active,boolean[] sea,long[] source,
                            double[] hardness,double[] exposure,int step,int seaHeight,int strength) {
        return evolve(width,height,bed,active,sea,source,hardness,exposure,step,seaHeight,strength,HydrologyTuning.defaults());
    }

    static Evolution evolve(int width,int height,int[] bed,boolean[] active,boolean[] sea,long[] source,
                            double[] hardness,double[] exposure,int step,int seaHeight,int strength,HydrologyTuning tuning) {
        if(strength<0||strength>3000)throw new IllegalArgumentException("Erosion strength 0..3000");
        if(tuning==null)throw new IllegalArgumentException("Hydrology tuning required");
        var flow=route(width,height,bed,active,sea,source,seaHeight);
        if(strength==0)return new Evolution(flow,new MassWasting.Result(0,0));
        int[] sequence=new int[bed.length];
        int passes=tuning.erosionPasses();
        for(int pass=0;pass<passes;pass++) {
            int[] previous=bed.clone();
            Arrays.fill(sequence,-1);
            for(int p=0;p<bed.length;p++)if(flow.resolved(p))sequence[flow.order(p)]=p;
            // Downstream first: every implicit receiver already has its new elevation.
            for(int p:sequence)if(p>=0&&!sea[p]&&flow.filled(p)==bed[p]) {
                // Solve the n=1 implicit update with every outgoing edge's allocated flow.
                double coefficient=0,weightedReceiver=0;
                for(int edge=0;edge<flow.receiverCount(p);edge++) {
                    int q=flow.receiver(p,edge);long flux=flow.edgeFlux(p,edge);if(flux==0)continue;
                    double distance=p%width!=q%width&&p/width!=q/width?StrictMath.sqrt(2):1;
                    double receiver=sea[q]?seaHeight:flow.filled(q)>previous[q]?flow.filled(q):bed[q];
                    if(receiver>=bed[p])continue;
                    double a=strength/(1000.0*passes)*exposure[p]*(1-.95*hardness[p])
                        *StrictMath.sqrt(flux/(1000.0*step*step))/distance;
                    coefficient+=a;weightedReceiver+=a*receiver;
                }
                double result=(bed[p]+weightedReceiver)/(1+coefficient);
                bed[p]=Math.min(bed[p],Math.max(seaHeight+1,(int)StrictMath.ceil(result)));
            }
            // The next incision pass needs fresh routing; after the last pass the
            // gravity stage changes the bed again, so defer that final reroute.
            if(pass+1<passes)flow=route(width,height,bed,active,sea,source,seaHeight);
        }
        // Stream incision can oversteepen valley walls. A small, fixed, mass-conserving
        // relaxation leaves weak material at a lower stable angle than resistant rock.
        var wasting=tuning.massWasting()?MassWasting.relax(width,height,bed,active,sea,hardness,step,seaHeight+1,tuning):new MassWasting.Result(0,0);
        flow=route(width,height,bed,active,sea,source,seaHeight);
        return new Evolution(flow,wasting);
    }
    private static ActiveHydrology.Result route(int w,int h,int[] bed,boolean[] active,boolean[] sea,long[] source,int seaHeight) {
        int[] routing=bed.clone();for(int p=0;p<bed.length;p++)if(sea[p])routing[p]=seaHeight;
        return ActiveHydrology.solveD8Rivers(w,h,routing,active,sea,source);
    }
}
