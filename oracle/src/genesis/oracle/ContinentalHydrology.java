package genesis.oracle;

import genesis.core.hash.Lattice;
import genesis.oracle.ContinentalGroups.Group;
import java.util.LinkedHashMap;
import java.util.Map;

/** Canonical continent-family hydrology on a fixed world lattice, independent of render windows.
 * Finite priority-flood experiment promoted only to a complete bounded sampled continent solve.
 * Not erosion, transient storage, or a proof of drainage on unsampled fine terrain.
 */
public final class ContinentalHydrology {
    public static final String VERSION="continental-hydrology-v1";
    public static final int SAMPLES_PER_SPACING=64;
    public final TectonicTerrain world;
    public final RainfallField rainfall;
    public final int step;
    private final int capacity;
    private final Map<Long,Root> roots=new LinkedHashMap<>(16,.75f,true);
    public ContinentalHydrology(TectonicTerrain world,RainfallField rainfall){this(world,rainfall,12);}
    public ContinentalHydrology(TectonicTerrain world,RainfallField rainfall,int capacity) {
        if(world==null||rainfall==null||capacity<1||capacity>32)throw new IllegalArgumentException("Invalid hydrology context");
        this.world=world;this.rainfall=rainfall;this.capacity=capacity;step=(world.plateParams.integer("plateSpacing")+SAMPLES_PER_SPACING-1)/SAMPLES_PER_SPACING;
    }
    public record Bounds(long x,long z,int width,int height) {}
    public Bounds bounds(Group group) {
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
    public synchronized Root root(Group group) {
        Root result=roots.get(group.id());if(result!=null)return result;
        result=build(group,bounds(group));roots.put(group.id(),result);
        if(roots.size()>capacity)roots.remove(roots.keySet().iterator().next());return result;
    }
    public synchronized void clear(){roots.clear();}
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
        private final int[] bed;
        private final boolean[] active,sea;
        private final long[] source;
        private final ActiveHydrology.Result solve;
        private Root(Group group,Bounds bounds,int step,int[] bed,boolean[] active,boolean[] sea,long[] source,ActiveHydrology.Result solve,long nanos) {
            this.group=group;this.bounds=bounds;this.step=step;this.bed=bed;this.active=active;this.sea=sea;this.source=source;this.solve=solve;buildNanos=nanos;
            int a=0,t=0,l=0;for(int p=0;p<bed.length;p++){if(active[p])a++;if(sea[p])t++;if(active[p]&&!sea[p]&&solve.resolved(p)&&solve.filled(p)>bed[p])l++;}activeCells=a;terminalCells=t;lakeCells=l;
        }
        public int size(){return bed.length;}
        public long x(int p){return bounds.x+(p%bounds.width)*(long)step;}
        public long z(int p){return bounds.z+(p/bounds.width)*(long)step;}
        public int index(long x,long z){long ix=Math.floorDiv(x-bounds.x+step/2,step),iz=Math.floorDiv(z-bounds.z+step/2,step);return ix<0||iz<0||ix>=bounds.width||iz>=bounds.height?-1:(int)(iz*bounds.width+ix);}
        public boolean active(int p){return p>=0&&p<bed.length&&active[p];}
        public boolean sea(int p){return active(p)&&sea[p];}
        public int status(int p){return !active(p)||!solve.resolved(p)?0:sea[p]?2:1;}
        public int bed(int p){return bed[p];}
        public int filled(int p){return solve.filled(p);}
        public long source(int p){return source[p];}
        public long flux(int p){return solve.flux(p);}
        public int downstream(int p){return solve.downstream(p);}
        public int order(int p){return solve.order(p);}
        public long supplied(){return solve.supplied;}
        public long discharged(){return solve.discharged;}
        public long unresolved(){return solve.unresolved;}
        public double lakeDepth(int p){return status(p)==1?Math.max(0,(filled(p)-bed[p])/1000.0):0;}
        /** Distance to nearby committed coarse receiver segments; independent of display zoom. */
        public River river(long x,long z) {
            int center=index(x,z);if(center<0)return new River(Double.POSITIVE_INFINITY,0);
            int cx=center%bounds.width,cz=center/bounds.width;double distance=Double.POSITIVE_INFINITY;long flow=0;
            for(int dz=-2;dz<=2;dz++)for(int dx=-2;dx<=2;dx++) {
                int ix=cx+dx,iz=cz+dz;if(ix<0||iz<0||ix>=bounds.width||iz>=bounds.height)continue;int p=iz*bounds.width+ix,q=downstream(p);
                // Fixed physical-source threshold, not relative to this viewport's maximum.
                if(q<0||sea(p)||flux(p)<16_000L*step*step)continue;
                double ax=x(p),az=z(p),vx=x(q)-ax,vz=z(q)-az,t=Math.max(0,Math.min(1,((x-ax)*vx+(z-az)*vz)/(vx*vx+vz*vz)));
                double d=StrictMath.hypot(x-ax-t*vx,z-az-t*vz);if(d<distance||(d==distance&&flux(p)>flow)){distance=d;flow=flux(p);}
            }
            return new River(distance,flow);
        }
    }
    public record River(double distance,long flux) {}

