package genesis.harness;

import genesis.oracle.ClimateField;
import genesis.oracle.ContinentalHydrology;
import genesis.oracle.DendriticErosion;
import genesis.oracle.TectonicTerrain;
import genesis.oracle.TerrainSubstrate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/** Determinism, bounds and hydrologic-protection gates for the dendritic terrain fork. */
final class DendriticErosionGates {
    private DendriticErosionGates() {}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}

    static void run()throws Exception {
        var world=new TectonicTerrain(42,TectonicTerrain.defaultPlateParams(),TectonicTerrain.Settings.defaults());
        var climate=new ClimateField(world.seed,world.plateParams.integer("plateSpacing"),1000);
        var substrate=TerrainSubstrate.seeded(world);
        var hydrology=new ContinentalHydrology(world,climate,2,700,substrate);
        var root=hydrology.root(world.continent(-1,-1));
        var field=new DendriticErosion(world);
        var points=new ArrayList<Integer>();
        for(int p=0;p<root.size();p++)if(root.active(p))points.add(p);
        Collections.shuffle(points,new Random(91824));

        int changed=0,protectedCells=0,guided=0;double maximum=0,neighborJump=0;
        long jumpX=0,jumpZ=0;double jumpStrength=0,jumpNeighborStrength=0;
        int count=Math.min(1200,points.size());
        for(int k=0;k<count;k++) {
            int p=points.get(k);long x=root.x(p),z=root.z(p);
            var raw=world.sample(x,z);double base=root.elevation(x,z,raw.elevation(),world.seaLevel());
            var a=field.sample(root,x,z,base);var b=new DendriticErosion(world).sample(root,x,z,base);
            check(a.equals(b),"Cold dendritic sample changed");
            check(a.height()>world.seaLevel()||a.delta()==0,"Dendritic detail crossed the coast floor");
            check(Math.abs(a.delta())<=14*world.settings.size()+1e-9,"Dendritic relief escaped its cap");
            check(a.ridgeMap()>=-1-1e-9&&a.ridgeMap()<=1+1e-9,"Dendritic ridge signal escaped -1..1");
            double guideLength=StrictMath.hypot(a.guideX(),a.guideZ());
            check(guideLength<1e-12||Math.abs(guideLength-1)<1e-9,"Guide direction is not normalized");
            if(a.delta()!=0){changed++;maximum=Math.max(maximum,Math.abs(a.delta()));}
            if(a.strength()>.05) {
                long neighborX=x+1;var neighborRaw=world.sample(neighborX,z);
                double neighborBase=root.elevation(neighborX,z,neighborRaw.elevation(),world.seaLevel());
                var neighbor=field.sample(root,neighborX,z,neighborBase);
                if(neighbor.strength()>.05) {
                    double jump=Math.abs(a.delta()-neighbor.delta());
                    if(jump>neighborJump){neighborJump=jump;jumpX=x;jumpZ=z;jumpStrength=a.strength();jumpNeighborStrength=neighbor.strength();}
                }
            }
            if(guideLength>.9)guided++;
            if(root.sea(p)||root.filled(p)>root.bed(p)) {
                protectedCells++;check(a.delta()==0&&a.height()==base,"Sea/lake protection changed terrain");
            }
        }
        check(changed>20&&maximum>.05,"Dendritic field is visually inactive");
        check(neighborJump<2,"Dendritic relief has a one-block discontinuity: "+neighborJump+" at "+jumpX+","+jumpZ+" strengths "+jumpStrength+"/"+jumpNeighborStrength);
        check(guided>changed,"Drainage guides are missing across active relief");
        check(protectedCells>0,"Fixture did not exercise protected water cells");
        Files.writeString(Path.of("build/dendritic-erosion.json"),
            "{\"version\":\""+DendriticErosion.VERSION+"\",\"samples\":"+count+",\"changed\":"+changed
                +",\"guided\":"+guided+",\"protected\":"+protectedCells+",\"maximumDelta\":"+maximum+",\"maximumNeighborJump\":"+neighborJump+"}\n");
        System.out.println("PASS DENDRITIC EROSION: world-coordinate/cold identity, one-block continuity, normalized drainage guidance, coast/lake protection and bounded relief; changed="+changed+", maxDelta="+maximum+", neighborJump="+neighborJump);
    }
}
