package genesis.oracle;

import genesis.core.hash.Lattice;

/**
 * Bounded steady moisture advection over a complete continental support.
 *
 * <p>Wind is the global divergence-free {@link WindField}.  Sea and the reserved maritime
 * margin replenish vapour; a fixed semi-Lagrangian sweep transports it downwind.  Upslope
 * flow removes more moisture than level flow, producing a windward enhancement and a
 * persistent lee-side rain shadow without an unbounded weather simulation.</p>
 */
public final class ClimateField implements RainfallField {
    public static final String VERSION="terrain-climate-v1";
    public static final int ADVECTION_PASSES=24;
    private final WindField wind;
    private final int rainfallScale;
    private final HydrologyTuning tuning;

    public static final class Grid {
        private final short[] humidity,rainfall;
        private Grid(short[] humidity,short[] rainfall){this.humidity=humidity;this.rainfall=rainfall;}
        public int size(){return rainfall.length;}
        public double humidity(int p){return Short.toUnsignedInt(humidity[p])/1000.0;}
        public int humidityPermille(int p){return Short.toUnsignedInt(humidity[p]);}
        public int rainfall(int p){return Short.toUnsignedInt(rainfall[p]);}
    }

    public ClimateField(long seed,int windScale,int rainfallScale) {
        this(seed,windScale,rainfallScale,HydrologyTuning.defaults());
    }

    public ClimateField(long seed,int windScale,int rainfallScale,HydrologyTuning tuning) {
        if(rainfallScale<0||rainfallScale>10_000)throw new IllegalArgumentException("Rainfall scale must be 0..10000 mm/year");
        if(tuning==null)throw new IllegalArgumentException("Hydrology tuning required");
        wind=new WindField(seed,windScale);this.rainfallScale=rainfallScale;this.tuning=tuning;
    }

    public WindField wind(){return wind;}
    public int rainfallScale(){return rainfallScale;}
    public WindField.Wind wind(long x,long z){return wind.sample(x,z);}

    /** Coordinate-only fallback for the RainfallField contract; terrain-aware roots use {@link #solve}. */
    @Override public int millimetresPerYear(long x,long z){Lattice.check(x);Lattice.check(z);return rainfallScale;}

    Grid solve(ContinentalHydrology.Bounds bounds,int step,int[] bed,boolean[] active,boolean[] sea,int seaHeight) {
        return solve(bounds.width(),bounds.height(),bounds.x(),bounds.z(),step,bed,active,sea,seaHeight);
    }

    /** Exposed for deterministic controlled fixtures as well as the production continent solve. */
    public Grid solve(int width,int height,long originX,long originZ,int step,int[] bed,boolean[] active,boolean[] sea,int seaHeight) {
        int n=Math.multiplyExact(width,height);
        if(width<2||height<2||step<1||bed==null||active==null||sea==null
            ||bed.length!=n||active.length!=n||sea.length!=n)throw new IllegalArgumentException("Invalid climate grid");
        Lattice.check(originX);Lattice.check(originZ);
        Lattice.check(Math.addExact(originX,Math.multiplyExact(width-1L,step)));
        Lattice.check(Math.addExact(originZ,Math.multiplyExact(height-1L,step)));
        float[] directionX=new float[n],directionZ=new float[n],humidity=new float[n],next=new float[n],lift=new float[n];
        for(int p=0;p<n;p++) {
            int px=p%width,pz=p/width;long x=originX+px*(long)step,z=originZ+pz*(long)step;
            var sample=wind.sample(x,z);directionX[p]=(float)sample.unitX();directionZ[p]=(float)sample.unitZ();
            humidity[p]=!active[p]||sea[p]?1:tuning.initialHumidityPermille()/1000f;
        }
        double trace=tuning.traceCells();
        for(int p=0;p<n;p++)if(active[p]&&!sea[p]) {
            int px=p%width,pz=p/width;double ux=px-directionX[p]*trace,uz=pz-directionZ[p]*trace;
            double upstream=height(bed,active,sea,width,height,ux,uz,seaHeight);
            lift[p]=(float)Math.max(0,(bed[p]-upstream)/1000.0/(trace*step));
        }
        for(int pass=0;pass<tuning.climatePasses();pass++) {
            for(int p=0;p<n;p++) {
                if(!active[p]||sea[p]){next[p]=1;continue;}
                int px=p%width,pz=p/width;
                double upstream=sample(humidity,width,height,px-directionX[p]*trace,pz-directionZ[p]*trace,1);
                double base=tuning.baseCondensationPermille()/1000.0;
                double condensation=clamp(base+tuning.orographicCondensationPermille()/1000.0*lift[p],base,.95);
                // Small land recycling prevents an unrealistically absolute desert while retaining rain shadows.
                double recycling=tuning.recyclingPermille()/1000.0*(2-upstream);
                next[p]=(float)clamp(upstream*(1-condensation)+recycling,.025,.995);
            }
            float[] swap=humidity;humidity=next;next=swap;
        }
        short[] storedHumidity=new short[n],rain=new short[n];
        for(int p=0;p<n;p++) {
            double q=humidity[p];int moisture=(int)Math.round(clamp(q,0,1)*1000);
            double shape=tuning.rainfallBasePermille()/1000.0+tuning.rainfallHumidityPermille()/1000.0*q
                +Math.min(2.6,tuning.rainfallLiftPermille()/1000.0*lift[p]*q);
            int amount=rainfallScale==0?0:(int)Math.round(clamp(rainfallScale*shape,0,10_000));
            storedHumidity[p]=(short)moisture;rain[p]=(short)amount;
        }
        return new Grid(storedHumidity,rain);
    }

    private static double height(int[] bed,boolean[] active,boolean[] sea,int width,int height,double x,double z,int seaHeight) {
        int x0=(int)StrictMath.floor(x),z0=(int)StrictMath.floor(z);double tx=x-x0,tz=z-z0,result=0;
        for(int dz=0;dz<2;dz++)for(int dx=0;dx<2;dx++) {
            int ix=x0+dx,iz=z0+dz;double value=seaHeight;
            if(ix>=0&&iz>=0&&ix<width&&iz<height) {int p=iz*width+ix;if(active[p]&&!sea[p])value=bed[p];}
            result+=value*(dx==0?1-tx:tx)*(dz==0?1-tz:tz);
        }
        return result;
    }

    private static double sample(float[] values,int width,int height,double x,double z,double outside) {
        int x0=(int)StrictMath.floor(x),z0=(int)StrictMath.floor(z);double tx=x-x0,tz=z-z0,result=0;
        for(int dz=0;dz<2;dz++)for(int dx=0;dx<2;dx++) {
            int ix=x0+dx,iz=z0+dz;double value=ix<0||iz<0||ix>=width||iz>=height?outside:values[iz*width+ix];
            result+=value*(dx==0?1-tx:tx)*(dz==0?1-tz:tz);
        }
        return result;
    }
    private static double clamp(double value,double low,double high){return Math.max(low,Math.min(high,value));}
}
