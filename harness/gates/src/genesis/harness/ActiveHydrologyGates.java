package genesis.harness;

import genesis.oracle.ActiveHydrology;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.List;

/** Independent Bellman relaxation and per-source walks; no reuse of heap decisions. */
final class ActiveHydrologyGates {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void rejects(Runnable action){try{action.run();}catch(IllegalArgumentException|ArithmeticException expected){return;}throw new AssertionError("Invalid active solve accepted");}
    static int[] emptyCrests(int n){int[] a=new int[n];Arrays.fill(a,Integer.MIN_VALUE);return a;}
    static void run()throws Exception {
        Random random=new Random(802399);
        int[] alternative=emptyCrests(4);alternative[0]=100;
        var relaxed=verify(2,2,new int[4],new boolean[]{true,true,true,true},new boolean[]{true,false,false,false},
            new long[]{0,1,1,1},alternative,emptyCrests(4));
        check(relaxed.filled(1)==0,"First discovered expensive saddle must be relaxed through the cheaper detour");
        for(int trial=0;trial<300;trial++) {
            int w=1+random.nextInt(8),h=1+random.nextInt(8),n=w*h;int[] bed=new int[n],east=emptyCrests(n),south=emptyCrests(n);
            boolean[] active=new boolean[n],term=new boolean[n];long[] source=new long[n];
            for(int p=0;p<n;p++){bed[p]=trial%7==0?3:random.nextInt(51)-25;active[p]=random.nextInt(5)!=0;
                term[p]=active[p]&&random.nextInt(10)==0;source[p]=active[p]?random.nextInt(11):0;
                if(random.nextBoolean())east[p]=random.nextInt(81)-20;if(random.nextBoolean())south[p]=random.nextInt(81)-20;}
            verify(w,h,bed,active,term,source,east,south);
        }
        // Exclusion is a wall, not just expensive terrain. Dry outer edges are not fallback outlets.
        var closed=ActiveHydrology.solve(3,1,new int[]{0,999,0},new boolean[]{true,false,true},new boolean[]{true,false,false},
            new long[]{2,0,7},emptyCrests(3),emptyCrests(3));
        check(closed.discharged==2&&closed.unresolved==7&&!closed.resolved(2),"Inactive barrier or unresolved mass failed");
        try{closed.filled(2);throw new AssertionError("Unresolved height presented as solved");}catch(IllegalStateException expected){}
        // D4 cannot cross diagonally through two excluded cells.
        var diagonal=ActiveHydrology.solve(2,2,new int[4],new boolean[]{true,false,false,true},new boolean[]{true,false,false,false},
            new long[]{0,0,0,5},emptyCrests(4),emptyCrests(4));check(diagonal.unresolved==5,"D4 corner leak");
        int[] extremes={Integer.MAX_VALUE,Integer.MIN_VALUE};
        var extreme=ActiveHydrology.solve(2,1,extremes,new boolean[]{true,true},new boolean[]{true,false},new long[]{0,Long.MAX_VALUE},emptyCrests(2),emptyCrests(2));
        check(extreme.fillDepthSum==4294967295L&&extreme.discharged==Long.MAX_VALUE,"Extreme fill/flux arithmetic");
        int[] mutableBed={0,8},mutableEast=emptyCrests(2);long[] mutableRain={0,11};
        boolean[] mutableActive={true,true},mutableTerm={true,false};
        var snapshot=ActiveHydrology.solve(2,1,mutableBed,mutableActive,mutableTerm,mutableRain,mutableEast,emptyCrests(2));
        mutableBed[1]=99;mutableRain[1]=0;mutableActive[1]=false;mutableTerm[0]=false;mutableEast[0]=999;
        check(snapshot.filled(1)==8&&snapshot.flux(0)==11,"Completed solve retains mutable caller storage");
        rejects(()->ActiveHydrology.solve(2,1,new int[2],new boolean[]{true,true},new boolean[]{true,false},new long[]{Long.MAX_VALUE,1},emptyCrests(2),emptyCrests(2)));
        rejects(()->ActiveHydrology.solve(1,1,new int[1],new boolean[]{false},new boolean[]{true},new long[1],emptyCrests(1),emptyCrests(1)));
        rejects(()->ActiveHydrology.solve(1,1,new int[1],new boolean[]{true},new boolean[1],new long[]{-1},emptyCrests(1),emptyCrests(1)));
        rejects(()->ActiveHydrology.solve(2,1,new int[1],new boolean[2],new boolean[2],new long[2],emptyCrests(2),emptyCrests(2)));
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Long> job=()->ActiveHydrology.solve(2,1,extremes,new boolean[]{true,true},new boolean[]{true,false},new long[]{0,37},emptyCrests(2),emptyCrests(2)).discharged;
            for(var result:pool.invokeAll(List.of(job,job)))check(result.get()==37,"Concurrent solve changed mass");
        }
        System.out.println("PASS ACTIVE HYDRO: 300 independent saddle-minimax/source-walk cases, excluded cells, D4 corners, exact heterogeneous mass, extremes/invalid/concurrent; finite overflow model");
    }
    static ActiveHydrology.Result verify(int w,int h,int[] bed,boolean[] active,boolean[] term,long[] source,int[] east,int[] south) {
        var actual=ActiveHydrology.solve(w,h,bed,active,term,source,east,south);int n=bed.length;
        long[] levels=new long[n];Arrays.fill(levels,Long.MAX_VALUE);for(int p=0;p<n;p++)if(term[p])levels[p]=bed[p];
        for(int iteration=0;iteration<n;iteration++){boolean change=false;
            for(int p=0;p<n;p++)if(active[p]&&!term[p])for(int q=0;q<n;q++) {
                if(!active[q]||levels[q]==Long.MAX_VALUE||Math.abs(p%w-q%w)+Math.abs(p/w-q/w)!=1)continue;
                int edge=p/w==q/w?east[Math.min(p,q)]:south[Math.min(p,q)];
                long candidate=Math.max(bed[p],Math.max(levels[q],edge));if(candidate<levels[p]){levels[p]=candidate;change=true;}
            }if(!change)break;
        }
        long[] expectedFlux=new long[n];long total=0,discharge=0,unresolved=0;
        for(int p=0;p<n;p++) {
            check(actual.resolved(p)==(levels[p]!=Long.MAX_VALUE),"Terminal reachability mismatch");
            if(actual.resolved(p))check(actual.filled(p)==levels[p],"Minimax spill level differs from independent relaxation");
            total+=source[p];int q=p,steps=0;
            while(true){expectedFlux[q]+=source[p];int next=actual.downstream(q);if(next<0)break;
                check(active[q]&&active[next]&&Math.abs(q%w-next%w)+Math.abs(q/w-next/w)==1,"Receiver crosses excluded/nonlocal edge");
                int saddle=q/w==next/w?east[Math.min(q,next)]:south[Math.min(q,next)];
                check(actual.order(next)<actual.order(q)&&actual.filled(next)<=actual.filled(q)&&saddle<=actual.filled(q),"Routing climbs its overflow level or cycles");
                q=next;check(++steps<=n,"Cycle in source walk");}
            if(actual.resolved(p)){check(term[q],"Resolved source ends at invented outlet");discharge+=source[p];}else unresolved+=source[p];
        }
        for(int p=0;p<n;p++)check(actual.flux(p)==expectedFlux[p],"Source-forward accumulation mismatch");
        check(actual.supplied==total&&actual.discharged==discharge&&actual.unresolved==unresolved,"Independent ledger mismatch");
        return actual;
    }
}
