package genesis.harness;

import genesis.core.hash.Hash64;
import genesis.oracle.TectonicTerrain;
import java.util.Arrays;

/** Offline preset screening only. Never called by the generator or a viewport. */
final class TectonicCalibration {
    public static void main(String[] args) {
        var d=TectonicTerrain.Settings.defaults();
        var unshifted=new TectonicTerrain.Settings(d.coastBlendPermille(),d.seaThreshold(),d.landHeight(),d.oceanDepth(),
            d.forcingPermille(),d.detailHeight(),d.seaLevel(),d.plateWarpPermille(),d.plateRoughnessPermille(),d.plateRelief(),d.plateTilt(),0);
        for(long seed:new long[]{42,-1,137,8675309,0,Long.MIN_VALUE}) {
            var world=new TectonicTerrain(seed,TectonicTerrain.defaultPlateParams(),unshifted);int spacing=world.plateParams.integer("plateSpacing");
            double[] heights=new double[16384];int n=0;
            for(int z=0;z<128;z++)for(int x=0;x<128;x++) {
                long ox=Math.floorMod(Hash64.hash(7781,0,x,z),spacing),oz=Math.floorMod(Hash64.hash(7781,1,x,z),spacing);
                heights[n++]=world.sample((x-64L)*spacing+ox,(z-64L)*spacing+oz).elevation();
            }
            Arrays.sort(heights);System.out.printf("seed %d: 70th percentile %.3f; preset land %.3f%%%n",seed,heights[(int)(n*.7)],100.0*Arrays.stream(heights).filter(h->h+d.elevationOffset()>0).count()/n);
        }
    }
}
