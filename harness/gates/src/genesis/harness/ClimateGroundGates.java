package genesis.harness;

import genesis.core.Params;
import genesis.oracle.ClimateField;
import genesis.oracle.ContinentalHydrology;
import genesis.oracle.TectonicTerrain;
import genesis.oracle.TerrainSubstrate;
import genesis.oracle.WindField;
import java.util.Arrays;
import java.util.Map;

/** Contracts for source-free wind, bounded moisture transport and material-dependent runoff. */
final class ClimateGroundGates {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}

    static void run() {
        wind();controlledRain();ground();double climateRootMillis=integratedClimate();horizontalScale();
        System.out.println("PASS CLIMATE/GROUND: continuous nonzero divergence-free wind, bounded orographic humidity/rain, material runoff/soil, exact root partition, fourfold XZ shrink; climateRootMs="+climateRootMillis);
    }

    private static void wind() {
        var field=new WindField(42,2048);double minimum=Double.POSITIVE_INFINITY,maximumDivergence=0;
        for(int z=-19;z<=19;z++)for(int x=-19;x<=19;x++) {
            long wx=x*137L-17,wz=z*149L+31;var sample=field.sample(wx,wz);minimum=Math.min(minimum,sample.speed());
            var east=field.sample(wx+1,wz);var west=field.sample(wx-1,wz);var south=field.sample(wx,wz+1);var north=field.sample(wx,wz-1);
            double divergence=(east.x()-west.x()+south.z()-north.z())/2;
            maximumDivergence=Math.max(maximumDivergence,Math.abs(divergence));
            check(sample.equals(field.sample(wx,wz)),"Wind query is stateful");
            check(StrictMath.hypot(sample.unitX(),sample.unitZ())>1-1e-12,"Wind direction is not normalized");
        }
        check(minimum>=WindField.minimumSpeed()-1e-12,"Curl perturbation created a wind endpoint");
        check(maximumDivergence<1e-8,"Analytic streamfunction wind is not divergence free: "+maximumDivergence);
    }

    private static void controlledRain() {
        int width=96,height=96,n=width*height,step=32,seaHeight=63_000;int[] bed=new int[n];
        boolean[] active=new boolean[n],sea=new boolean[n];Arrays.fill(active,true);
        var climate=new ClimateField(137,2048,1000);var center=climate.wind(width/2L*step,height/2L*step);
        double cx=(width-1)/2.0,cz=(height-1)/2.0;
        for(int z=0;z<height;z++)for(int x=0;x<width;x++) {
            double along=(x-cx)*center.unitX()+(z-cz)*center.unitZ();
            double ridge=Math.max(0,1-Math.abs(along)/9.0);bed[z*width+x]=seaHeight+(int)Math.round(34_000*ridge);
        }
        var grid=climate.solve(width,height,0,0,step,bed,active,sea,seaHeight);
        double windward=0,lee=0;int windwardCells=0,leeCells=0;int min=10_000,max=0;
        for(int z=8;z<height-8;z++)for(int x=8;x<width-8;x++) {
            int p=z*width+x;double along=(x-cx)*center.unitX()+(z-cz)*center.unitZ();int rain=grid.rainfall(p);
            min=Math.min(min,rain);max=Math.max(max,rain);check(grid.humidity(p)>=0&&grid.humidity(p)<=1,"Humidity outside 0..1");
            if(along>=-8&&along<=-2){windward+=rain;windwardCells++;}
            if(along>=2&&along<=8){lee+=rain;leeCells++;}
        }
        check(max>min,"Orographic rainfall is uniform");
        check(windward/windwardCells>lee/leeCells,"Ridge lacks windward enhancement / lee rain shadow");
        var dry=new ClimateField(137,2048,0).solve(width,height,0,0,step,bed,active,sea,seaHeight);
        for(int p=0;p<n;p++)check(dry.rainfall(p)==0,"Zero climate scale produced rain");
    }

    private static void ground() {
        var world=new TectonicTerrain(42,TectonicTerrain.defaultPlateParams(),TectonicTerrain.Settings.defaults());
        var model=TerrainSubstrate.seeded(world);
        var shale=new TerrainSubstrate.Base(TerrainSubstrate.Rock.SHALE,.3,.07,.86);
        var limestone=new TerrainSubstrate.Base(TerrainSubstrate.Rock.LIMESTONE,.5,.72,.66);
        var tight=model.ground(shale,.02,.7);var open=model.ground(limestone,.02,.7);var steep=model.ground(shale,.4,.7);
        check(tight.runoffPermille()>open.runoffPermille(),"Impermeable shale does not yield more runoff than limestone");
        check(tight.infiltrationPermille()<open.infiltrationPermille(),"Material permeability does not affect infiltration");
        check(steep.soilDepth()<tight.soilDepth(),"Steep ground did not lose soil depth");
        check(tight.drainage()==TerrainSubstrate.Drainage.POOR&&open.drainage()!=TerrainSubstrate.Drainage.POOR,"Drainage classes ignore composition");
    }

    private static double integratedClimate() {
        var world=new TectonicTerrain(42,TectonicTerrain.defaultPlateParams(),TectonicTerrain.Settings.defaults());
        var climate=new ClimateField(world.seed,world.basePlateParams.integer("plateSpacing"),1000);var substrate=TerrainSubstrate.seeded(world);
        long start=System.nanoTime();var root=new ContinentalHydrology(world,climate,1,700,substrate).root(world.continent(-1,-1));
        int minRain=10_000,maxRain=0,rocks=0;boolean[] seen=new boolean[TerrainSubstrate.Rock.values().length];long supplied=0,cellArea=(long)root.step*root.step;
        for(int p=0;p<root.size();p++)if(root.active(p)&&!root.sea(p)) {
            int rain=root.rainfall(p),runoff=root.runoffPermille(p);minRain=Math.min(minRain,rain);maxRain=Math.max(maxRain,rain);
            check(root.humidity(p)>=0&&root.humidity(p)<=1,"Root humidity outside contract");
            check(root.soilDepth(p)>=0&&root.soilDepth(p)<=7.5,"Root soil depth outside contract");
            check(root.infiltrationPermille(p)+root.runoffPermille(p)+root.evapotranspirationPermille(p)==1000,"Ground water partition does not close");
            long effective=Math.floorDiv((long)rain*runoff+500,1000);check(root.source(p)==effective*cellArea,"Root runoff source ignores precipitation partition");
            supplied=Math.addExact(supplied,root.source(p));seen[root.rock(p).ordinal()]=true;
        }
        for(boolean value:seen)if(value)rocks++;
        check(minRain<maxRain,"Integrated climate rainfall is uniform");check(rocks>=3,"Integrated ground exposes too few rock classes");
        check(supplied==root.supplied()&&root.supplied()==root.discharged()+root.unresolved(),"Integrated runoff ledger does not close");
        check(root.wind(root.bounds.x(),root.bounds.z())!=null,"Integrated root lost wind field");
        double elapsedMillis=(System.nanoTime()-start)/1e6;
        check(elapsedMillis<3000,"Climate/ground root exceeded generous performance gate");
        return elapsedMillis;
    }

    private static void horizontalScale() {
        var small=new TectonicTerrain(42,TectonicTerrain.defaultPlateParams(),TectonicTerrain.Settings.defaults());
        var large=new TectonicTerrain(42,new Params(Map.of("plateSpacing",8192.0,"continentalPercent",53.0)),TectonicTerrain.Settings.defaults());
        check(small.basePlateParams.integer("plateSpacing")==2048,"Default XZ scale was not divided by four");
        var a=new ContinentalHydrology(small,new genesis.oracle.RainfallField.Uniform(1000),1).root(small.continent(-1,-1));
        var b=new ContinentalHydrology(large,new genesis.oracle.RainfallField.Uniform(1000),1).root(large.continent(-1,-1));
        check(a.bounds.width()==b.bounds.width()&&a.bounds.height()==b.bounds.height()&&a.step*4==b.step,
            "Fourfold shrink changed the canonical per-continent support dimensions");
        check((a.bounds.width()-1L)*a.step*4==(b.bounds.width()-1L)*b.step,
            "Fourfold shrink did not reduce physical continent support by four");
    }
}
