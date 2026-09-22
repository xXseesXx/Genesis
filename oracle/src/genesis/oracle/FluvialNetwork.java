package genesis.oracle;

import genesis.core.hash.Hash64;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Immutable geomorphic interpretation of one final continental drainage graph.
 * Topology is derived in linear work. Point queries inspect a fixed 5x5 coarse
 * neighborhood and a fixed number of curve chords, independent of crop/chunk order.
 */
public final class FluvialNetwork {
    public static final String VERSION="fluvial-network-v5";
    public static final double SECONDS_PER_YEAR=365.2425*24*60*60;
    /** Bounded formative-event proxy; mean flow remains separately available. */
    public static final double BANKFULL_MULTIPLIER=12;
    public static final int QUERY_RADIUS=2;
    public static final int CURVE_SUBDIVISIONS=16;
    public static final double MAX_CURVE_WAVES=4;
    public static final double MAX_DISPLACEMENT_FRACTION=.22;
    public static final double MAX_THREAD_OFFSET_FRACTION=.05;
    public static final double MAX_BANKFULL_WIDTH_FRACTION=.36;
    /** Minecraft realization scale: preserve discharge while making channels eight times wider. */
    public static final double CHANNEL_WIDTH_MULTIPLIER=8;
    public static final long MIN_CHANNEL_RAIN_CELLS=16_000;
    private static final long LAKE_DOMAIN=0x464c55564c414b45L;
    private static final long CURVE_DOMAIN=0x464c555643555256L;

    public enum Planform { CASCADE, STRAIGHT, MEANDERING, BRAIDED }

    /** Widths and depths are full rectangular-section dimensions in blocks/metres. */
    public record Profile(long flux,double meanDischarge,double bankfullDischarge,
                          double bankfullWidth,double bankfullDepth,double bankfullVelocity,
                          double currentWidth,double currentDepth,double currentVelocity,
                          double slope,double roughness,int order,Planform planform,
                          double centerlineAmplitude,double targetWavelength) {}

    /** Exact coarse component metadata; the long id is stable across support padding. */
    public record Lake(int component,long id,int surfaceMillimetres,double surface,double maxDepth,
                       long flux,int cellCount,int spillCell,int outletCell,
                       long minX,long minZ,long maxX,long maxZ) {}

    /** Presence is the explicit fine wet mask. Shoreline signal is positive inside. */
    public record LakeSample(Lake lake,boolean wet,double shorelineSignal,double surface,
                             double bedElevation,double depth) {}

    /** Fine component candidate independent of the local bed; positive signal is inside the lake mask. */
    public record LakeMask(Lake lake,double shorelineSignal) {}

    public record CenterlinePoint(double x,double z,double displacement) {}

    /** BRAIDED profiles expose two equal-discharge threads which rejoin at both endpoints. */
    public record Channel(int sourceSegment,int downstream,double t,int thread,double centerDistance,
                          double threadDistance,double centerX,double centerZ,double threadX,double threadZ,
                          double tangentX,double tangentZ,double waterSurface,double bedElevation,
                          double bankfullWaterSurface,Profile profile) {
        public int threadCount(){return profile.planform==Planform.BRAIDED?2:1;}
        public double threadFraction(){return 1.0/threadCount();}
        public double threadWidth(){return profile.currentWidth/threadCount();}
        public double threadDischarge(){return profile.meanDischarge/threadCount();}
        public boolean insideCurrent(){return threadDistance<=threadWidth()/2;}
        public boolean insideBankfull(){return centerDistance<=profile.bankfullWidth/2;}
    }

    private static final class MutableLake {
        final int component,minCell,surface;
        int cells,spill=-1,outlet=-1;
        long maxFlux,outflow,spillFlux=-1,minX=Long.MAX_VALUE,minZ=Long.MAX_VALUE,maxX=Long.MIN_VALUE,maxZ=Long.MIN_VALUE;
        double maxDepth;
        MutableLake(int component,int minCell,int surface){this.component=component;this.minCell=minCell;this.surface=surface;}
    }
    private record Closest(double distance,double t,double x,double z,double tangentX,double tangentZ) {}

