package genesis.core.fields;

/** Immutable structured field value. Bounds are model metres, lower inclusive / upper exclusive.
 * Null ends mean unbounded material volume, not an infinitely thick deposited event.
 */
public final class RockColumn {
    public enum Rock {
        BASALT(0,"Basalt","basalt"), GRANITE(1,"Granite","granite"),
        SHALE(2,"Shale","shale"), LIMESTONE(3,"Limestone","limestone"), SANDSTONE(4,"Sandstone","sandstone");
        public final int id;
        public final String label, parameter;
        Rock(int id,String label,String parameter) { this.id=id;this.label=label;this.parameter=parameter; }
    }
    public static final class Layer {
        public final Rock rock;
        public final Long lower, upper;
        public final int formationAge;
        public Layer(Rock rock,Long lower,Long upper,int age) {
            if(rock==null||age<0||(lower!=null&&upper!=null&&lower>=upper))throw new IllegalArgumentException("Invalid rock interval");
            this.rock=rock;this.lower=lower;this.upper=upper;formationAge=age;
        }
        public boolean contains(long y) { return (lower==null||y>=lower)&&(upper==null||y<upper); }
    }
    private final Layer[] layers;
    public final long surfaceY;
    public RockColumn(long surfaceY,Layer[] layers) {
        if(layers==null||layers.length==0)throw new IllegalArgumentException("Empty rock column");
        this.surfaceY=surfaceY;this.layers=layers.clone();
        for(int i=0;i<this.layers.length;i++) {
            Layer layer=this.layers[i];if(layer==null)throw new IllegalArgumentException("Null rock layer");
            if(i==0?layer.lower!=null:!java.util.Objects.equals(this.layers[i-1].upper,layer.lower)||layer.lower==null)
                throw new IllegalArgumentException("Column has a gap or overlap");
            if(i==this.layers.length-1?layer.upper!=null:layer.upper==null)throw new IllegalArgumentException("Column end is not unbounded");
        }
    }
    public Layer[] layers() { return layers.clone(); }
    public Layer at(long y) { for(Layer layer:layers)if(layer.contains(y))return layer;throw new IllegalStateException("Incomplete material volume"); }
}
