package genesis.harness;

import genesis.oracle.ContinentalHydrology;
import genesis.oracle.ContinentalGroups;
import genesis.oracle.RainfallField;
import genesis.oracle.TectonicTerrain;
import genesis.oracle.ActiveHydrology;
import genesis.oracle.TerrainHardness;
import genesis.core.hash.Lattice;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.file.Files;
import java.nio.file.Path;

/** Complete continent solve checks; the finite solver has separate independent minimax gates. */
final class ContinentalHydrologyGates {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void rejects(Runnable r){try{r.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Invalid drainage input accepted");}
    public static void main(String[] args)throws Exception{run();}
    static void run()throws Exception {
        var world=new TectonicTerrain(42,TectonicTerrain.defaultPlateParams(),TectonicTerrain.Settings.defaults());
        var hydro=new ContinentalHydrology(world,new RainfallField.Uniform(1000));StringBuilder json=new StringBuilder("{\"version\":\"").append(ContinentalHydrology.VERSION).append("\",\"roots\":[");
        var digest=MessageDigest.getInstance("SHA-256");var bytes=ByteBuffer.allocate(48);int roots=0,lakes=0;
        for(int[] ij:new int[][]{{0,0},{-1,-1},{8,6}}) {
            var root=hydro.root(world.continent(ij[0],ij[1]));if(roots++>0)json.append(',');
            long total=0,out=0;int resolved=0;long[] walked=new long[root.size()];
            for(int p=0;p<root.size();p++)if(root.active(p)) {
                total=Math.addExact(total,root.source(p));if(root.status(p)==0)continue;resolved++;
                int q=root.downstream(p);if(q<0)out=Math.addExact(out,root.flux(p));else {
                    check(root.active(q)&&root.order(q)<root.order(p)&&root.filled(q)<=root.filled(p),"River does not terminate/downhill on filled surface");
                    check(Math.abs(root.x(p)-root.x(q))<=hydro.step&&Math.abs(root.z(p)-root.z(q))<=hydro.step,"River teleports");
                }
                check(root.flux(p)>=root.source(p),"Flux lost local rain");
                walked[p]=Math.addExact(walked[p],root.source(p));long outgoing=0;
                for(int edge=0;edge<root.receiverCount(p);edge++) {
                    int receiver=root.receiver(p,edge);long amount=root.edgeFlux(p,edge);
                    check(root.order(receiver)<root.order(p)&&root.filled(receiver)<=root.filled(p),"Cyclic or uphill branch");
                    walked[receiver]=Math.addExact(walked[receiver],amount);outgoing=Math.addExact(outgoing,amount);
                }
                if(root.receiverCount(p)>0)check(outgoing==root.flux(p),"Branch shares lose water");
                bytes.clear();bytes.putLong(root.x(p)).putLong(root.z(p)).putInt(root.bed(p)).putInt(root.filled(p)).putInt(root.downstream(p)).putLong(root.source(p)).putLong(root.flux(p));digest.update(bytes.array(),0,bytes.position());
            }
            for(int p=0;p<root.size();p++)if(root.active(p))check(walked[p]==root.flux(p),"Complete upstream source walks disagree with flux");
            check(total==root.supplied()&&out==root.discharged()&&total==out+root.unresolved(),"Continent water ledger mismatch");
            check(root.unresolved()==0,"Default continent contains unresolved rain");lakes+=root.lakeCells;
            System.out.println("Root "+root.group.id()+" ("+root.group.size()+" plates): "+root.bounds+", active="+root.activeCells+", lakes="+root.lakeCells+", supplied="+total+", unresolved="+root.unresolved()+", buildMs="+root.buildNanos/1e6);
            json.append("{\"id\":\"").append(root.group.id()).append("\",\"plates\":").append(root.group.size()).append(",\"active\":").append(root.activeCells).append(",\"lakes\":").append(root.lakeCells).append(",\"supplied\":\"").append(total).append("\",\"discharged\":\"").append(out).append("\",\"unresolved\":\"").append(root.unresolved()).append("\",\"buildMs\":").append(root.buildNanos/1e6).append('}');
        }
        check(lakes>0,"No continental depressions fill to spill");
        counterfactuals(world);spillFixtures();
        String fingerprint=HexFormat.of().formatHex(digest.digest());check(fingerprint.equals("6d1ad9209233fb3b541db0d2b6b86c69cee0cf372ba16389983f25aeb9b71bee"),"Versioned continental hydrology changed: "+fingerprint);
        Files.createDirectories(Path.of("build/gallery"));Files.writeString(Path.of("build/continental-hydrology.json"),json.append("],\"fingerprint\":\"").append(fingerprint).append("\"}\n").toString());
        System.out.println("PASS CONTINENT HYDRO: complete support vs wider reference, rainfall scaling/heterogeneity/dryness, downhill overflow and independent upstream walks, cache/concurrency, spill cascade and exact ledgers");
    }
    private static void compare(ContinentalHydrology.Root a,ContinentalHydrology.Root b,long rainScale) {
        check(a.activeCells==b.activeCells&&a.terminalCells==b.terminalCells,"Support changed active continent/maritime cells");
        for(int p=0;p<a.size();p++)if(a.active(p)) {
            int q=b.index(a.x(p),a.z(p));check(b.active(q)&&a.bed(p)==b.bed(q)&&a.filled(p)==b.filled(q),"Support/rain changed bed or spill");
            int ap=a.downstream(p),bp=b.downstream(q);check((ap<0&&bp<0)||(ap>=0&&bp>=0&&a.x(ap)==b.x(bp)&&a.z(ap)==b.z(bp)),"Drainage topology depends on box/rain/cache");
            long tolerance=rainScale<=1?0:8L*a.activeCells*rainScale;
            check(b.source(q)==a.source(p)*rainScale&&Math.abs(b.flux(q)-a.flux(p)*rainScale)<=tolerance,"Rainfall scaling exceeds accumulated integer apportionment error");
        }
    }
    private static void counterfactuals(TectonicTerrain world)throws Exception {
        var group=world.continent(-1,-1);var hydro=new ContinentalHydrology(world,new RainfallField.Uniform(1000),1);var base=hydro.root(group);
        compare(base,hydro.referenceRoot(group,3),1);
        compare(base,new ContinentalHydrology(world,new RainfallField.Uniform(2000)).root(group),2);
        compare(base,new ContinentalHydrology(world,new RainfallField.Uniform(0)).root(group),0);
        var d=world.settings;var upSettings=new TectonicTerrain.Settings(d.coastBlendPermille(),d.seaThreshold(),d.landHeight(),d.oceanDepth(),d.forcingPermille(),d.detailHeight(),d.seaLevel(),d.plateWarpPermille(),d.plateRoughnessPermille(),d.plateRelief(),d.plateTilt(),d.elevationOffset(),2);
        var upWorld=new TectonicTerrain(world.seed,world.basePlateParams,upSettings);var up=new ContinentalHydrology(upWorld,new RainfallField.Uniform(1000)).root(upWorld.continent(-1,-1));
        check(up.activeCells==base.activeCells&&up.terminalCells==base.terminalCells&&up.supplied()==base.supplied()*4&&up.discharged()==base.discharged()*4,"Native upscale changed drainage support or area budget");
        for(int p=0;p<base.size();p++)if(base.active(p)) {
            int q=up.index(base.x(p)*2,base.z(p)*2);check(up.active(q)&&up.bed(q)==base.bed(p)*2&&up.filled(q)==base.filled(p)*2&&up.source(q)==base.source(p)*4&&Math.abs(up.flux(q)-base.flux(p)*4)<=32L*base.activeCells,"Native upscale changed river geometry or exceeded integer rounding bound");
            int a=base.downstream(p),b=up.downstream(q);check((a<0&&b<0)||(a>=0&&b>=0&&up.x(b)==base.x(a)*2&&up.z(b)==base.z(a)*2),"Native upscale changed receiver topology");
        }
        long split=base.bounds.x()+base.bounds.width()/2L*base.step;
        var varying=new ContinentalHydrology(world,(x,z)->x<split?500:1500).root(group);long rain=0;boolean west=false,east=false;
        for(int p=0;p<varying.size();p++)if(varying.active(p)&&!varying.sea(p)) {
            long amount=varying.x(p)<split?500:1500;west|=amount==500;east|=amount==1500;
            long expected=amount*varying.step*varying.step;check(varying.source(p)==expected,"Spatial rainfall field ignored");rain+=expected;
        }
        check(west&&east&&rain==varying.supplied()&&rain==varying.discharged(),"Heterogeneous rain not conserved");
        hydro.root(world.continent(8,6));compare(base,hydro.root(group),1);hydro.clear();compare(base,hydro.root(group),1);
        try(var pool=Executors.newFixedThreadPool(3)) {
            List<Callable<ContinentalHydrology.Root>> jobs=List.of(()->hydro.root(group),()->hydro.root(world.continent(8,6)),()->hydro.root(group));
            var results=pool.invokeAll(jobs);compare(base,results.get(0).get(),1);compare(base,results.get(2).get(),1);
        }
        cacheConcurrencyFixtures(world);
        var forgedSource=world.continent(8,6);check(forgedSource.id()!=group.id(),"Forged-group fixture shares an id");
        var forged=new ContinentalGroups.Group(forgedSource.tileI(),forgedSource.tileJ(),forgedSource.part(),group.id(),forgedSource.members());
        rejects(()->hydro.root(forged));
        rejects(()->new RainfallField.Uniform(-1));rejects(()->new RainfallField.Uniform(10001));rejects(()->new ContinentalHydrology(world,(x,z)->-1).root(group));
        rejects(()->hydro.root(hydro.group(world.sample(Lattice.MAX_COORDINATE,Lattice.MAX_COORDINATE))));
        // Entire support, not the read crop, determines the result for distant seeds/continents.
        for(long seed:new long[]{-1,137}) {
            var other=new TectonicTerrain(seed,TectonicTerrain.defaultPlateParams(),TectonicTerrain.Settings.defaults());
            var root=new ContinentalHydrology(other,new RainfallField.Uniform(1000)).root(other.continent(0,0));
            check(root.supplied()==root.discharged()+root.unresolved()&&root.unresolved()==0,"Additional seed drainage unresolved");
        }
        for(int spacing:new int[]{8193,1048576}) {
            var other=new TectonicTerrain(42,new genesis.core.Params(java.util.Map.of("plateSpacing",(double)spacing,"continentalPercent",53.0)),TectonicTerrain.Settings.defaults());
            var root=new ContinentalHydrology(other,new RainfallField.Uniform(10000)).root(other.continent(-1,-1));
            check(root.size()<=514*514&&root.supplied()==root.discharged()&&root.unresolved()==0,"Off-step/extreme spacing or maximum rainfall failed");
        }
    }
    private static void cacheConcurrencyFixtures(TectonicTerrain world)throws Exception {
        var group=world.continent(-1,-1);var baseHardness=TerrainHardness.seeded(world);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var first=new AtomicBoolean(true);
        TerrainHardness delayed=(x,z)->{if(first.compareAndSet(true,false)){entered.countDown();await(release);}return baseHardness.resistance(x,z);};
        var activeClear=new ContinentalHydrology(world,new RainfallField.Uniform(1000),2,0,delayed);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var old=pool.submit(()->activeClear.root(group));
            try {
                check(entered.await(10,TimeUnit.SECONDS),"Active-clear build did not reach its provider");
                activeClear.clear();var waiterThread=new AtomicReference<Thread>();var waiterStarted=new CountDownLatch(1);
                var fresh=pool.submit(()->{waiterThread.set(Thread.currentThread());waiterStarted.countDown();return activeClear.root(group);});
                check(waiterStarted.await(10,TimeUnit.SECONDS),"Post-clear request did not start");
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean waiting=false;
                while(System.nanoTime()<deadline&&!fresh.isDone()) {
                    var state=waiterThread.get().getState();if(state==Thread.State.WAITING||state==Thread.State.TIMED_WAITING){waiting=true;break;}
                    Thread.sleep(1);
                }
                check(waiting,"Post-clear request did not wait for the stale build");release.countDown();
                var oldRoot=old.get(20,TimeUnit.SECONDS);var freshRoot=fresh.get(20,TimeUnit.SECONDS);
                check(oldRoot!=freshRoot,"Post-clear request reused a pre-clear root");compare(oldRoot,freshRoot,1);
            } finally {release.countDown();}
        }