    private final ContinentalHydrology.Root root;
    private final HydrologyTuning tuning;
    private final long[] contributingCells;
    private final int[] strahler,lakeByCell;
    private final Profile[] profiles;
    private final java.util.Map<Integer,Profile> branches=new java.util.HashMap<>();
    private final List<Lake> lakes;

    FluvialNetwork(ContinentalHydrology.Root root) {
        this(root,HydrologyTuning.defaults());
    }

    FluvialNetwork(ContinentalHydrology.Root root,HydrologyTuning tuning) {
        if(root==null)throw new IllegalArgumentException("Hydrology root required");
        if(tuning==null)throw new IllegalArgumentException("Hydrology tuning required");
        this.root=root;this.tuning=tuning;int n=root.size();
        contributingCells=new long[n];strahler=new int[n];lakeByCell=new int[n];profiles=new Profile[n];
        Arrays.fill(lakeByCell,-1);
        int[] sequence=new int[n],maxIncoming=new int[n],equalMax=new int[n];Arrays.fill(sequence,-1);
        for(int p=0;p<n;p++)if(root.active(p)) {
            if(!root.sea(p))contributingCells[p]=1;
            if(root.status(p)!=0)sequence[root.order(p)]=p;
        }
        // Upstream to downstream: receiver order is always smaller.
        for(int k=n-1;k>=0;k--){int p=sequence[k];if(p<0)continue;int q=root.downstream(p);
            if(q>=0)contributingCells[q]=Math.addExact(contributingCells[q],contributingCells[p]);}
        for(int k=n-1;k>=0;k--){int p=sequence[k];if(p<0)continue;
            int incoming=maxIncoming[p],value=incoming==0?(root.sea(p)?0:1):incoming+(equalMax[p]>=2?1:0);strahler[p]=value;
            int q=root.downstream(p);if(q>=0&&value>0){if(value>maxIncoming[q]){maxIncoming[q]=value;equalMax[q]=1;}else if(value==maxIncoming[q])equalMax[q]++;}}
        lakes=buildLakes();
        long threshold=Math.multiplyExact(tuning.minimumChannelCells(),(long)root.step*root.step);
        for(int p=0;p<n;p++) {
            int q=root.downstream(p);long channelFlux=root.primaryFlux(p);
            int lake=lakeByCell[p];
            // Lake interiors have no channel profile. Every exit branch begins at its
            // actual lake source cell, preserving lake-to-river continuity.
            if(q<0||root.sea(p)||(lake>=0&&lakeByCell[q]==lake)||channelFlux<threshold)continue;
            double dx=root.x(q)-root.x(p),dz=root.z(q)-root.z(p),distance=StrictMath.hypot(dx,dz);
            double slope=Math.max(0,(root.filled(p)-root.filled(q))/1000.0/distance);
            profiles[p]=hydraulicProfile(channelFlux,root.step,slope,root.hardness(p),Math.max(1,strahler[p]),tuning);
        }
        for(int p=0;p<n;p++)if(!root.sea(p))for(int edge=0;edge<root.receiverCount(p);edge++) {
            int q=root.receiver(p,edge);long flux=root.edgeFlux(p,edge);
            if(q==root.downstream(p)||flux<threshold||(lakeByCell[p]>=0&&lakeByCell[p]==lakeByCell[q]))continue;
            double distance=StrictMath.hypot(root.x(q)-root.x(p),root.z(q)-root.z(p));
            branches.put(p*8+edge,hydraulicProfile(flux,root.step,Math.max(0,(root.filled(p)-root.filled(q))/1000.0/distance),
                root.hardness(p),Math.max(1,strahler[p]),tuning));
        }
    }

