package genesis.oracle;

import genesis.core.hash.Lattice;
import genesis.oracle.ContinentalGroups.Group;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Canonical continent-family hydrology on a fixed world lattice, independent of render windows.
 * Finite priority-flood experiment promoted only to a complete bounded sampled continent solve.
 * Optional deterministic bedrock erosion; not transient storage or a proof of fine drainage.
 */
public final class ContinentalHydrology {
    public static final String VERSION="continental-hydrology-v4";
    public static final int SAMPLES_PER_SPACING=64;
    public final TectonicTerrain world;
    public final RainfallField rainfall;
    public final TerrainHardness hardness;
    public final ClimateField climate;
    public final TerrainSubstrate substrate;
    public final int erosionStrength;
    public final int step;
    private final int capacity;
    private final Map<Long,Root> roots=new LinkedHashMap<>(16,.75f,true);
    private final Map<Long,Pending> inFlight=new HashMap<>();
    private final int concurrentBuilds;
    private long cacheGeneration;
    private record Pending(long generation,CompletableFuture<Root> future) {}
    public ContinentalHydrology(TectonicTerrain world,RainfallField rainfall){this(world,rainfall,12);}
    public ContinentalHydrology(TectonicTerrain world,RainfallField rainfall,int capacity) {
        this(world,rainfall,capacity,0,TerrainHardness.seeded(world));
    }
    public ContinentalHydrology(TectonicTerrain world,RainfallField rainfall,int capacity,int erosionStrength,TerrainHardness hardness) {
        if(world==null||rainfall==null||capacity<1||capacity>32)throw new IllegalArgumentException("Invalid hydrology context");
        if(hardness==null||erosionStrength<0||erosionStrength>3000)throw new IllegalArgumentException("Invalid erosion context");
        this.hardness=hardness;this.erosionStrength=erosionStrength;
        climate=rainfall instanceof ClimateField value?value:null;
        substrate=hardness instanceof TerrainSubstrate value?value:null;
        this.world=world;this.rainfall=rainfall;this.capacity=capacity;concurrentBuilds=Math.min(4,capacity);step=(world.plateParams.integer("plateSpacing")+SAMPLES_PER_SPACING-1)/SAMPLES_PER_SPACING;
    }
    public record Bounds(long x,long z,int width,int height) {}
    public Bounds bounds(Group group) {
        requireGroup(group);
        long loX=Long.MAX_VALUE,loZ=Long.MAX_VALUE,hiX=Long.MIN_VALUE,hiZ=Long.MIN_VALUE,s=world.plateParams.integer("plateSpacing");
        for(var c:group.members()){loX=Math.min(loX,(c.i()-3)*s);loZ=Math.min(loZ,(c.j()-3)*s);hiX=Math.max(hiX,(c.i()+4)*s);hiZ=Math.max(hiZ,(c.j()+4)*s);}
        long x=Math.floorDiv(loX,step)*step,z=Math.floorDiv(loZ,step)*step;
        int w=Math.toIntExact(Math.floorDiv(hiX-x+step-1,step)+1),h=Math.toIntExact(Math.floorDiv(hiZ-z+step-1,step)+1);
        // Complete canonical support, never a clipped box that becomes a false ocean edge.
        Lattice.check(x);Lattice.check(z);Lattice.check(x+(w-1L)*step);Lattice.check(z+(h-1L)*step);
        return new Bounds(x,z,w,h);
    }
    public Group group(TectonicTerrain.Sample s){return world.continent(Math.floorDiv(s.owner().x(),world.plateParams.integer("plateSpacing")),Math.floorDiv(s.owner().z(),world.plateParams.integer("plateSpacing")));}
    public long snap(long coordinate){return Math.floorDiv(coordinate+step/2,step)*step;}
    /** Coalesce equal cold roots while allowing a bounded number of distinct roots to build concurrently. */
    public Root root(Group group) {
        requireGroup(group);long id=group.id();
        for(;;) {
            Pending mine=null,wait;boolean coalesced=false;
            synchronized(this) {
                Root cached=roots.get(id);if(cached!=null)return cached;
                wait=inFlight.get(id);
                if(wait!=null)coalesced=wait.generation==cacheGeneration;
                else if(inFlight.size()<concurrentBuilds){mine=new Pending(cacheGeneration,new CompletableFuture<>());inFlight.put(id,mine);wait=null;}
                else wait=inFlight.values().iterator().next();
            }
            if(mine==null) {
                if(coalesced)return await(wait.future);
                wait.future.handle((value,failure)->null).join();continue;
            }
            try {
                Root built=build(group,bounds(group));
                synchronized(this) {
                    if(inFlight.get(id)==mine) {
                        inFlight.remove(id);
                        if(cacheGeneration==mine.generation) {
                            roots.put(id,built);if(roots.size()>capacity)roots.remove(roots.keySet().iterator().next());
                        }
                    }
                }
                mine.future.complete(built);return built;
            } catch(Throwable failure) {
                synchronized(this){if(inFlight.get(id)==mine)inFlight.remove(id);}
                mine.future.completeExceptionally(failure);
                if(failure instanceof RuntimeException runtime)throw runtime;
                if(failure instanceof Error error)throw error;
                throw new IllegalStateException(failure);
            }
        }
    }
    private static Root await(CompletableFuture<Root> future) {
        try{return future.join();}catch(CompletionException failure){Throwable cause=failure.getCause();
            if(cause instanceof RuntimeException runtime)throw runtime;if(cause instanceof Error error)throw error;throw new IllegalStateException(cause);}
    }
    public synchronized void clear() {
        cacheGeneration++;
        roots.clear();
        // A running build still occupies a concurrency slot.  Keep it visible so a clear
        // cannot start an unbounded second wave; its generation prevents cache publication.
    }
    private void requireGroup(Group group) {
        if(group==null||group.members().isEmpty())throw new IllegalArgumentException("Continent group required");
        var member=group.members().get(0);if(!world.continent(member.i(),member.j()).equals(group))
            throw new IllegalArgumentException("Continent group belongs to a different terrain");
    }
    /** Slow independent-support diagnostic: wider box, no conservative ownership shortcut. */
    public Root referenceRoot(Group group,int padding) {
        if(padding<0||padding>4)throw new IllegalArgumentException("Reference padding 0..4");var b=bounds(group);
        var wider=new Bounds(b.x-padding*(long)step,b.z-padding*(long)step,b.width+2*padding,b.height+2*padding);
        Lattice.check(wider.x);Lattice.check(wider.z);Lattice.check(wider.x+(wider.width-1L)*step);Lattice.check(wider.z+(wider.height-1L)*step);
        return build(group,wider,false);
    }
    public int rain(long x,long z){int r=rainfall.millimetresPerYear(x,z);if(r<0||r>10000)throw new IllegalArgumentException("Rainfall field outside contract");return r;}

