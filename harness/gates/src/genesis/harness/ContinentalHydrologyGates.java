package genesis.harness;

import genesis.oracle.ContinentalHydrology;
import genesis.oracle.RainfallField;
import genesis.oracle.TectonicTerrain;
import genesis.oracle.ActiveHydrology;
import genesis.core.hash.Lattice;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
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
                int visit=p,guard=0;while(visit>=0){walked[visit]=Math.addExact(walked[visit],root.source(p));visit=root.downstream(visit);check(++guard<=root.activeCells,"Drainage cycle");}
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
        String fingerprint=HexFormat.of().formatHex(digest.digest());check(fingerprint.equals("9d986dab09b1e116a9d6d011e19276d172f1fb1050fd07f66860b3c9718ef5d7"),"Versioned continental hydrology changed: "+fingerprint);
        Files.createDirectories(Path.of("build/gallery"));Files.writeString(Path.of("build/continental-hydrology.json"),json.append("],\"fingerprint\":\"").append(fingerprint).append("\"}\n").toString());
        System.out.println("PASS CONTINENT HYDRO: complete support vs wider reference, rainfall scaling/heterogeneity/dryness, downhill overflow and independent upstream walks, cache/concurrency, spill cascade and exact ledgers");
    }
    private static void compare(ContinentalHydrology.Root a,ContinentalHydrology.Root b,long rainScale) {
        check(a.activeCells==b.activeCells&&a.terminalCells==b.terminalCells,"Support changed active continent/maritime cells");
        for(int p=0;p<a.size();p++)if(a.active(p)) {
            int q=b.index(a.x(p),a.z(p));check(b.active(q)&&a.bed(p)==b.bed(q)&&a.filled(p)==b.filled(q),"Support/rain changed bed or spill");
            int ap=a.downstream(p),bp=b.downstream(q);check((ap<0&&bp<0)||(ap>=0&&bp>=0&&a.x(ap)==b.x(bp)&&a.z(ap)==b.z(bp)),"Drainage topology depends on box/rain/cache");
            check(b.source(q)==a.source(p)*rainScale&&b.flux(q)==a.flux(p)*rainScale,"Rainfall mass does not scale exactly");
        }
    }
    private static void counterfactuals(TectonicTerrain world)throws Exception {
        var group=world.continent(-1,-1);var hydro=new ContinentalHydrology(world,new RainfallField.Uniform(1000),1);var base=hydro.root(group);
        compare(base,hydro.referenceRoot(group,3),1);
        compare(base,new ContinentalHydrology(world,new RainfallField.Uniform(2000)).root(group),2);
        compare(base,new ContinentalHydrology(world,new RainfallField.Uniform(0)).root(group),0);
        var varying=new ContinentalHydrology(world,(x,z)->x< -524288?500:1500).root(group);long rain=0;boolean west=false,east=false;
        for(int p=0;p<varying.size();p++)if(varying.active(p)&&!varying.sea(p)) {
            long amount=varying.x(p)< -524288?500:1500;west|=amount==500;east|=amount==1500;
            long expected=amount*varying.step*varying.step;check(varying.source(p)==expected,"Spatial rainfall field ignored");rain+=expected;
        }
        check(west&&east&&rain==varying.supplied()&&rain==varying.discharged(),"Heterogeneous rain not conserved");
        hydro.root(world.continent(8,6));compare(base,hydro.root(group),1);hydro.clear();compare(base,hydro.root(group),1);
        try(var pool=Executors.newFixedThreadPool(3)) {
            List<Callable<ContinentalHydrology.Root>> jobs=List.of(()->hydro.root(group),()->hydro.root(world.continent(8,6)),()->hydro.root(group));
            var results=pool.invokeAll(jobs);compare(base,results.get(0).get(),1);compare(base,results.get(2).get(),1);
        }
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
