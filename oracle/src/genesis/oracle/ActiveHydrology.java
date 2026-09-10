package genesis.oracle;

import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

/** Finite D4 minimax routing with excluded cells, explicit edge crests and supplied net runoff.
 * Edge crests are physical graph saddle constraints, NOT hidden owner-based flow prohibitions.
 * Depression filling models eventual overflow, not transient storage or strict channel-bed descent.
 */
public final class ActiveHydrology {
    public static final String VERSION = "active-hydrology-v1";
    private record Entry(int cell,int level) {}
    public static final class Result {
        private final int[] filled,downstream,order;
        private final long[] flux;
        public final long supplied,discharged,unresolved,fillDepthSum;
        private Result(int[] filled,int[] downstream,int[] order,long[] flux,long supplied,long discharged,long unresolved,long depth) {
            this.filled=filled;this.downstream=downstream;this.order=order;this.flux=flux;
            this.supplied=supplied;this.discharged=discharged;this.unresolved=unresolved;fillDepthSum=depth;
        }
        public int size(){return filled.length;}
        public boolean resolved(int p){return order[p]>=0;}
        public int filled(int p){if(!resolved(p))throw new IllegalStateException("No certified terminal path");return filled[p];}
        public int downstream(int p){return downstream[p];}
        public int order(int p){return order[p];}
        public long flux(int p){return flux[p];}
    }
    private ActiveHydrology() {}
    /** East/south arrays name undirected saddles; Integer.MIN_VALUE adds no extra crest.
     * Inactive cells have no edges and must carry neither sources nor terminal flags.
     * All terminals are absorbing at their supplied height. Query order never enters the solve.
     */
    public static Result solve(int width,int height,int[] terrain,boolean[] active,boolean[] terminals,long[] runoff,
                               int[] east,int[] south) {
        return solveInternal(width,height,terrain,active,terminals,runoff,east,south,false);
    }
    /** D8 vertex-surface audit, including corner diagonals. No hidden edge crests or owner barriers.
     * The caller must establish what these sampled connections mean on its actual surface.
     */
    public static Result solveD8Surface(int width,int height,int[] terrain,boolean[] active,boolean[] terminals,long[] runoff) {
        return solveInternal(width,height,terrain,active,terminals,runoff,null,null,true);
    }
    /** Same minimax spill levels; steepest filled-surface descent outside flats.
     * Flat/lake paths retain the terminating priority-flood tree. Integer D8 lengths
     * 1000/1414 avoid floating tie decisions. Existing reference methods are unchanged.
     */
    public static Result solveD8Rivers(int width,int height,int[] terrain,boolean[] active,boolean[] terminals,long[] runoff) {
        Result flood=solveD8Surface(width,height,terrain,active,terminals,runoff);int n=terrain.length;
        int[] down=flood.downstream.clone(),sequence=new int[n];Arrays.fill(sequence,-1);
        for(int p=0;p<n;p++)if(flood.resolved(p)) {
            sequence[flood.order[p]]=p;if(terminals[p])continue;
            int x=p%width,z=p/width,best=-1,length=1;long drop=0;
            for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++) {
                int nx=x+dx,nz=z+dz;if(dx==0&&dz==0||nx<0||nz<0||nx>=width||nz>=height)continue;int q=nz*width+nx;
                if(!active[q]||!flood.resolved(q))continue;long candidate=(long)flood.filled[p]-flood.filled[q];int distance=dx!=0&&dz!=0?1414:1000;
                if(candidate>0&&(best<0||candidate*length>drop*distance)){best=q;drop=candidate;length=distance;}
            }
            if(best>=0)down[p]=best;
        }
        long[] flux=runoff.clone();long discharge=0;
        for(int k=n-1;k>=0;k--){int p=sequence[k];if(p<0)continue;int q=down[p];
            if(q>=0){if(flood.order[q]>=flood.order[p])throw new AssertionError("Steepest route violated flood order");flux[q]=Math.addExact(flux[q],flux[p]);}
            else discharge=Math.addExact(discharge,flux[p]);
        }
        if(Math.addExact(discharge,flood.unresolved)!=flood.supplied)throw new AssertionError("River ledger mismatch");
        return new Result(flood.filled,down,flood.order,flux,flood.supplied,discharge,flood.unresolved,flood.fillDepthSum);
    }
    private static Result solveInternal(int width,int height,int[] terrain,boolean[] active,boolean[] terminals,long[] runoff,
                                        int[] east,int[] south,boolean diagonal) {
        long size=(long)width*height;
        if(width<1||height<1||size>1048576)throw new IllegalArgumentException("Finite grid must have 1..1048576 cells");
        int n=(int)size;
        if(terrain==null||active==null||terminals==null||runoff==null||(!diagonal&&(east==null||south==null))
            ||terrain.length!=n||active.length!=n||terminals.length!=n||runoff.length!=n||(!diagonal&&(east.length!=n||south.length!=n)))
            throw new IllegalArgumentException("Mismatched finite inputs");
        int[] filled=new int[n],down=new int[n],order=new int[n],sequence=new int[n];
        Arrays.fill(down,-1);Arrays.fill(order,-1);
        boolean[] reached=new boolean[n];long total=0;
        var queue=new PriorityQueue<Entry>(Comparator.comparingInt(Entry::level).thenComparingInt(Entry::cell));
        for(int p=0;p<n;p++) {
            if(runoff[p]<0||!active[p]&&(terminals[p]||runoff[p]!=0))throw new IllegalArgumentException("Invalid inactive/source/terminal contract");
            total=Math.addExact(total,runoff[p]);
            if(terminals[p]){reached[p]=true;filled[p]=terrain[p];queue.add(new Entry(p,filled[p]));}
        }
        int count=0;
        while(!queue.isEmpty()) {
            var entry=queue.remove();int p=entry.cell;
            if(order[p]>=0||filled[p]!=entry.level)continue;
            order[p]=count;sequence[count++]=p;
            int x=p%width,z=p/width;
            int[] neighbors={x>0?p-1:-1,x+1<width?p+1:-1,z>0?p-width:-1,z+1<height?p+width:-1,
                diagonal&&x>0&&z>0?p-width-1:-1,diagonal&&x+1<width&&z>0?p-width+1:-1,
                diagonal&&x>0&&z+1<height?p+width-1:-1,diagonal&&x+1<width&&z+1<height?p+width+1:-1};
            for(int q:neighbors) {
                if(q<0||!active[q]||terminals[q]||order[q]>=0)continue;
                int crest=diagonal?Integer.MIN_VALUE:p/width==q/width?east[Math.min(p,q)]:south[Math.min(p,q)];
                int candidate=Math.max(terrain[q],Math.max(filled[p],crest));
                // Unlike vertex-only priority flood, arbitrary edge saddles require relaxation.
                if(!reached[q]||candidate<filled[q]) {
                    reached[q]=true;filled[q]=candidate;down[q]=p;queue.add(new Entry(q,candidate));
                }
            }
        }
        long[] flux=runoff.clone();long discharge=0,unresolved=0,depth=0;
        for(int p=0;p<n;p++) {
            if(order[p]<0)unresolved=Math.addExact(unresolved,runoff[p]);
            else depth=Math.addExact(depth,(long)filled[p]-terrain[p]);
        }
        for(int k=count-1;k>=0;k--){int p=sequence[k];if(down[p]>=0)flux[down[p]]=Math.addExact(flux[down[p]],flux[p]);else discharge=Math.addExact(discharge,flux[p]);}
        if(Math.addExact(discharge,unresolved)!=total)throw new AssertionError("Water ledger mismatch");
        return new Result(filled,down,order,flux,total,discharge,unresolved,depth);
    }
}