    public long contributingCells(int p){return valid(p)?contributingCells[p]:0;}
    public long contributingArea(int p){return Math.multiplyExact(contributingCells(p),(long)root.step*root.step);}
    public int strahlerOrder(int p){return valid(p)?strahler[p]:0;}
    public Profile profile(int sourceSegment){return valid(sourceSegment)?profiles[sourceSegment]:null;}
    /** Profile of an actual apportioned flow edge, including secondary branches. */
    public Profile edgeProfile(int p,int edge) {
        return root.receiver(p,edge)==root.downstream(p)?profiles[p]:branches.get(p*8+edge);
    }
    public Profile profile(int sourceSegment,double t) {
        Profile source=profile(sourceSegment);int q=valid(sourceSegment)?root.downstream(sourceSegment):-1;
        if(source==null||!Double.isFinite(t)||t<0||t>1)throw new IllegalArgumentException("Invalid profile sample");
        return interpolate(source,q<0?null:profiles[q],t);
    }
    public Lake lakeForCell(int p){int c=valid(p)?lakeByCell[p]:-1;return c<0?null:lakes.get(c);}
    /** Lake receiving this reach, or null when the reach does not enter a retained basin. */
    public Lake receivingLake(int sourceSegment) {
        int q=valid(sourceSegment)?root.downstream(sourceSegment):-1,c=q<0?-1:lakeByCell[q];
        return c<0?null:lakes.get(c);
    }
    public List<Lake> lakes(){return lakes;}
    public int maximumSegmentsPerQuery(){int side=2*QUERY_RADIUS+1;return side*side*8;}
    public int maximumCurveChordsPerQuery(){return maximumSegmentsPerQuery()*CURVE_SUBDIVISIONS*3;}

    public static long minimumChannelFlux(int step) {
        if(step<1)throw new IllegalArgumentException("Positive hydrology step required");
        return Math.multiplyExact(MIN_CHANNEL_RAIN_CELLS,(long)step*step);
    }

    /** Physical mean discharge: flux is routed-runoff-mm * square-blocks / year and one block is one metre. */
    public static double meanDischarge(long flux) {
        if(flux<0)throw new IllegalArgumentException("Nonnegative flux required");
        return flux*1e-3/SECONDS_PER_YEAR;
    }

    /** Deterministic process classification; curve phase never chooses the style. */
    public static Planform classify(double slope,double hardness,int order,double widthDepthRatio) {
        if(!Double.isFinite(slope)||slope<0||!Double.isFinite(hardness)||hardness<0||hardness>1
            ||order<1||!Double.isFinite(widthDepthRatio)||widthDepthRatio<0)
            throw new IllegalArgumentException("Invalid planform inputs");
        if(slope>=.03)return Planform.CASCADE;
        if(order>=3&&hardness<=.38&&slope>=.0015&&slope<=.018&&widthDepthRatio>=35)return Planform.BRAIDED;
        if(order>=2&&hardness<=.72&&slope<=.006)return Planform.MEANDERING;
        return Planform.STRAIGHT;
    }

    /** Manning-style rectangular sections constrained by an empirical Q^1/2 width prior. */
    public static Profile hydraulicProfile(long flux,int step,double slope,double hardness,int order) {
        return hydraulicProfile(flux,step,slope,hardness,order,HydrologyTuning.defaults());
    }

    private static Profile hydraulicProfile(long flux,int step,double slope,double hardness,int order,HydrologyTuning tuning) {
        if(flux<0||step<1||!Double.isFinite(slope)||slope<0||!Double.isFinite(hardness)||hardness<0||hardness>1||order<1)
            throw new IllegalArgumentException("Invalid hydraulic profile inputs");
        double mean=meanDischarge(flux),bank=mean*tuning.bankfullMultiplier();
        if(mean==0)return new Profile(0,0,0,0,0,0,0,0,0,slope,.035,order,Planform.STRAIGHT,0,0);
        double widthMultiplier=tuning.channelWidthMultiplier();
        double bankWidth=widthMultiplier*Math.min(step*MAX_BANKFULL_WIDTH_FRACTION,Math.max(.75,3.5*StrictMath.sqrt(bank)));
        double provisionalDepth=manningDepth(bank,bankWidth,.035,slope,step*.20);
        Planform form=classify(slope,hardness,order,bankWidth/Math.max(.01,provisionalDepth));
        if(!tuning.braidedChannels()&&form==Planform.BRAIDED)form=Planform.MEANDERING;
        double roughness=switch(form) {
            case CASCADE->.045+.020*hardness;
            case STRAIGHT->.025+.012*hardness;
            case MEANDERING->.035+.012*(1-hardness);
            case BRAIDED->.030+.015*(1-hardness);
        };
        double bankDepth=manningDepth(bank,bankWidth,roughness,slope,step*.20);
        double currentWidth=Math.min(bankWidth,Math.max(.35*widthMultiplier,bankWidth*StrictMath.pow(mean/bank,.45)));
        double currentDepth=Math.min(bankDepth,manningDepth(mean,currentWidth,roughness,slope,bankDepth));
        double bankVelocity=bank/(bankWidth*bankDepth),currentVelocity=mean/(currentWidth*currentDepth);
        double amplitude=Math.min(step*tuning.meanderLimit(),bankWidth*switch(form){case CASCADE->.08;case STRAIGHT->.25;case MEANDERING->1.70;case BRAIDED->.75;});
        double wavelength=Math.max(bankWidth*10,Math.min(bankWidth*14,bankWidth*12));
        return new Profile(flux,mean,bank,bankWidth,bankDepth,bankVelocity,currentWidth,currentDepth,currentVelocity,
            slope,roughness,order,form,amplitude,wavelength);
    }