    private Root build(Group group,Bounds bounds) {
        return build(group,bounds,true);
    }
    private Root build(Group group,Bounds bounds,boolean fastReject) {
        long start=System.nanoTime();int w=bounds.width,h=bounds.height,n=w*h,seaHeight=world.settings.seaLevel()*1000;
        int[] bed=new int[n];boolean[] active=new boolean[n],sea=new boolean[n];long[] source=new long[n];
        for(int p=0;p<n;p++) {
            long x=bounds.x+(p%w)*(long)step,z=bounds.z+(p/w)*(long)step;
            if(fastReject&&!world.mayBelong(group,x,z))continue;
            var s=world.sample(x,z);if(s.continentId()!=group.id())continue;
            if(p%w==0||p%w==w-1||p/w==0||p/w==h-1)throw new IllegalStateException("Continent escaped its complete support box");
            active[p]=true;bed[p]=millimetres(s.elevation(),world.settings.seaLevel());
        }
        // The family border is a constructed deep maritime reserve, not a watershed wall.
        // Flood only submerged family vertices touching that physical margin, then their
        // submerged D8 component. Enclosed depressions are NOT automatically sea outlets.
        int[] queue=new int[n];int tail=0;
        for(int p=0;p<n;p++)if(active[p]&&bed[p]<=seaHeight) {
            for(int q:neighbors(p,w,h))if(q>=0&&!active[q]){sea[p]=true;queue[tail++]=p;break;}
        }
        for(int head=0;head<tail;head++)for(int q:neighbors(queue[head],w,h))if(q>=0&&active[q]&&!sea[q]&&bed[q]<=seaHeight){sea[q]=true;queue[tail++]=q;}
        int[] routingBed=bed.clone();
        for(int p=0;p<n;p++)if(active[p]) {
            if(sea[p])routingBed[p]=seaHeight;
            else source[p]=Math.multiplyExact((long)rain(bounds.x+(p%w)*(long)step,bounds.z+(p/w)*(long)step),(long)step*step);
        }
        var flood=ActiveHydrology.solveD8Rivers(w,h,routingBed,active,sea,source);
        return new Root(group,bounds,step,bed,active,sea,source,flood,System.nanoTime()-start);
    }
    public static int millimetres(double height,int seaLevel) {
        long mm=Math.round(height*1000);if(height>seaLevel)mm=Math.max(mm,seaLevel*1000L+1);else mm=Math.min(mm,seaLevel*1000L);return Math.toIntExact(mm);
    }
    private static int[] neighbors(int p,int w,int h) {
        int x=p%w,z=p/w;return new int[]{x>0?p-1:-1,x+1<w?p+1:-1,z>0?p-w:-1,z+1<h?p+w:-1,
            x>0&&z>0?p-w-1:-1,x+1<w&&z>0?p-w+1:-1,x>0&&z+1<h?p+w-1:-1,x+1<w&&z+1<h?p+w+1:-1};
    }
}
