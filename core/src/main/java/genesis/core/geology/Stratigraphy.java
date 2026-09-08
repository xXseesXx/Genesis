package genesis.core.geology;

import genesis.core.Params;
import genesis.core.fields.FieldRegistry;
import genesis.core.fields.Fields;
import genesis.core.fields.RockColumn;
import genesis.core.fields.RockColumn.Rock;
import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;

/** M5a material volume: ordered strata translated by continuous, seeded broad folds.
 * It samples the current surface; it does not carve it or feed back into drainage.
 */
public strictfp final class Stratigraphy {
    public static final String VERSION="stratigraphy-v1";
    private static final long DOMAIN=0x535452415441L;
    private static final long Q=4096;
    private final long seed;
    private final Params params;
    private final FieldRegistry inputs;
    public Stratigraphy(long seed,Params params,FieldRegistry inputs) {
        this.seed=Hash64.stream(seed,DOMAIN);this.params=params;this.inputs=inputs;
    }
    // Fixed-point smoothstep has zero derivative at lattice joins; integer rounding is <= one unit per stage.
    private static long smooth(long offset,int scale) {
        long u=offset*Q/scale;return u*u*(3*Q-2*u)/(Q*Q);
    }
    private long anchor(long i,long j) { return Math.floorMod(Hash64.hash(seed,0,i,j),2*Q+1)-Q; }
    public int displacement(long x,long z) {
        Lattice.check(x);Lattice.check(z);int amplitude=params.integer("geologyFoldAmplitude");if(amplitude==0)return 0;
        int scale=params.integer("geologyFoldScale");long i=Math.floorDiv(x,scale),j=Math.floorDiv(z,scale);
        long u=smooth(Math.floorMod(x,scale),scale),v=smooth(Math.floorMod(z,scale),scale);
        long a=anchor(i,j)*(Q-u)+anchor(i+1,j)*u,b=anchor(i,j+1)*(Q-u)+anchor(i+1,j+1)*u;
        return (int)((a*(Q-v)+b*v)*amplitude/(Q*Q*Q));
    }
    public long contact(long x,long z,int index) {
        if(index<0||index>2)throw new IllegalArgumentException("Contact index must be 0..2");
        long top=params.integer("geologyDatum")+displacement(x,z),thickness=params.integer("geologyLayerThickness");
        return index==0?top-3*thickness:index==1?top-thickness:top;
    }
    private long surface(long x,long z) { return (long)Math.floor(inputs.get(Fields.BASE_ELEVATION,x,z)); }
    private int index(long x,long z,long y) {
        long top=contact(x,z,2),t=params.integer("geologyLayerThickness");
        return y<top-3*t?0:y<top-t?1:y<top?2:3;
    }
    private Rock material(long x,long z,int index) {
        return index==0?(inputs.get(Fields.CRUST_TYPE,x,z)==0?Rock.BASALT:Rock.GRANITE):index==1?Rock.SHALE:index==2?Rock.LIMESTONE:Rock.SANDSTONE;
    }
    public int rock(long x,long z) { return material(x,z,index(x,z,surface(x,z))).id; }
    public int hardness(long x,long z) { return params.integer(material(x,z,index(x,z,surface(x,z))).parameter+"Hardness"); }
    public int weatherability(long x,long z) { return params.integer(material(x,z,index(x,z,surface(x,z))).parameter+"Weatherability"); }
    public int age(long x,long z) { return inputs.get(Fields.CRUST_AGE,x,z)*(4-index(x,z,surface(x,z)))/4; }
    public RockColumn column(long x,long z) {
        Lattice.check(x);Lattice.check(z);long top=contact(x,z,2),t=params.integer("geologyLayerThickness");
        Long[] bounds={null,top-3*t,top-t,top,null};RockColumn.Layer[] layers=new RockColumn.Layer[4];
        int crustAge=inputs.get(Fields.CRUST_AGE,x,z);
        for(int k=0;k<4;k++)layers[k]=new RockColumn.Layer(material(x,z,k),bounds[k],bounds[k+1],crustAge*(4-k)/4);
        return new RockColumn(surface(x,z),layers);
    }
}