    private static double manningDepth(double discharge,double width,double roughness,double slope,double cap) {
        if(discharge<=0)return 0;
        double energySlope=Math.max(1e-5,slope),lo=.02,hi=Math.max(.02,cap);
        if(manningFlow(width,hi,roughness,energySlope)<discharge)return hi;
        for(int iteration=0;iteration<32;iteration++) {
            double mid=(lo+hi)/2;
            if(manningFlow(width,mid,roughness,energySlope)<discharge)lo=mid;else hi=mid;
        }
        return (lo+hi)/2;
    }

    private static double manningFlow(double width,double depth,double roughness,double slope) {
        double area=width*depth,radius=area/(width+2*depth);
        return area/roughness*StrictMath.pow(radius,2.0/3)*StrictMath.sqrt(slope);
    }

    /** Cubic downstream tangent plus zero-end-slope oscillation, with exact inherited endpoints. */
    public CenterlinePoint centerline(int sourceSegment,double t) {
        Profile profile=profile(sourceSegment);int q=valid(sourceSegment)?root.downstream(sourceSegment):-1;
        return centerline(sourceSegment,q,profile,t);
    }

    public CenterlinePoint edgeCenterline(int p,int edge,double t) {
        return centerline(p,root.receiver(p,edge),edgeProfile(p,edge),t);
    }

    private CenterlinePoint centerline(int sourceSegment,int q,Profile profile,double t) {
        if(profile==null||q<0||!Double.isFinite(t)||t<0||t>1)throw new IllegalArgumentException("Invalid channel segment/sample");
        double ax=root.x(sourceSegment),az=root.z(sourceSegment),bx=root.x(q),bz=root.z(q);
        if(t==0)return new CenterlinePoint(ax,az,0);if(t==1)return new CenterlinePoint(bx,bz,0);
        double dx=bx-ax,dz=bz-az,length=StrictMath.hypot(dx,dz),nx=-dz/length,nz=dx/length;
        int downstream=root.downstream(q);double endX=dx,endZ=dz;
        if(downstream>=0) {
            endX=root.x(downstream)-bx;endZ=root.z(downstream)-bz;double endLength=StrictMath.hypot(endX,endZ);
            if(endLength>0){endX=endX/endLength*length;endZ=endZ/endLength*length;}else{endX=dx;endZ=dz;}
        }
        double t2=t*t,t3=t2*t;
        double hx=(2*t3-3*t2+1)*ax+(t3-2*t2+t)*dx+(-2*t3+3*t2)*bx+(t3-t2)*endX;
        double hz=(2*t3-3*t2+1)*az+(t3-2*t2+t)*dz+(-2*t3+3*t2)*bz+(t3-t2)*endZ;
        double waves=Math.min(MAX_CURVE_WAVES,Math.max(.25,length/Math.max(.001,profile.targetWavelength)));
        long seed=Hash64.stream(root.group.id(),CURVE_DOMAIN);
        long hash=Hash64.hash(seed,0,root.x(sourceSegment),root.z(sourceSegment));
        double phase=(hash>>>11)*0x1.0p-53*StrictMath.PI*2;
        double envelope=StrictMath.sin(StrictMath.PI*t);envelope*=envelope;
        double offset=profile.centerlineAmplitude*envelope*StrictMath.sin(2*StrictMath.PI*waves*t+phase);
        double lx=ax+dx*t,lz=az+dz*t,cx=hx+nx*offset,cz=hz+nz*offset;
        double deviation=StrictMath.hypot(cx-lx,cz-lz),maximum=root.step*tuning.meanderLimit();
        if(deviation>maximum){double scale=maximum/deviation;cx=lx+(cx-lx)*scale;cz=lz+(cz-lz)*scale;deviation=maximum;}
        return new CenterlinePoint(cx,cz,deviation);
    }

