package genesis.harness;

import genesis.oracle.*;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;

/** Independent edge ledgers and proportionality checks for the complete multiple-flow path. */
public final class MultipleFlowGates {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        // A summit with eight absorbing neighbors: shares must equal the eight height drops.
        int[] bed={9,8,7,6,10,5,4,3,2};boolean[] active=new boolean[9],term=new boolean[9];
        Arrays.fill(active,true);Arrays.fill(term,true);term[4]=false;long[] source=new long[9];source[4]=360;
        var flow=ActiveHydrology.solveD8Rivers(3,3,bed,active,term,source);
        check(flow.receiverCount(4)==8,"Summit did not split across all eight neighbors");
        for(int edge=0;edge<8;edge++)check(flow.edgeFlux(4,edge)==10*(10-bed[flow.receiver(4,edge)]),"Height-drop ratio differs");
        // At zero gradient every level outlet must receive a share, without reciprocal edges.
        Arrays.fill(bed,10);source[4]=800;flow=ActiveHydrology.solveD8Rivers(3,3,bed,active,term,source);
        check(flow.receiverCount(4)==8,"Flat summit did not split across every level outlet");
        for(int edge=0;edge<8;edge++)check(flow.edgeFlux(4,edge)==100,"Flat shares differ");
        // Integer overflow fallback in proportional apportionment.
        bed=new int[]{Integer.MIN_VALUE,-1,0,1,Integer.MAX_VALUE,2,3,4,5};source[4]=Long.MAX_VALUE;
        audit(3,3,bed,active,term,source);
        Random random=new Random(381231);
        for(int trial=0;trial<150;trial++) {
            int w=3+random.nextInt(8),h=3+random.nextInt(8),n=w*h;
            bed=new int[n];active=new boolean[n];term=new boolean[n];source=new long[n];
            for(int p=0;p<n;p++){active[p]=random.nextInt(7)!=0;term[p]=active[p]&&random.nextInt(9)==0;
                bed[p]=trial%3==0?7:random.nextInt(20);source[p]=active[p]?random.nextInt(10000):0;}
            audit(w,h,bed,active,term,source);
        }
        var world=new TectonicTerrain(42,TectonicTerrain.defaultPlateParams(),TectonicTerrain.Settings.defaults());
        var hydro=new ContinentalHydrology(world,new RainfallField.Uniform(1000));
        var root=hydro.root(world.continent(-1,-1));int branches=0,visible=0;
        for(int p=0;p<root.size();p++)for(int edge=0;edge<root.receiverCount(p);edge++) {
            var profile=root.fluvial().edgeProfile(p,edge);if(profile==null)continue;
            check(profile.flux()==root.edgeFlux(p,edge),"Branch profile invented discharge");
            int q=root.receiver(p,edge);var end=root.fluvial().edgeCenterline(p,edge,1);
            check(end.x()==root.x(q)&&end.z()==root.z(q),"Branch lost its receiver endpoint");
            if(q!=root.downstream(p)) {
                branches++;
                if(visible==0){var middle=root.fluvial().edgeCenterline(p,edge,.5);
                    var channel=root.channelAt(Math.round(middle.x()),Math.round(middle.z()));
                    if(channel!=null&&channel.sourceSegment()==p&&channel.downstream()==q)visible++;}
            }
        }
        check(branches>0&&visible>0,"Secondary flow never reaches the visible river geometry");
        System.out.println("PASS MULTIPLE FLOW: eight-way proportional splits, equal-height outlets, 150 random DAG ledgers, long-limit arithmetic, "+branches+" secondary channel profiles and visible branch query");
    }
    private static void audit(int w,int h,int[] bed,boolean[] active,boolean[] term,long[] source) {
        var result=ActiveHydrology.solveD8Rivers(w,h,bed,active,term,source);long[] incoming=source.clone();long out=0;
        for(int p=0;p<bed.length;p++)if(result.resolved(p)) {
            int count=result.receiverCount(p);long sum=0,totalDrop=0;
            for(int edge=0;edge<count;edge++)totalDrop+=(long)result.filled(p)-result.filled(result.receiver(p,edge));
            for(int edge=0;edge<count;edge++) {
                int q=result.receiver(p,edge);long amount=result.edgeFlux(p,edge),drop=(long)result.filled(p)-result.filled(q);
                check(active[q]&&result.order(q)<result.order(p)&&drop>=0,"Uphill, inactive or cyclic edge");
                long weight=totalDrop==0?1:drop,denominator=totalDrop==0?count:totalDrop;
                BigInteger error=BigInteger.valueOf(amount).multiply(BigInteger.valueOf(denominator))
                    .subtract(BigInteger.valueOf(result.flux(p)).multiply(BigInteger.valueOf(weight))).abs();
                check(error.compareTo(BigInteger.valueOf(denominator))<0,"Share differs by more than one integer unit");
                incoming[q]=Math.addExact(incoming[q],amount);sum=Math.addExact(sum,amount);
            }
            if(count==0){check(term[p],"Invented outlet");out=Math.addExact(out,result.flux(p));}
            else check(sum==result.flux(p),"Outgoing edges lose water");
        }
        for(int p=0;p<bed.length;p++)check(incoming[p]==result.flux(p),"Incoming edge ledger differs");
        check(out==result.discharged&&out+result.unresolved==result.supplied,"Global water ledger differs");
    }
}