    public static final class Root {
        public final Group group;
        public final Bounds bounds;
        public final int step,activeCells,terminalCells,lakeCells;
        public final long buildNanos;
        /** Net open-system incision after locally conservative slope deposition, in height-mm times block area. */
        public final java.math.BigInteger exportedSediment;
        /** Absolute material redistributed by gravity, in height-mm times block area. */
        public final java.math.BigInteger redistributedSediment;
        private final int[] bed;
        private final int[] original;
        private final double[] hardness;
        private final boolean[] active,sea;
        private final long[] source;
        private final short[] rainfall,humidity,soilDepth,runoff,infiltration;
        private final byte[] rock;
        private final WindField wind;
        private final ActiveHydrology.Result solve;
        private final FluvialNetwork fluvial;
        private Root(Group group,Bounds bounds,int step,int[] bed,int[] original,double[] hardness,boolean[] active,boolean[] sea,long[] source,
                     short[] rainfall,short[] humidity,short[] soilDepth,short[] runoff,short[] infiltration,byte[] rock,WindField wind,
                     ActiveHydrology.Result solve,long moved,long startedNanos) {
            this.original=original;this.hardness=hardness;
            long removed=0;for(int p=0;p<bed.length;p++)removed=Math.addExact(removed,(long)original[p]-bed[p]);
            exportedSediment=java.math.BigInteger.valueOf(removed).multiply(java.math.BigInteger.valueOf((long)step*step));
            redistributedSediment=java.math.BigInteger.valueOf(moved).multiply(java.math.BigInteger.valueOf((long)step*step));
            this.group=group;this.bounds=bounds;this.step=step;this.bed=bed;this.active=active;this.sea=sea;this.source=source;this.solve=solve;
            this.rainfall=rainfall;this.humidity=humidity;this.soilDepth=soilDepth;this.runoff=runoff;this.infiltration=infiltration;
            this.rock=rock;this.wind=wind;
            int a=0,t=0,l=0;for(int p=0;p<bed.length;p++){if(active[p])a++;if(sea[p])t++;if(active[p]&&!sea[p]&&solve.resolved(p)&&solve.filled(p)>bed[p])l++;}activeCells=a;terminalCells=t;lakeCells=l;
            fluvial=new FluvialNetwork(this);buildNanos=System.nanoTime()-startedNanos;
        }
        public int size(){return bed.length;}
        public long x(int p){return bounds.x+(p%bounds.width)*(long)step;}
        public long z(int p){return bounds.z+(p/bounds.width)*(long)step;}
        public int index(long x,long z){long ix=Math.floorDiv(x-bounds.x+step/2,step),iz=Math.floorDiv(z-bounds.z+step/2,step);return ix<0||iz<0||ix>=bounds.width||iz>=bounds.height?-1:(int)(iz*bounds.width+ix);}
        public boolean active(int p){return p>=0&&p<bed.length&&active[p];}
        public boolean sea(int p){return active(p)&&sea[p];}
        /** Bilinear connected-maritime mask. Positive values are on the ocean side of the fine coast. */
        public double seaSignalAt(long x,long z) {
            long gx=Math.floorDiv(x-bounds.x,step),gz=Math.floorDiv(z-bounds.z,step);
            double tx=Math.floorMod(x-bounds.x,step)/(double)step,tz=Math.floorMod(z-bounds.z,step)/(double)step,signal=0;
            for(int dz=0;dz<2;dz++)for(int dx=0;dx<2;dx++) {
                long ix=gx+dx,iz=gz+dz;int p=ix<0||iz<0||ix>=bounds.width||iz>=bounds.height?-1:(int)(iz*bounds.width+ix);
                double node=p>=0&&sea(p)?1:-1;
                signal+=node*(dx==0?1-tx:tx)*(dz==0?1-tz:tz);
            }
            return signal;
        }
        public boolean seaAt(long x,long z){return seaSignalAt(x,z)>=0;}
        public int status(int p){return !active(p)||!solve.resolved(p)?0:sea[p]?2:1;}
        public int bed(int p){return bed[p];}
        public int originalBed(int p){return original[p];}
        public double hardness(int p){return hardness[p];}
        public int rainfall(int p){return valid(p)?Short.toUnsignedInt(rainfall[p]):0;}
        public double humidity(int p){return valid(p)?Short.toUnsignedInt(humidity[p])/1000.0:0;}
        public int runoffPermille(int p){return valid(p)?Short.toUnsignedInt(runoff[p]):0;}
        public int infiltrationPermille(int p){return valid(p)?Short.toUnsignedInt(infiltration[p]):0;}
        public int evapotranspirationPermille(int p){return valid(p)?1000-runoffPermille(p)-infiltrationPermille(p):0;}
        public double soilDepth(int p){return valid(p)?Short.toUnsignedInt(soilDepth[p])/1000.0:0;}
        public double bedrockElevation(int p){return valid(p)?bed[p]/1000.0-soilDepth(p):0;}
        public TerrainSubstrate.Rock rock(int p){return TerrainSubstrate.Rock.values()[valid(p)?Byte.toUnsignedInt(rock[p]):0];}
        public TerrainSubstrate.Drainage drainage(int p) {
            int value=infiltrationPermille(p);
            return value>=500?TerrainSubstrate.Drainage.WELL_DRAINED:value>=220?TerrainSubstrate.Drainage.MODERATE:TerrainSubstrate.Drainage.POOR;
        }
        public WindField.Wind wind(long x,long z){return wind==null?null:wind.sample(x,z);}
        private boolean valid(int p){return p>=0&&p<bed.length;}
        public double erosionDepth(int p){return (original[p]-bed[p])/1000.0;}
        public double terrainChange(int p){return (bed[p]-original[p])/1000.0;}
        /** Smooth signed terrain change only within this family, fading to zero at inactive/ocean vertices.
         * A continuous detail surface, not a proof of fine-grid channel descent.
         */
        public double elevation(long x,long z,double raw,int seaLevel) {
            int nearest=index(x,z);if(sea(nearest))return raw;
            long gx=Math.floorDiv(x-bounds.x,step),gz=Math.floorDiv(z-bounds.z,step);
            double tx=Math.floorMod(x-bounds.x,step)/(double)step,tz=Math.floorMod(z-bounds.z,step)/(double)step,change=0;
            for(int dz=0;dz<2;dz++)for(int dx=0;dx<2;dx++) {
                long ix=gx+dx,iz=gz+dz;if(ix<0||iz<0||ix>=bounds.width||iz>=bounds.height)continue;
                int p=(int)(iz*bounds.width+ix);if(active(p)&&!sea(p))change+=terrainChange(p)*(dx==0?1-tx:tx)*(dz==0?1-tz:tz);
            }
            double result=raw+change;
            return raw>seaLevel?Math.max(seaLevel+.001,result):Math.max(1,result);
        }
        public int filled(int p){return solve.filled(p);}
        public long source(int p){return source[p];}
        public long flux(int p){return solve.flux(p);}
        public int downstream(int p){return solve.downstream(p);}
        public int order(int p){return solve.order(p);}
        public long supplied(){return solve.supplied;}
        public long discharged(){return solve.discharged;}
        public long unresolved(){return solve.unresolved;}
        public double lakeDepth(int p){return status(p)==1?Math.max(0,(filled(p)-bed[p])/1000.0):0;}
        public FluvialNetwork fluvial(){return fluvial;}
        public FluvialNetwork.Profile profile(int sourceSegment){return fluvial.profile(sourceSegment);}
        public FluvialNetwork.Channel channelAt(long x,long z){return fluvial.channelAt(x,z);}
        public FluvialNetwork.LakeSample lakeAt(long x,long z,double fineBedElevation){return fluvial.lakeAt(x,z,fineBedElevation);}
        /** Distance to nearby committed coarse receiver segments; independent of display zoom. */
        public River river(long x,long z) {
            var channel=channelAt(x,z);return channel==null?new River(Double.POSITIVE_INFINITY,0):new River(channel.threadDistance(),channel.profile().flux());
        }
    }
    public record River(double distance,long flux) {}