    /** Center thread (0) or one of the two braided threads (-1/+1); all share exact endpoints. */
    public CenterlinePoint threadCenterline(int sourceSegment,double t,int thread) {
        return threadCenterline(sourceSegment,root.downstream(sourceSegment),profiles[sourceSegment],t,thread);
    }

    private CenterlinePoint threadCenterline(int sourceSegment,int q,Profile profile,double t,int thread) {
        if(thread<-1||thread>1)throw new IllegalArgumentException("Thread must be -1, 0, or 1");
        CenterlinePoint center=centerline(sourceSegment,q,profile,t);
        if(thread==0||profile.planform!=Planform.BRAIDED||t==0||t==1)return center;
        double epsilon=1.0/CURVE_SUBDIVISIONS;
        CenterlinePoint before=centerline(sourceSegment,q,profile,Math.max(0,t-epsilon)),after=centerline(sourceSegment,q,profile,Math.min(1,t+epsilon));
        double dx=after.x-before.x,dz=after.z-before.z,length=StrictMath.hypot(dx,dz);
        if(length==0)return center;
        double envelope=StrictMath.sin(StrictMath.PI*t);envelope*=envelope;
        double separation=Math.min(root.step*MAX_THREAD_OFFSET_FRACTION,profile.bankfullWidth*.22)*envelope*thread;
        return new CenterlinePoint(center.x-dz/length*separation,center.z+dx/length*separation,center.displacement+Math.abs(separation));
    }

    private Closest closest(int sourceSegment,int q,Profile profile,long x,long z,int thread) {
        CenterlinePoint previous=threadCenterline(sourceSegment,q,profile,0,thread);Closest best=null;
        for(int chord=0;chord<CURVE_SUBDIVISIONS;chord++) {
            CenterlinePoint next=threadCenterline(sourceSegment,q,profile,(chord+1.0)/CURVE_SUBDIVISIONS,thread);
            double vx=next.x-previous.x,vz=next.z-previous.z,length2=vx*vx+vz*vz;
            double local=length2==0?0:Math.max(0,Math.min(1,((x-previous.x)*vx+(z-previous.z)*vz)/length2));
            double px=previous.x+local*vx,pz=previous.z+local*vz,distance=StrictMath.hypot(x-px,z-pz),length=StrictMath.sqrt(length2);
            double t=(chord+local)/CURVE_SUBDIVISIONS,tx=length==0?0:vx/length,tz=length==0?0:vz/length;
            if(best==null||distance<best.distance)best=new Closest(distance,t,px,pz,tx,tz);
            previous=next;
        }
        return best;
    }

    private static Profile interpolate(Profile a,Profile b,double t) {
        if(b==null||t<=0)return a;if(t>=1)t=1;
        double mean=geometric(a.meanDischarge,b.meanDischarge,t),bank=geometric(a.bankfullDischarge,b.bankfullDischarge,t);
        double bankWidth=geometric(a.bankfullWidth,b.bankfullWidth,t),bankDepth=geometric(a.bankfullDepth,b.bankfullDepth,t);
        double currentWidth=geometric(a.currentWidth,b.currentWidth,t),currentDepth=geometric(a.currentDepth,b.currentDepth,t);
        double bankVelocity=bank/(bankWidth*bankDepth),currentVelocity=mean/(currentWidth*currentDepth);
        long flux=t==1?b.flux:Math.round(StrictMath.exp((1-t)*StrictMath.log(a.flux)+t*StrictMath.log(b.flux)));
        return new Profile(flux,mean,bank,bankWidth,bankDepth,bankVelocity,currentWidth,currentDepth,currentVelocity,
            (1-t)*a.slope+t*b.slope,(1-t)*a.roughness+t*b.roughness,t==1?b.order:a.order,a.planform,
            a.centerlineAmplitude,a.targetWavelength);
    }

