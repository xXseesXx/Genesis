package genesis.oracle;

import java.math.BigInteger;
import java.util.Arrays;

/** Finite D4 minimax routing with excluded cells, explicit edge crests and supplied net runoff.
 * Edge crests are physical graph saddle constraints, NOT hidden owner-based flow prohibitions.
 * Depression filling models eventual overflow, not transient storage or strict channel-bed descent.
 */
public final class ActiveHydrology {
    public static final String VERSION = "active-hydrology-v2";
    private static final int MAX_RECEIVERS=8;
    private static final int[] DX={-1,1,0,0,-1,1,-1,1};
    private static final int[] DZ={0,0,-1,1,-1,-1,1,1};
    /** Primitive heap key order is exactly signed level, then non-negative cell. */
    private static final class MinHeap {
        private long[] keys;
        private int size;
        MinHeap(int capacity){keys=new long[Math.max(1,capacity)];}
        boolean isEmpty(){return size==0;}
        void add(int cell,int level) {
            if(size==keys.length)keys=Arrays.copyOf(keys,Math.addExact(size,size<1024?size:Math.max(1024,size>>>1)));
            long key=((long)level<<32)|(cell&0xffffffffL);int child=size++;
            while(child>0){int parent=(child-1)>>>1;long prior=keys[parent];if(prior<=key)break;keys[child]=prior;child=parent;}
            keys[child]=key;
        }
        long remove() {
            long root=keys[0],key=keys[--size];
            if(size>0){int parent=0,half=size>>>1;
                while(parent<half){int child=(parent<<1)+1;long next=keys[child];
                    if(child+1<size&&keys[child+1]<next)next=keys[++child];
                    if(key<=next)break;keys[parent]=next;parent=child;
                }
                keys[parent]=key;
            }
            return root;
        }
    }
    public static final class Result {
        private final int[] filled,downstream,order;
        private final byte[] receiverCount;
        private final int[] receivers;
        private final long[] edgeFlux,flux;
        public final long supplied,discharged,unresolved,fillDepthSum;
        private Result(int[] filled,int[] downstream,int[] order,byte[] receiverCount,int[] receivers,long[] edgeFlux,
                       long[] flux,long supplied,long discharged,long unresolved,long depth) {
            this.filled=filled;this.downstream=downstream;this.order=order;this.flux=flux;
            this.receiverCount=receiverCount;this.receivers=receivers;this.edgeFlux=edgeFlux;
            this.supplied=supplied;this.discharged=discharged;this.unresolved=unresolved;fillDepthSum=depth;
        }
        public int size(){return filled.length;}
        public boolean resolved(int p){return order[p]>=0;}
        public int filled(int p){if(!resolved(p))throw new IllegalStateException("No certified terminal path");return filled[p];}
        public int downstream(int p){return downstream[p];}
        public int receiverCount(int p){return receiverCount==null?(downstream[p]>=0?1:0):Byte.toUnsignedInt(receiverCount[p]);}
        public int receiver(int p,int edge){checkEdge(p,edge);return receivers==null?downstream[p]:receivers[p*MAX_RECEIVERS+edge];}
        public long edgeFlux(int p,int edge){checkEdge(p,edge);return edgeFlux==null?flux[p]:edgeFlux[p*MAX_RECEIVERS+edge];}
        /** Flux committed to the deterministic primary receiver; every branch retains its share. */
        public long primaryFlux(int p) {
            int q=downstream[p];if(q<0)return 0;
            int count=receiverCount(p);for(int edge=0;edge<count;edge++)if(receiver(p,edge)==q)return edgeFlux(p,edge);
            throw new IllegalStateException("Primary receiver missing from runoff graph");
        }
        public int order(int p){return order[p];}
        public long flux(int p){return flux[p];}
        private void checkEdge(int p,int edge) {
            if(p<0||p>=filled.length||edge<0||edge>=receiverCount(p))throw new IndexOutOfBoundsException("Invalid receiver edge");
        }
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
    /** Same minimax spill levels; split accumulated flow in proportion to positive height
     * drops. Where every drop is zero, split equally across all earlier flood-order neighbors.
     * Flood order supplies the outlet-directed potential on flats, preventing cycles.
     */
    public static Result solveD8Rivers(int width,int height,int[] terrain,boolean[] active,boolean[] terminals,long[] runoff) {
        Result flood=solveD8Surface(width,height,terrain,active,terminals,runoff);int n=terrain.length;
        int[] down=flood.downstream.clone(),sequence=new int[n],receivers=new int[Math.multiplyExact(n,MAX_RECEIVERS)];
        long[] weights=new long[receivers.length],edgeFlux=new long[receivers.length];byte[] receiverCount=new byte[n];
        Arrays.fill(receivers,-1);Arrays.fill(sequence,-1);
        // Absorbing outlets have no outgoing edges and can always precede nonterminals.
        // This includes every adjacent level outlet regardless of its cell-index tie break.
        for(int p=0;p<n;p++)if(flood.resolved(p))sequence[flood.order[p]]=p;
        int rank=0;for(int p:sequence)if(p>=0&&terminals[p])flood.order[p]=rank++;
        for(int p:sequence)if(p>=0&&!terminals[p])flood.order[p]=rank++;
        Arrays.fill(sequence,-1);
        for(int p=0;p<n;p++)if(flood.resolved(p)) {
            sequence[flood.order[p]]=p;if(terminals[p])continue;
            int x=p%width,z=p/width,best=-1,bestLength=1;long bestDrop=0;
            for(int d=0;d<MAX_RECEIVERS;d++) {
                int nx=x+DX[d],nz=z+DZ[d];if(nx<0||nz<0||nx>=width||nz>=height)continue;int q=nz*width+nx;
                if(!active[q]||!flood.resolved(q)||flood.order[q]>=flood.order[p])continue;
                long drop=(long)flood.filled[p]-flood.filled[q];if(drop<=0)continue;
                addReceiver(p,q,drop,receiverCount,receivers,weights);
                int distance=d>=4?1414:1000;
                if(best<0||drop*bestLength>bestDrop*distance){best=q;bestDrop=drop;bestLength=distance;}
            }
            if(best>=0)down[p]=best;
            else {
                // A zero height drop has zero proportional weight alongside positive drops.
                // On a flat water surface, all outlet-directed level neighbors share equally.
                for(int d=0;d<MAX_RECEIVERS;d++) {
                    int nx=x+DX[d],nz=z+DZ[d];if(nx<0||nz<0||nx>=width||nz>=height)continue;int q=nz*width+nx;
                    if(active[q]&&flood.resolved(q)&&flood.order[q]<flood.order[p]&&flood.filled[q]==flood.filled[p])
                        addReceiver(p,q,1,receiverCount,receivers,weights);
                }
            }
        }
        long[] flux=runoff.clone(),remainders=new long[MAX_RECEIVERS];long discharge=0;
        for(int k=n-1;k>=0;k--){int p=sequence[k];if(p<0)continue;int count=Byte.toUnsignedInt(receiverCount[p]);
            if(count==0){discharge=Math.addExact(discharge,flux[p]);continue;}
            apportion(p,flux[p],count,weights,edgeFlux,remainders);
            for(int edge=0;edge<count;edge++) {
                int slot=p*MAX_RECEIVERS+edge,q=receivers[slot];
                if(flood.order[q]>=flood.order[p])throw new AssertionError("Multiple-flow route violated flood order");
                flux[q]=Math.addExact(flux[q],edgeFlux[slot]);
            }
        }
        if(Math.addExact(discharge,flood.unresolved)!=flood.supplied)throw new AssertionError("River ledger mismatch");
        return new Result(flood.filled,down,flood.order,receiverCount,receivers,edgeFlux,flux,
            flood.supplied,discharge,flood.unresolved,flood.fillDepthSum);
    }