        var fourEntered=new CountDownLatch(4);var releaseFour=new CountDownLatch(1);
        var builderThreads=ConcurrentHashMap.<Thread>newKeySet();
        TerrainHardness fourAtOnce=(x,z)->{if(builderThreads.add(Thread.currentThread())){fourEntered.countDown();await(releaseFour);}return baseHardness.resistance(x,z);};
        var bounded=new ContinentalHydrology(world,new RainfallField.Uniform(1000),5,0,fourAtOnce);
        var groups=List.of(world.continent(-1,-1),world.continent(8,6),world.continent(16,12),world.continent(24,18),world.continent(32,24));
        check(groups.stream().map(ContinentalGroups.Group::id).distinct().count()==5,"Build-bound fixtures are not distinct");
        try(var pool=Executors.newFixedThreadPool(5)) {
            var jobs=groups.stream().<Callable<ContinentalHydrology.Root>>map(g->()->bounded.root(g)).toList();var futures=new java.util.ArrayList<java.util.concurrent.Future<ContinentalHydrology.Root>>();
            for(var job:jobs)futures.add(pool.submit(job));
            try {
                check(fourEntered.await(20,TimeUnit.SECONDS),"Four build slots did not start");Thread.sleep(100);
                check(builderThreads.size()==4,"More than four distinct roots built concurrently");
            } finally {releaseFour.countDown();}
            for(var future:futures)check(future.get(30,TimeUnit.SECONDS).unresolved()==0,"Bounded concurrent root failed");
            check(builderThreads.size()==5,"Queued fifth root never acquired a released slot");
        }