    private static double geometric(double a,double b,double t){return StrictMath.exp((1-t)*StrictMath.log(a)+t*StrictMath.log(b));}

    /** Closest committed curved segment. Callers decide whether current or bankfull width is desired. */
    public Channel channelAt(long x,long z) {
        int center=root.index(x,z);if(center<0)return null;
        int cx=center%root.bounds.width(),cz=center/root.bounds.width();
        Channel best=null;
        for(int dz=-QUERY_RADIUS;dz<=QUERY_RADIUS;dz++)for(int dx=-QUERY_RADIUS;dx<=QUERY_RADIUS;dx++) {
            int ix=cx+dx,iz=cz+dz;if(ix<0||iz<0||ix>=root.bounds.width()||iz>=root.bounds.height())continue;
            int p=iz*root.bounds.width()+ix;
            for(int edge=0;edge<root.receiverCount(p);edge++) {
            int q=root.receiver(p,edge);Profile profile=edgeProfile(p,edge);
            if(q<0||profile==null)continue;
            int lake=lakeByCell[p];if(lake>=0&&lakeByCell[q]==lake)continue;
            Closest centerClosest=closest(p,q,profile,x,z,0),thread=centerClosest;int threadIndex=0;
            if(profile.planform==Planform.BRAIDED) {
                thread=null;
                for(int candidate:new int[]{-1,1}) {Closest found=closest(p,q,profile,x,z,candidate);
                    if(thread==null||found.distance<thread.distance){thread=found;threadIndex=candidate;}}
            }
            double t=thread.t;
            // Each branch carries its allocated discharge to the junction. Interpolating
            // to the receiver's primary flux would invent/loss water before branches meet.
            Profile local=profile;
            if(best==null||thread.distance<best.threadDistance
                ||thread.distance==best.threadDistance&&(local.flux>best.profile.flux
                    ||local.flux==best.profile.flux&&t>best.t)) {
                double bankfullSurface=((1-t)*root.filled(p)+t*root.filled(q))/1000.0;
                double bed=bankfullSurface-local.bankfullDepth,water=bed+local.currentDepth;
                int receiving=lakeByCell[q];
                if(receiving>=0) {
                    double blend=t*t*(3-2*t),lakeSurface=lakes.get(receiving).surface;
                    water=Math.max(water,water+(lakeSurface-water)*blend);
                }
                int sourceLake=lakeByCell[p];
                if(sourceLake>=0) {
                    double blend=1-t*t*(3-2*t),lakeSurface=lakes.get(sourceLake).surface;
                    water=Math.min(bankfullSurface,Math.max(water,water+(lakeSurface-water)*blend));
                }
                best=new Channel(p,q,t,threadIndex,centerClosest.distance,thread.distance,centerClosest.x,centerClosest.z,thread.x,thread.z,
                    thread.tangentX,thread.tangentZ,water,bed,bankfullSurface,local);
            }
            }
        }
        return best;
    }

    /** Fine component mask from a four-node marching-squares-style signed interpolation. */
    public LakeSample lakeAt(long x,long z,double fineBedElevation) {
        if(!Double.isFinite(fineBedElevation))throw new IllegalArgumentException("Finite fine bed required");
        LakeMask mask=lakeMaskAt(x,z);
        if(mask==null||mask.shorelineSignal<=0||fineBedElevation>=mask.lake.surface)return null;
        return new LakeSample(mask.lake,true,mask.shorelineSignal,mask.lake.surface,fineBedElevation,mask.lake.surface-fineBedElevation);
    }

