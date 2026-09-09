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
        long size=(long)width*height;
        if(width<1||height<1||size>1048576)throw new IllegalArgumentException("Finite grid must have 1..1048576 cells");
        int n=(int)size;
        if(terrain==null||active==null||terminals==null||runoff==null||east==null||south==null
            ||terrain.length!=n||active.length!=n||terminals.length!=n||runoff.length!=n||east.length!=n||south.length!=n)
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
            int[] neighbors={x>0?p-1:-1,x+1<width?p+1:-1,z>0?p-width:-1,z+1<height?p+width:-1};
            for(int q:neighbors) {
                if(q<0||!active[q]||terminals[q]||order[q]>=0)continue;
                int crest=p/width==q/width?east[Math.min(p,q)]:south[Math.min(p,q)];
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
