package genesis.oracle;

import genesis.core.Params;
import genesis.core.fields.Noise;
import genesis.core.hash.Hash64;
import java.util.Map;

/**
 * Seeded near-surface lithology and soil hydrology shared by erosion and the Minecraft adapter.
 * Values are deliberately broad material classes, not a claim of reconstructed geological history.
 */
public final class TerrainSubstrate implements TerrainHardness {
    public static final String VERSION="terrain-substrate-v1";
    private static final long DOMAIN=0x5355425354524154L;

    public enum Rock {
        BASALT(.84,.10,.28),GRANITE(.90,.16,.24),SHALE(.30,.07,.86),LIMESTONE(.50,.72,.66),SANDSTONE(.66,.58,.48);
        private final double resistance,permeability,weatherability;
        Rock(double resistance,double permeability,double weatherability){this.resistance=resistance;this.permeability=permeability;this.weatherability=weatherability;}
        public double resistance(){return resistance;}
        public double permeability(){return permeability;}
        public double weatherability(){return weatherability;}
    }
    public enum Drainage {WELL_DRAINED,MODERATE,POOR}
    public record Base(Rock rock,double resistance,double permeability,double weatherability) {}
    public record Ground(Base base,double soilDepth,double bedrockDepth,int runoffPermille,int infiltrationPermille,
                         int evapotranspirationPermille,Drainage drainage) {
        public Ground {
            if(base==null||drainage==null||!Double.isFinite(soilDepth)||soilDepth<0||soilDepth>12
                ||bedrockDepth!=soilDepth||runoffPermille<0||runoffPermille>1000||infiltrationPermille<0||infiltrationPermille>1000
                ||evapotranspirationPermille<0||evapotranspirationPermille>1000
                ||runoffPermille+infiltrationPermille+evapotranspirationPermille!=1000)
                throw new IllegalArgumentException("Invalid ground sample");
        }
    }

    private final TectonicTerrain world;
    private final Noise folds,texture;

    private TerrainSubstrate(TectonicTerrain world) {
        this.world=world;int spacing=world.basePlateParams.integer("plateSpacing");long seed=Hash64.stream(world.seed,DOMAIN);
        folds=new Noise(Hash64.stream(seed,1),new Params(Map.of("wavelength",(double)Math.max(16,spacing/2),"octaves",2.0)));
        texture=new Noise(Hash64.stream(seed,2),new Params(Map.of("wavelength",(double)Math.max(16,spacing/8),"octaves",2.0)));
    }
    public static TerrainSubstrate seeded(TectonicTerrain world) {
        if(world==null)throw new IllegalArgumentException("Terrain required");return new TerrainSubstrate(world);
    }

    public Base base(long x,long z,TectonicTerrain.Sample terrain) {
        if(terrain==null)throw new IllegalArgumentException("Terrain sample required");int scale=world.settings.size();
        long cx=Math.floorDiv(x,scale),cz=Math.floorDiv(z,scale);double folded=folds.sample(cx,cz),grain=texture.sample(cx,cz);
        Rock rock;
        if(terrain.owner().crust()==0||terrain.recentFracture()&&grain<-.25)rock=Rock.BASALT;
        else {
            // Folded, repeated contacts expose different units as relief cuts through them.
            int band=Math.floorMod((int)StrictMath.floor((terrain.elevation()/scale+folded*22+grain*5)/14),4);
            rock=switch(band){case 0->Rock.GRANITE;case 1->Rock.SHALE;case 2->Rock.LIMESTONE;default->Rock.SANDSTONE;};
        }
        double adjustment=.07*grain;double resistance=clamp(rock.resistance+adjustment,.05,.97);
        double permeability=clamp(rock.permeability-.04*grain,.02,.90);
        double weatherability=clamp(rock.weatherability+.05*grain,.08,.95);
        return new Base(rock,resistance,permeability,weatherability);
    }

    /** Soil storage and saturation turn material permeability into a bounded annual runoff fraction. */
    public Ground ground(Base base,double slope,double humidity) {
        if(base==null||!Double.isFinite(slope)||slope<0||!Double.isFinite(humidity)||humidity<0||humidity>1)
            throw new IllegalArgumentException("Invalid ground inputs");
        double stable=Math.exp(-Math.min(8,slope*18));
        double soil=clamp(.20+6.4*base.weatherability*humidity*(.28+.72*stable),.15,7.5);
        double capacity=clamp(base.permeability*(.45+.075*soil)*(1-.52*humidity),.02,.88);
        int evapotranspirationPermille=(int)Math.round((.16+.20*(1-humidity))*1000);
        int infiltrationPermille=Math.min(1000-evapotranspirationPermille,(int)Math.round(capacity*1000));
        int slopeTransfer=Math.min(infiltrationPermille,(int)Math.round(180*Math.min(1,slope/.12)));
        infiltrationPermille-=slopeTransfer;
        int runoffPermille=1000-evapotranspirationPermille-infiltrationPermille;
        Drainage drainage=infiltrationPermille>=500?Drainage.WELL_DRAINED:infiltrationPermille>=220?Drainage.MODERATE:Drainage.POOR;
        return new Ground(base,soil,soil,runoffPermille,infiltrationPermille,evapotranspirationPermille,drainage);
    }

    @Override public double resistance(long x,long z){return base(x,z,world.sample(x,z)).resistance;}
    private static double clamp(double value,double low,double high){return Math.max(low,Math.min(high,value));}
}