    /** Component indicator used by the adapter to build a one-voxel solid containment fringe. */
    public LakeMask lakeMaskAt(long x,long z) {
        int nearest=root.index(x,z);if(nearest<0)return null;
        long gx=Math.floorDiv(x-root.bounds.x(),root.step),gz=Math.floorDiv(z-root.bounds.z(),root.step);
        double tx=Math.floorMod(x-root.bounds.x(),root.step)/(double)root.step;
        double tz=Math.floorMod(z-root.bounds.z(),root.step)/(double)root.step;
        int[] corners=new int[4],candidates=new int[4];int candidateCount=0;
        for(int dz=0;dz<2;dz++)for(int dx=0;dx<2;dx++) {
            long ix=gx+dx,iz=gz+dz;int slot=dz*2+dx;
            corners[slot]=ix<0||iz<0||ix>=root.bounds.width()||iz>=root.bounds.height()?-1:(int)(iz*root.bounds.width()+ix);
            int component=corners[slot]<0?-1:lakeByCell[corners[slot]];
            boolean seen=false;for(int k=0;k<candidateCount;k++)seen|=candidates[k]==component;
            if(component>=0&&!seen)candidates[candidateCount++]=component;
        }
        Lake chosen=null;double bestSignal=Double.NEGATIVE_INFINITY;
        for(int k=0;k<candidateCount;k++) {
            int component=candidates[k];Lake lake=lakes.get(component);double signal=0;
            for(int dz=0;dz<2;dz++)for(int dx=0;dx<2;dx++) {
                int p=corners[dz*2+dx];double weight=(dx==0?1-tx:tx)*(dz==0?1-tz:tz),node;
                node=p>=0&&lakeByCell[p]==component?1:-1;
                signal+=weight*node;
            }
            if(signal>bestSignal||(signal==bestSignal&&chosen!=null&&Long.compareUnsigned(lake.id,chosen.id)<0)){bestSignal=signal;chosen=lake;}
        }
        return chosen==null?null:new LakeMask(chosen,bestSignal);
    }

    private List<Lake> buildLakes() {
        int n=root.size();int[] queue=new int[n];var mutable=new ArrayList<MutableLake>();
        for(int start=0;start<n;start++) {
            if(lakeByCell[start]>=0||!lakeCell(start))continue;
            int component=mutable.size(),surface=root.filled(start),head=0,tail=1;queue[0]=start;lakeByCell[start]=component;
            var lake=new MutableLake(component,start,surface);mutable.add(lake);
            while(head<tail) {
                int p=queue[head++],px=p%root.bounds.width(),pz=p/root.bounds.width();
                for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++) {
                    if(Math.abs(dx)+Math.abs(dz)!=1)continue;int x=px+dx,z=pz+dz;if(x<0||z<0||x>=root.bounds.width()||z>=root.bounds.height())continue;
                    int q=z*root.bounds.width()+x;if(lakeByCell[q]<0&&lakeCell(q)&&root.filled(q)==surface){lakeByCell[q]=component;queue[tail++]=q;}
                }
            }
        }
        for(int p=0;p<n;p++)if(lakeByCell[p]>=0) {
            MutableLake lake=mutable.get(lakeByCell[p]);lake.cells++;lake.maxDepth=Math.max(lake.maxDepth,(lake.surface-root.bed(p))/1000.0);
            lake.maxFlux=Math.max(lake.maxFlux,root.flux(p));long x=root.x(p),z=root.z(p);lake.minX=Math.min(lake.minX,x);lake.minZ=Math.min(lake.minZ,z);lake.maxX=Math.max(lake.maxX,x);lake.maxZ=Math.max(lake.maxZ,z);
            for(int edge=0;edge<root.receiverCount(p);edge++)if(lakeByCell[root.receiver(p,edge)]!=lake.component)
                lake.outflow=Math.addExact(lake.outflow,root.edgeFlux(p,edge));
            int q=root.downstream(p);if(q<0||lakeByCell[q]!=lake.component) {
                long flux=root.flux(p);if(lake.spill<0||flux>lake.spillFlux||flux==lake.spillFlux&&p<lake.spill){lake.spill=p;lake.outlet=q;lake.spillFlux=flux;}
            }
        }
        var result=new ArrayList<Lake>(mutable.size());long seed=Hash64.stream(root.group.id(),LAKE_DOMAIN);
        for(var lake:mutable) {
            long id=Hash64.hash(seed,lake.surface,root.x(lake.minCell),root.z(lake.minCell));
            result.add(new Lake(lake.component,id,lake.surface,lake.surface/1000.0,lake.maxDepth,
                lake.outflow,lake.cells,lake.spill,lake.outlet,lake.minX,lake.minZ,lake.maxX,lake.maxZ));
        }
        return List.copyOf(result);
    }

    private boolean lakeCell(int p){return root.active(p)&&!root.sea(p)&&root.status(p)==1&&root.filled(p)>root.bed(p);}
    private boolean valid(int p){return p>=0&&p<root.size();}
}
