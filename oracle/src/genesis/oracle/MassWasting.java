package genesis.oracle;

import java.util.Arrays;

/**
 * Bounded thermal erosion for gravity-driven rockfall and shallow landsliding.
 * Material moves only when a local slope exceeds the hardness-dependent stable
 * angle. Each Jacobi pass conserves integer height-volume exactly; maritime
 * cells are fixed and no request/chunk boundary participates in the result.
 */
public final class MassWasting {
    public static final String VERSION="mass-wasting-v1";
    public static final int PASSES=4;
    private static final int[][] EDGES={{1,0},{0,1},{1,1},{-1,1}};

    public record Result(long movedHeightMillimetres,int changedCells) {}

    private MassWasting() {}

    /** Soft regolith settles near 28 degrees; coherent hard rock can retain cliffs near 62 degrees. */
    public static double stableAngleDegrees(double hardness) {
        if(!Double.isFinite(hardness)||hardness<0||hardness>1)throw new IllegalArgumentException("Hardness outside 0..1");
        return 28+34*StrictMath.pow(hardness,.75);
    }

    /** Maximum stable vertical rise in the same units as horizontalDistance. */
    public static double stableRise(double hardness,double horizontalDistance) {
        if(!Double.isFinite(horizontalDistance)||horizontalDistance<=0)throw new IllegalArgumentException("Positive finite distance required");
        return StrictMath.tan(StrictMath.toRadians(stableAngleDegrees(hardness)))*horizontalDistance;
    }

    /**
     * Relaxes four undirected D8 edge families for a small fixed pass count.
     * Bed elevations are integer millimetres and step is horizontal blocks.
     * Fixed cells neither donate nor receive material. Donors cannot cross the
     * supplied floor, which preserves the continental maritime separation.
     */
    public static Result relax(int width,int height,int[] bed,boolean[] active,boolean[] fixed,double[] hardness,
                               int step,int floor) {
        return relax(width,height,bed,active,fixed,hardness,step,floor,HydrologyTuning.defaults());
    }

    public static Result relax(int width,int height,int[] bed,boolean[] active,boolean[] fixed,double[] hardness,
                               int step,int floor,HydrologyTuning tuning) {
        long size=(long)width*height;
        if(width<1||height<1||size>1048576||bed==null||active==null||fixed==null||hardness==null
            ||bed.length!=size||active.length!=size||fixed.length!=size||hardness.length!=size||step<1||tuning==null)
            throw new IllegalArgumentException("Invalid mass-wasting grid");
        for(int p=0;p<bed.length;p++)if(active[p]&&(!Double.isFinite(hardness[p])||hardness[p]<0||hardness[p]>1))
            throw new IllegalArgumentException("Hardness outside 0..1");

        long moved=0;boolean[] changed=new boolean[bed.length];long[] outgoing=new long[bed.length],delta=new long[bed.length];
        for(int pass=0;pass<tuning.massWastingPasses();pass++) {
            Arrays.fill(outgoing,0);Arrays.fill(delta,0);
            for(int[] edge:EDGES)for(int z=0;z<height;z++)for(int x=0;x<width;x++) {
                int nx=x+edge[0],nz=z+edge[1];if(nx<0||nx>=width||nz>=height)continue;
                int p=z*width+x,q=nz*width+nx,high=bed[p]>=bed[q]?p:q,low=high==p?q:p;
                long proposal=proposal(high,low,bed,active,fixed,hardness,step,p%width!=q%width&&p/width!=q/width,tuning);
                outgoing[high]=Math.addExact(outgoing[high],proposal);
            }
            boolean any=false;
            for(int[] edge:EDGES)for(int z=0;z<height;z++)for(int x=0;x<width;x++) {
                int nx=x+edge[0],nz=z+edge[1];if(nx<0||nx>=width||nz>=height)continue;
                int p=z*width+x,q=nz*width+nx,high=bed[p]>=bed[q]?p:q,low=high==p?q:p;
                long proposal=proposal(high,low,bed,active,fixed,hardness,step,p%width!=q%width&&p/width!=q/width,tuning);
                if(proposal==0)continue;
                long available=Math.max(0L,(long)bed[high]-floor);
                long transfer=outgoing[high]<=available?proposal:(long)StrictMath.floor(proposal*(available/(double)outgoing[high]));
                if(transfer<=0)continue;
                delta[high]=Math.subtractExact(delta[high],transfer);delta[low]=Math.addExact(delta[low],transfer);
                moved=Math.addExact(moved,transfer);changed[high]=changed[low]=true;any=true;
            }
            if(!any)break;
            long ledger=0;
            for(int p=0;p<bed.length;p++) {
                ledger=Math.addExact(ledger,delta[p]);
                bed[p]=Math.toIntExact(Math.addExact(bed[p],delta[p]));
            }
            if(ledger!=0)throw new AssertionError("Mass wasting created or destroyed material");
        }
        int count=0;for(boolean cell:changed)if(cell)count++;
        return new Result(moved,count);
    }

    private static long proposal(int high,int low,int[] bed,boolean[] active,boolean[] fixed,double[] hardness,
                                 int step,boolean diagonal,HydrologyTuning tuning) {
        if(high==low||!active[high]||!active[low]||fixed[high]||fixed[low]||bed[high]<=bed[low])return 0;
        double distance=step*(diagonal?StrictMath.sqrt(2):1);
        double angle=tuning.softStableAngle()+(tuning.hardStableAngle()-tuning.softStableAngle())*StrictMath.pow(hardness[high],.75);
        long stable=(long)StrictMath.ceil(StrictMath.tan(StrictMath.toRadians(angle))*distance*1000);
        long excess=(long)bed[high]-bed[low]-stable;
        return excess>1?excess/2:0;
    }
}