    private Root build(Group group,Bounds bounds) {
        return build(group,bounds,true);
    }
    private Root build(Group group,Bounds bounds,boolean fastReject) {
        long start=System.nanoTime();int w=bounds.width,h=bounds.height,n=w*h,seaHeight=world.seaLevel()*1000;
        int[] bed=new int[n];boolean[] active=new boolean[n],sea=new boolean[n];long[] source=new long[n];
        double[] resistance=new double[n],exposure=new double[n];
        float[] permeability=new float[n],weatherability=new float[n];byte[] rock=new byte[n];
        for(int p=0;p<n;p++) {
            long x=bounds.x+(p%w)*(long)step,z=bounds.z+(p/w)*(long)step;
            if(fastReject&&!world.mayBelong(group,x,z))continue;
            var s=world.sample(x,z);if(s.continentId()!=group.id())continue;
            if(p%w==0||p%w==w-1||p/w==0||p/w==h-1)throw new IllegalStateException("Continent escaped its complete support box");
            active[p]=true;bed[p]=millimetres(s.elevation(),world.seaLevel());
            if(substrate!=null) {
                var base=substrate.base(x,z,s);rock[p]=(byte)base.rock().ordinal();resistance[p]=base.resistance();
                permeability[p]=(float)base.permeability();weatherability[p]=(float)base.weatherability();
            } else resistance[p]=hardness.resistance(x,z);
            exposure[p]=HydraulicErosion.exposure(s.owner().age());
            if(!Double.isFinite(resistance[p])||resistance[p]<0||resistance[p]>1)throw new IllegalArgumentException("Hardness field outside 0..1");
        }
        // The family border is a constructed deep maritime reserve, not a watershed wall.
        // Flood only submerged family vertices touching that physical margin, then their
        // submerged D8 component. Enclosed depressions are NOT automatically sea outlets.
        int[] queue=new int[n];int tail=0;
        for(int p=0;p<n;p++)if(active[p]&&bed[p]<=seaHeight) {
            for(int q:neighbors(p,w,h))if(q>=0&&!active[q]){sea[p]=true;queue[tail++]=p;break;}
        }
        for(int head=0;head<tail;head++)for(int q:neighbors(queue[head],w,h))if(q>=0&&active[q]&&!sea[q]&&bed[q]<=seaHeight){sea[q]=true;queue[tail++]=q;}
        ClimateField.Grid climateGrid=climate==null?null:climate.solve(bounds,step,bed,active,sea,seaHeight);
        short[] localRain=new short[n],humidity=new short[n],soilDepth=new short[n],runoff=new short[n],infiltration=new short[n];
        long cellArea=(long)step*step;
        for(int p=0;p<n;p++)if(active[p]) {
            long x=bounds.x+(p%w)*(long)step,z=bounds.z+(p/w)*(long)step;
            int precipitation=climateGrid==null?rain(x,z):climateGrid.rainfall(p);
            int moisture=climateGrid==null?500:climateGrid.humidityPermille(p);
            localRain[p]=(short)precipitation;humidity[p]=(short)moisture;
            int runoffPermille=1000,infiltrationPermille=0;
            if(substrate!=null&&!sea[p]) {
                var base=new TerrainSubstrate.Base(TerrainSubstrate.Rock.values()[Byte.toUnsignedInt(rock[p])],resistance[p],permeability[p],weatherability[p]);
                var ground=substrate.ground(base,slope(p,w,h,bed,active,step),moisture/1000.0);
                soilDepth[p]=(short)Math.round(ground.soilDepth()*1000);runoffPermille=ground.runoffPermille();
                infiltrationPermille=ground.infiltrationPermille();
            }
            runoff[p]=(short)runoffPermille;infiltration[p]=(short)infiltrationPermille;
            if(!sea[p]) {
                long effectiveMillimetres=Math.floorDiv((long)precipitation*runoffPermille+500,1000);
                source[p]=Math.multiplyExact(effectiveMillimetres,cellArea);
            }
        }
        int[] original=bed.clone();
        var evolution=HydraulicErosion.evolve(w,h,bed,active,sea,source,resistance,exposure,step,seaHeight,erosionStrength);
        if(substrate!=null)for(int p=0;p<n;p++)if(active[p]&&!sea[p]) {
            var base=new TerrainSubstrate.Base(TerrainSubstrate.Rock.values()[Byte.toUnsignedInt(rock[p])],resistance[p],permeability[p],weatherability[p]);
            var ground=substrate.ground(base,slope(p,w,h,bed,active,step),Short.toUnsignedInt(humidity[p])/1000.0);
            soilDepth[p]=(short)Math.round(ground.soilDepth()*1000);
        }
        return new Root(group,bounds,step,bed,original,resistance,active,sea,source,localRain,humidity,soilDepth,runoff,infiltration,rock,
            climate==null?null:climate.wind(),evolution.flow(),evolution.massWasting().movedHeightMillimetres(),start);
    }
    public static int millimetres(double height,int seaLevel) {
        long mm=Math.round(height*1000);if(height>seaLevel)mm=Math.max(mm,seaLevel*1000L+1);else mm=Math.min(mm,seaLevel*1000L);return Math.toIntExact(mm);
    }
    private static int[] neighbors(int p,int w,int h) {
        int x=p%w,z=p/w;return new int[]{x>0?p-1:-1,x+1<w?p+1:-1,z>0?p-w:-1,z+1<h?p+w:-1,
            x>0&&z>0?p-w-1:-1,x+1<w&&z>0?p-w+1:-1,x>0&&z+1<h?p+w-1:-1,x+1<w&&z+1<h?p+w+1:-1};
    }
    private static double slope(int p,int w,int h,int[] bed,boolean[] active,int step) {
        double maximum=0;int px=p%w,pz=p/w;
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)if(dx!=0||dz!=0) {
            int x=px+dx,z=pz+dz;if(x<0||z<0||x>=w||z>=h)continue;int q=z*w+x;if(!active[q])continue;
            double distance=step*(dx!=0&&dz!=0?StrictMath.sqrt(2):1);
            maximum=Math.max(maximum,Math.abs(bed[p]-bed[q])/1000.0/distance);
        }
        return maximum;
    }
}