    private static void addReceiver(int p,int q,long weight,byte[] counts,int[] receivers,long[] weights) {
        int edge=Byte.toUnsignedInt(counts[p]);if(edge>=MAX_RECEIVERS||weight<=0)throw new AssertionError("Invalid D8 receiver");
        int slot=p*MAX_RECEIVERS+edge;receivers[slot]=q;weights[slot]=weight;counts[p]=(byte)(edge+1);
    }

    /** Hamilton apportionment keeps every integer runoff unit while matching height-drop shares. */
    private static void apportion(int p,long available,int count,long[] weights,long[] result,long[] remainders) {
        int offset=p*MAX_RECEIVERS;long denominator=0,assigned=0;
        for(int edge=0;edge<count;edge++)denominator=Math.addExact(denominator,weights[offset+edge]);
        long quotient=available/denominator,remainder=available%denominator;
        for(int edge=0;edge<count;edge++) {
            long weight=weights[offset+edge],extra,fraction;
            if(remainder==0||weight==0){extra=0;fraction=0;}
            else if(remainder<=Long.MAX_VALUE/weight) {
                long product=remainder*weight;extra=product/denominator;fraction=product%denominator;
            } else {
                BigInteger[] qr=BigInteger.valueOf(remainder).multiply(BigInteger.valueOf(weight))
                    .divideAndRemainder(BigInteger.valueOf(denominator));
                extra=qr[0].longValueExact();fraction=qr[1].longValueExact();
            }
            long share=Math.addExact(Math.multiplyExact(quotient,weight),extra);
            result[offset+edge]=share;assigned=Math.addExact(assigned,share);remainders[edge]=fraction;
        }
        long remaining=available-assigned;
        for(long unit=0;unit<remaining;unit++) {
            int best=-1;for(int edge=0;edge<count;edge++)if(remainders[edge]>=0&&(best<0||remainders[edge]>remainders[best]))best=edge;
            result[offset+best]++;remainders[best]=-1;
        }
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
        var queue=new MinHeap(Math.min(n,256));
        for(int p=0;p<n;p++) {
            if(runoff[p]<0||!active[p]&&(terminals[p]||runoff[p]!=0))throw new IllegalArgumentException("Invalid inactive/source/terminal contract");
            total=Math.addExact(total,runoff[p]);
            if(terminals[p]){reached[p]=true;filled[p]=terrain[p];queue.add(p,filled[p]);}
        }
        int count=0;
        while(!queue.isEmpty()) {
            long entry=queue.remove();int p=(int)entry,level=(int)(entry>>32);
            if(order[p]>=0||filled[p]!=level)continue;
            order[p]=count;sequence[count++]=p;
            int x=p%width,z=p/width;
            int directions=diagonal?8:4;
            for(int d=0;d<directions;d++) {
                int nx=x+DX[d],nz=z+DZ[d];if(nx<0||nz<0||nx>=width||nz>=height)continue;int q=nz*width+nx;
                if(!active[q]||terminals[q]||order[q]>=0)continue;
                int crest=diagonal?Integer.MIN_VALUE:p/width==q/width?east[Math.min(p,q)]:south[Math.min(p,q)];
                int candidate=Math.max(terrain[q],Math.max(filled[p],crest));
                // Unlike vertex-only priority flood, arbitrary edge saddles require relaxation.
                if(!reached[q]||candidate<filled[q]) {
                    reached[q]=true;filled[q]=candidate;down[q]=p;queue.add(q,candidate);
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
        // Single-receiver D4 and flood results use a compact implicit edge view. The D8
        // river solve materializes its bounded eight-edge arrays only for the final graph.
        return new Result(filled,down,order,null,null,null,flux,total,discharge,unresolved,depth);
    }
}