        var failOnce=new AtomicBoolean(true);
        var recovering=new ContinentalHydrology(world,new RainfallField.Uniform(1000),2,0,(x,z)->{if(failOnce.compareAndSet(true,false))throw new IllegalArgumentException("failure fixture");return baseHardness.resistance(x,z);});
        rejects(()->recovering.root(group));check(recovering.root(group).unresolved()==0,"Failed build left its cache slot wedged");
    }
    private static void await(CountDownLatch latch) {
        try{latch.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
    }
    private static void spillFixtures() {
        // Two depressions in series, separated by a saddle at 30, then sea at 0.
        int[] bed={80,10,30,5,20,0};boolean[] active=new boolean[6];Arrays.fill(active,true);boolean[] terminals={false,false,false,false,false,true};long[] rain={0,7,0,11,0,0};
        var a=ActiveHydrology.solveD8Rivers(6,1,bed,active,terminals,rain);
        check(a.filled(1)==30&&a.filled(3)==20&&a.flux(3)==18&&a.discharged==18,"Two-basin fill/spill cascade failed");
        // The next-best exit changes when the separating pass is lowered.
        int[] twoExits={0,40,10,60,0};boolean[] mask={true,true,true,true,true},ends={true,false,false,false,true};long[] source={0,0,9,0,0};
        var left=ActiveHydrology.solveD8Rivers(5,1,twoExits,mask,ends,source);check(left.filled(2)==40&&left.downstream(2)==1,"Basin did not choose its lower spill");
        twoExits[3]=20;var right=ActiveHydrology.solveD8Rivers(5,1,twoExits,mask,ends,source);check(right.filled(2)==20&&right.downstream(2)==3&&right.flux(4)==9,"Lower alternate pass did not redirect overflow");
        Arrays.fill(ends,false);var closed=ActiveHydrology.solveD8Rivers(5,1,twoExits,mask,ends,source);check(closed.unresolved==9&&closed.discharged==0,"Closed basin fabricated an outlet");
    }
}
