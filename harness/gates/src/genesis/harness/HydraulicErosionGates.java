package genesis.harness;

import genesis.oracle.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/** Erosion counterfactuals and full-family random-access contracts. */
final class HydraulicErosionGates {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {run();}
    static void run()throws Exception {
        massWastingFixtures();
        // Independent one-edge backward-Euler solution: a=1, h=(100+20)/2=60.
        check(HydraulicErosion.incise(100,20,1,0,1,1,1)==60,"Implicit analytic fixture");
        check(HydraulicErosion.incise(100,20,0,0,1,1,1)==100,"Dry erosion");
        check(HydraulicErosion.incise(10,20,4,0,1,1,1)==10,"Uphill incision");
        double soft=HydraulicErosion.incise(100,20,1,0,1,1,1);
        check(HydraulicErosion.incise(100,20,1,1,1,1,1)>soft,"Hardness ignored");
        check(HydraulicErosion.incise(100,20,4,0,1,1,1)<soft,"Discharge ignored");
        check(HydraulicErosion.incise(100,20,1,0,HydraulicErosion.exposure(0),1,1)>
              HydraulicErosion.incise(100,20,1,0,HydraulicErosion.exposure(4500),1,1),"Age ignored");
        var world=new TectonicTerrain(42,TectonicTerrain.defaultPlateParams(),TectonicTerrain.Settings.defaults());
        var group=world.continent(-1,-1);var hardness=TerrainHardness.seeded(world);
        var hydro=new ContinentalHydrology(world,new RainfallField.Uniform(1000),1,700,hardness);
        var root=hydro.root(group);long removed=0;int changed=0,deposited=0;double lo=1,hi=0;
        for(int p=0;p<root.size();p++)if(root.active(p)) {
            if(root.sea(p))check(root.bed(p)==root.originalBed(p),"Maritime reserve changed");
            if(root.originalBed(p)>world.seaLevel()*1000)check(root.bed(p)>world.seaLevel()*1000,"Coast crossed sea datum");
            if(root.erosionDepth(p)>0)changed++;
            if(root.terrainChange(p)>0)deposited++;
            removed+=root.originalBed(p)-root.bed(p);lo=Math.min(lo,root.hardness(p));hi=Math.max(hi,root.hardness(p));
            int q=root.downstream(p);if(q>=0)check(root.order(q)<root.order(p)&&root.filled(q)<=root.filled(p),"Final drainage invalid");
            if(root.originalBed(p)>world.seaLevel()*1000)check(Math.abs(root.elevation(root.x(p),root.z(p),root.originalBed(p)/1000.0,world.seaLevel())-root.bed(p)/1000.0)<1e-9,"Fine sample disagrees at node");
        }
        check(changed>100&&removed>0&&hi-lo>.1,"Erosion/hardness inactive");
        check(root.exportedSediment.equals(java.math.BigInteger.valueOf(removed).multiply(java.math.BigInteger.valueOf((long)root.step*root.step))),"Bedrock volume ledger mismatch");
        check(root.redistributedSediment.signum()>=0,"Invalid gravity-transport ledger");
        check(root.supplied()==root.discharged()&&root.unresolved()==0,"Final runoff lost water");
        // Independent incoming-edge ledger on the FINAL apportioned drainage.
        long[] walks=new long[root.size()];
        for(int p=0;p<root.size();p++)walks[p]=root.source(p);
        for(int p=0;p<root.size();p++)for(int edge=0;edge<root.receiverCount(p);edge++) {
            int q=root.receiver(p,edge);check(root.order(q)<root.order(p),"Cycle");
            walks[q]=Math.addExact(walks[q],root.edgeFlux(p,edge));
        }
        for(int p=0;p<root.size();p++)if(root.active(p))check(walks[p]==root.flux(p),"Eroded source walks disagree");
        compare(root,hydro.referenceRoot(group,3));
        hydro.root(world.continent(8,6));compare(root,hydro.root(group));hydro.clear();
        try(var pool=Executors.newFixedThreadPool(3)) {
            List<Callable<ContinentalHydrology.Root>> jobs=List.of(()->hydro.root(group),()->hydro.root(world.continent(0,0)),()->hydro.root(group));
            var results=pool.invokeAll(jobs);compare(root,results.get(0).get());compare(root,results.get(2).get());
        }
        var dry=new ContinentalHydrology(world,new RainfallField.Uniform(0),1,700,hardness).root(group);
        check(dry.exportedSediment.signum()==0,"Dry gravity relaxation exported bedrock");
        var disabled=new ContinentalHydrology(world,new RainfallField.Uniform(1000),1,0,hardness).root(group);
        for(int p=0;p<disabled.size();p++)if(disabled.active(p))check(disabled.bed(p)==disabled.originalBed(p),"Disabled erosion changed terrain");
        var softRoot=new ContinentalHydrology(world,new RainfallField.Uniform(1000),1,700,(x,z)->0).root(group);
        var hardRoot=new ContinentalHydrology(world,new RainfallField.Uniform(1000),1,700,(x,z)->1).root(group);
        check(removed(softRoot)>removed(hardRoot),"Whole-family hardness counterfactual failed");
        var wet=new ContinentalHydrology(world,(x,z)->x<0?2000:1000,1,700,hardness).root(group);
        check(removed(wet)>removed(root),"Rainfall map did not affect erosion");
        for(long seed:new long[]{-1,Long.MIN_VALUE}) {
            var other=new TectonicTerrain(seed,new genesis.core.Params(java.util.Map.of("plateSpacing",8193.0,"continentalPercent",53.0)),TectonicTerrain.Settings.defaults());
            var extreme=new ContinentalHydrology(other,new RainfallField.Uniform(10000),1,3000,TerrainHardness.seeded(other));
            var full=extreme.root(other.continent(-1,-1));compare(full,extreme.referenceRoot(full.group,2));
            check(full.supplied()==full.discharged()&&full.unresolved()==0,"Extreme erosion water budget");
        }
        boolean rejected=false;try{new ContinentalHydrology(world,new RainfallField.Uniform(1),1,700,(x,z)->Double.NaN).root(group);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Invalid hardness accepted");
        Files.writeString(Path.of("build/hydraulic-erosion.json"),"{\"version\":\""+HydraulicErosion.VERSION+"\",\"changedCells\":"+changed+",\"depositedCells\":"+deposited+",\"removedHeightMmSum\":"+removed+",\"massWastingVersion\":\""+MassWasting.VERSION+"\",\"waterConserved\":true,\"widerSupportEqual\":true}\n");
        System.out.println("PASS EROSION: implicit fixture, mass-conserving hardness-sensitive slope failure, age/hardness/rainfall counterfactuals, complete support, final runoff, cache/concurrency; changed="+changed+", deposited="+deposited+", removedHeightMmSum="+removed);
    }
    private static void massWastingFixtures() {
        check(MassWasting.stableAngleDegrees(1)>MassWasting.stableAngleDegrees(0),"Hard rock did not retain a steeper angle");
        boolean[] active={true,true},fixed={false,false};
        int[] soft={100_000,0};double[] softRock={0,0};
        var moved=MassWasting.relax(2,1,soft,active,fixed,softRock,100,0);
        check(moved.movedHeightMillimetres()>0&&soft[0]+soft[1]==100_000,"Slope relaxation lost material");
        check(soft[0]-soft[1]<=Math.ceil(MassWasting.stableRise(0,100)*1000)+1,"Soft slope remains above its stable angle");
        int[] hard={100_000,0};double[] hardRock={1,1};
        check(MassWasting.relax(2,1,hard,active,fixed,hardRock,100,0).movedHeightMillimetres()==0&&hard[0]==100_000,"Hard slope collapsed like regolith");
        int[] repeat={100_000,0};MassWasting.relax(2,1,repeat,active,fixed,softRock,100,0);
        check(java.util.Arrays.equals(soft,repeat),"Mass wasting is order/state dependent");
    }
    private static long removed(ContinentalHydrology.Root r){long sum=0;for(int p=0;p<r.size();p++)if(r.active(p))sum+=r.originalBed(p)-r.bed(p);return sum;}
    private static void compare(ContinentalHydrology.Root a,ContinentalHydrology.Root b) {
        check(a.activeCells==b.activeCells,"Support changed");
        // Deliberately reverse query order; arrays are immutable externally.
        for(int p=a.size()-1;p>=0;p--)if(a.active(p)) {
            int q=b.index(a.x(p),a.z(p));check(b.active(q)&&a.bed(p)==b.bed(q)&&a.flux(p)==b.flux(q)&&a.filled(p)==b.filled(q)&&a.hardness(p)==b.hardness(q),"Erosion depends on support/cache/query order");
            int ap=a.downstream(p),bp=b.downstream(q);check(ap<0&&bp<0||ap>=0&&bp>=0&&a.x(ap)==b.x(bp)&&a.z(ap)==b.z(bp),"Eroded route changed");
        }
    }
}
