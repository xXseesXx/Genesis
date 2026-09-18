package genesis.harness;

import java.util.Map;

/** Exact pixel stitching including contour halos and nonzero sample phase. */
public final class TectonicTileGates {
    public static void main(String[] args) {
        for(String layer:new String[]{"erodedTerrain","riverMap","heightMap","windDirection","windSpeed","humidity","rainfall",
                                      "soilDepth","bedrockElevation","bedrockDepth","rockType","infiltration","runoffFraction","drainage"}) {
            long x=-3075,z=-3076,step=16;
            var full=TectonicView.render(TectonicView.request(Map.of("layer",layer,"x",""+x,"z",""+z,"step",""+step,"width","256","height","256"))).image();
            for(int tz=0;tz<2;tz++)for(int tx=0;tx<2;tx++) {
                var tile=TectonicView.render(TectonicView.request(Map.of("layer",layer,"x",""+(x+tx*128*step),"z",""+(z+tz*128*step),"step",""+step,"width","128","height","128"))).image();
                for(int py=0;py<128;py++)for(int px=0;px<128;px++)if(tile.getRGB(px,py)!=full.getRGB(tx*128+px,tz*128+py))throw new AssertionError("Tile seam: "+layer);
            }
        }
        System.out.println("PASS TECTONIC TILES: exact 2x2 stitching of terrain, rivers/lakes, continuous wind, climate, ground and contour halos at non-aligned negative origins");
    }
}
