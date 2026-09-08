package genesis.harness;

import genesis.core.Params;
import genesis.core.hydro.BoundaryPorts;
import genesis.core.hydro.DrainageRefinement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;

final class RefinementGates {
    private RefinementGates() {}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void rejects(Runnable action){try{action.run();}catch(IllegalArgumentException|ArithmeticException expected){return;}throw new AssertionError("Invalid refinement accepted");}
    static void run() throws Exception {
        Random random=new Random(82551);
        for(int trial=0;trial<600;trial++) {
            long seed=trial%17==0?Long.MIN_VALUE:random.nextLong(),i=random.nextInt(15)-7,j=random.nextInt(15)-7;
            int level=1+random.nextInt(5),exitSide=1+random.nextInt(4);
            Params params=new Params(Map.of("coarseSpacing",(double)new int[]{1024,1025,1026,4096,4097,4098,16383,16384}[trial%8]));
            var ports=new BoundaryPorts(seed,params);var kernel=new DrainageRefinement(seed,params);
            var exit=ports.port(ports.face(level,i,j,exitSide));
            var entries=new ArrayList<DrainageRefinement.Inflow>();long rain=trial%13==0?0:random.nextInt(1000),total=rain;
            for(int side=1;side<=4;side++)if(side!=exitSide&&random.nextBoolean()) {
                long units=random.nextInt(2000);total+=units;entries.add(new DrainageRefinement.Inflow(ports.port(ports.face(level,i,j,side)),units));
            }
            int[] costs={random.nextInt(100),random.nextInt(100),random.nextInt(100),random.nextInt(100)};
            if(trial%7==0)Arrays.fill(costs,0);if(trial%11==0)Arrays.fill(costs,Integer.MAX_VALUE);
            var r=kernel.refine(level,i,j,exit,entries.toArray(DrainageRefinement.Inflow[]::new),rain,total,costs);
            audit(r,ports);
            for(int c=0;c<4;c++) {
                long best=bruteDistance(c,r.root,1<<c,0,costs);
                check(best==r.child(c).distance,"Exhaustive simple-path distance mismatch");
            }
            String expected=signature(r);
            Collections.reverse(entries);
            var repeat=new DrainageRefinement(seed,params).refine(level,i,j,exit,entries.toArray(DrainageRefinement.Inflow[]::new),rain,total,costs);
            check(expected.equals(signature(repeat)),"Input permutation/cold construction changes refinement");
            costs[0]=-1;
            check(r.child(0).resistance>=0,"Retained mutable resistance input");
            var copy=r.children();copy[0]=null;check(r.child(0)!=null,"Mutable result array exposed");
        }
        contracts(); adjacencyAndThreads(); inheritedSubdivision(); golden(false);
        RefinementDemo.writeDiagnostics();
        System.out.println("PASS REFINE: 600 exhaustive four-child path/budget cases, inherited crossing ownership, input permutation, adjacent parents, parallel queries, overflow/invalid contracts");
    }
    private static long bruteDistance(int p,int root,int seen,long cost,int[] resistance) {
        if(p==root)return cost;long best=Long.MAX_VALUE;
        for(int q=0;q<4;q++)if((p^q)==1||(p^q)==2)if((seen&(1<<q))==0)
            best=Math.min(best,bruteDistance(q,root,seen|(1<<q),cost+(long)resistance[p]+resistance[q]+1,resistance));
        return best;
    }
    private static void audit(DrainageRefinement.Result r,BoundaryPorts ports) {
        long rain=0,external=0;long[] recurrence=new long[4];int roots=0;
        long parentS=ports.spacing(r.level),px=r.i*parentS,pz=r.j*parentS;
        for(int c=0;c<4;c++) {
            var node=r.child(c);rain+=node.localRain;external+=node.externalInflow;
            check(node.localRain==r.localRain/4||node.localRain==r.localRain/4+1,"Uneven rain disaggregation");
            recurrence[c]+=node.localRain+node.externalInflow;
            int q=node.downstream;
            if(q<0){roots++;check(c==r.root&&node.exit==r.exit&&node.distance==0&&node.outflow==r.outflow,"Inherited outlet moved or budget changed");}
            else {
                check((q^c)==1||(q^c)==2,"Non-cardinal child edge");
                check(node.distance>r.child(q).distance,"Child cycle/nondecreasing potential");
                recurrence[q]+=node.outflow;
                int side=q==c+1?2:q==c-1?4:q==c+2?3:1,opposite=side<=2?side+2:side-2;
                var target=r.child(q);var face=ports.face(node.level,node.i,node.j,side);
                check(face.equals(ports.face(target.level,target.i,target.j,opposite))&&node.exit.owner.equals(face),"Internal crossing ownership mismatch");
                check(node.exit.x>px&&node.exit.x<px+parentS&&node.exit.z>pz&&node.exit.z<pz+parentS,"Internal transfer escapes parent");
            }
            int p=c,steps=0;while(r.child(p).downstream>=0){p=r.child(p).downstream;check(++steps<=3,"Cycle in refined parent");}
            check(p==r.root,"Child does not reach inherited exit");
        }
        check(roots==1&&rain==r.localRain&&external==r.externalInflow&&rain+external==r.outflow,"Parent rain/inflow counted twice");
        for(int c=0;c<4;c++)check(recurrence[c]==r.child(c).outflow,"Local child conservation");
        long[] injections=new long[4];
        for(var entry:r.entries()) {
            int owners=0;
            for(int c=0;c<4;c++) {
                var node=r.child(c);long s=ports.spacing(node.level),x=node.i*s,z=node.j*s;
                boolean boundary=entry.port.owner.axis==BoundaryPorts.VERTICAL?
                    (entry.port.x==x||entry.port.x==x+s)&&entry.port.z>z&&entry.port.z<z+s:
                    (entry.port.z==z||entry.port.z==z+s)&&entry.port.x>x&&entry.port.x<x+s;
                if(boundary){owners++;injections[c]+=entry.units;}
            }
            check(owners==1,"Inherited entry has zero/multiple child owners");
        }
        for(int c=0;c<4;c++)check(injections[c]==r.child(c).externalInflow,"Entry injected into wrong child");
    }
    private static void contracts() {
        var params=Params.defaults();var p=new BoundaryPorts(42,params);var k=new DrainageRefinement(42,params);
        var exit=p.port(p.face(1,0,0,2));var entry=new DrainageRefinement.Inflow(p.port(p.face(1,0,0,4)),3);
        var in=new DrainageRefinement.Inflow[]{entry};int[] zero=new int[4];
        rejects(()->k.refine(1,0,0,exit,in,5,5,zero));
        rejects(()->k.refine(1,0,0,exit,in,Long.MAX_VALUE,Long.MAX_VALUE,zero));
        rejects(()->k.refine(1,0,0,exit,new DrainageRefinement.Inflow[]{entry,entry},5,11,zero));
        rejects(()->k.refine(1,0,0,exit,new DrainageRefinement.Inflow[]{new DrainageRefinement.Inflow(exit,3)},5,8,zero));
        rejects(()->k.refine(1,1,0,exit,in,5,8,zero));
        rejects(()->k.refine(0,0,0,exit,in,5,8,zero));
        rejects(()->k.refine(1,0,0,exit,in,5,8,new int[]{0,-1,0,0}));
        rejects(()->new DrainageRefinement.Inflow(entry.port,-1));
        rejects(()->new DrainageRefinement(137,params).refine(1,0,0,exit,in,5,8,zero));
        var max=k.refine(1,0,0,exit,new DrainageRefinement.Inflow[0],Long.MAX_VALUE,Long.MAX_VALUE,zero);audit(max,p);
        var dry=k.refine(1,0,0,exit,new DrainageRefinement.Inflow[0],0,0,zero);audit(dry,p);
    }
    private static void inheritedSubdivision() {
        for(int base:new int[]{1025,1026,4096,4097,4098}) {
            var params=new Params(Map.of("coarseSpacing",(double)base));var p=new BoundaryPorts(42,params);var k=new DrainageRefinement(42,params);
            var exit=p.port(p.face(2,-1,-1,2));var entry=new DrainageRefinement.Inflow(p.port(p.face(2,-1,-1,4)),100);
            var parent=k.refine(2,-1,-1,exit,new DrainageRefinement.Inflow[]{entry},11,111,new int[]{0,3,7,5});
            for(int c=0;c<4;c++) {
                var node=parent.child(c);var incoming=new ArrayList<DrainageRefinement.Inflow>();
                for(var sibling:parent.children())if(sibling.downstream==c)incoming.add(new DrainageRefinement.Inflow(sibling.exit,sibling.outflow));
                if(node.externalInflow!=0)incoming.add(entry);
                var fine=k.refine(node.level,node.i,node.j,node.exit,incoming.toArray(DrainageRefinement.Inflow[]::new),node.localRain,node.outflow,new int[]{5,0,2,9});
                audit(fine,p);check(fine.exit==node.exit,"Second subdivision rehashed inherited exit");
                for(var old:incoming)check(Arrays.stream(fine.entries()).anyMatch(e->e.port==old.port),"Second subdivision moved inherited entry");
            }
        }
    }
    static void golden(boolean candidates) throws Exception {
        Properties baseline=new Properties();try(var input=Files.newInputStream(Path.of("harness/golden/refinement.properties"))){baseline.load(input);}
        check(DrainageRefinement.VERSION.equals(baseline.getProperty("version")),"Refinement baseline version mismatch");
        for(long seed:new long[]{0,1,-1,42,137,8675309,Long.MIN_VALUE,Long.MAX_VALUE}) {
            String json=RefinementDemo.json(RefinementDemo.create(Map.of("seed",Long.toString(seed))));
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
            if(candidates)System.out.println("refinement:seed."+seed+"="+hash);
            else check(hash.equals(baseline.getProperty("seed."+seed)),"Refinement exact contract baseline changed: "+seed);
        }
    }
    private static void adjacencyAndThreads() throws Exception {
        var p=new BoundaryPorts(42,Params.defaults());var k=new DrainageRefinement(42,Params.defaults());
        var shared=p.port(p.face(1,-1,-1,2));var far=p.port(p.face(1,0,-1,2));
        var a=k.refine(1,-1,-1,shared,new DrainageRefinement.Inflow[0],101,101,new int[]{0,7,11,5});
        var b=k.refine(1,0,-1,far,new DrainageRefinement.Inflow[]{new DrainageRefinement.Inflow(shared,a.outflow)},103,204,new int[]{7,0,13,3});
        audit(a,p);audit(b,p);
        check(a.exit==b.entries()[0].port&&b.outflow==204,"Adjacent parents move or duplicate transfer");
        String expected=signature(a)+signature(b);
        try(var pool=Executors.newFixedThreadPool(4)) {
            List<Callable<String>> jobs=new ArrayList<>();
            for(int q=0;q<64;q++)jobs.add(()->{
                var rb=k.refine(1,0,-1,far,new DrainageRefinement.Inflow[]{new DrainageRefinement.Inflow(shared,101)},103,204,new int[]{7,0,13,3});
                var ra=k.refine(1,-1,-1,shared,new DrainageRefinement.Inflow[0],101,101,new int[]{0,7,11,5});
                return signature(ra)+signature(rb);
            });
            for(var job:pool.invokeAll(jobs))check(expected.equals(job.get()),"Parent query order or concurrency dependency");
        }
    }
    private static String signature(DrainageRefinement.Result r) {
        StringBuilder out=new StringBuilder();
        for(var c:r.children())out.append(c.i).append(',').append(c.j).append(',').append(c.downstream).append(',').append(c.distance).append(',')
            .append(c.localRain).append(',').append(c.externalInflow).append(',').append(c.outflow).append(',').append(c.exit.x).append(',').append(c.exit.z).append(';');
        for(var in:r.entries())out.append(in.port.x).append(',').append(in.port.z).append(',').append(in.units).append(';');
        return out.toString();
    }
}
