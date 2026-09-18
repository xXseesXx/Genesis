package genesis.harness;

import genesis.oracle.ContinentalHydrology;
import genesis.oracle.FluvialNetwork;
import genesis.oracle.RainfallField;
import genesis.oracle.TectonicTerrain;
import java.util.ArrayDeque;
import java.util.HashSet;

/** Invariants for the shared, query-order-independent fluvial interpretation. */
final class FluvialNetworkGates {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void close(double actual,double expected,double tolerance,String message) {
        check(Double.isFinite(actual)&&Math.abs(actual-expected)<=tolerance*Math.max(1,Math.abs(expected)),message+": "+actual+" != "+expected);
    }
    public static void main(String[] args){run();}

    static void run() {
        hydraulicFixtures();
        var world=new TectonicTerrain(42,TectonicTerrain.defaultPlateParams(),TectonicTerrain.Settings.defaults());
        var group=world.continent(-1,-1);
        var wetRoot=new ContinentalHydrology(world,new RainfallField.Uniform(1000),1).root(group);
        var dryRoot=new ContinentalHydrology(world,new RainfallField.Uniform(0),1).root(group);
        TopologySummary topology=topology(wetRoot,dryRoot);
        int lakes=lakes(wetRoot,dryRoot);
        GeometrySummary geometry=geometry(wetRoot);
        int backwater=lakeBackwater(wetRoot);
        int counterfactualBraids=0;
        deterministic(world,wetRoot,geometry.segment);
        System.out.println("PASS FLUVIAL NETWORK: exact rainfall-independent area/Strahler, face-connected flat lakes, inlet backwater, discharge hydraulics, two-thread braids, curved deterministic queries and fixed work; channels="+geometry.channels+", cascade="+geometry.cascade+", straight="+geometry.straight+", meandering="+geometry.meandering+", defaultBraided="+geometry.braided+", softWetBraided="+counterfactualBraids+", lakes="+lakes+", backwaterInlets="+backwater+", maxOrder="+topology.maximumOrder);
    }

    private static void hydraulicFixtures() {
        long flux=1_000_000_000L;int step=16_384;
        var a=FluvialNetwork.hydraulicProfile(flux,step,.004,.8,2);
        var b=FluvialNetwork.hydraulicProfile(flux*4,step,.004,.8,2);
        var c=FluvialNetwork.hydraulicProfile(flux*16,step,.004,.8,2);
        for(var profile:new FluvialNetwork.Profile[]{a,b,c}) {
            close(profile.meanDischarge(),profile.flux()*1e-3/FluvialNetwork.SECONDS_PER_YEAR,1e-14,"Flux-to-mean-discharge conversion");
            close(profile.bankfullDischarge(),profile.meanDischarge()*FluvialNetwork.BANKFULL_MULTIPLIER,1e-14,"Bankfull multiplier");
            close(profile.currentWidth()*profile.currentDepth()*profile.currentVelocity(),profile.meanDischarge(),1e-12,"Current Q=A*V");
            close(profile.bankfullWidth()*profile.bankfullDepth()*profile.bankfullVelocity(),profile.bankfullDischarge(),1e-12,"Bankfull Q=A*V");
            check(profile.currentWidth()<=profile.bankfullWidth()&&profile.currentDepth()<=profile.bankfullDepth(),"Current section exceeds formative section");
            check(profile.targetWavelength()>=10*profile.bankfullWidth()&&profile.targetWavelength()<=14*profile.bankfullWidth(),"Meander wavelength is not width-scaled");
        }
        check(a.bankfullWidth()<b.bankfullWidth()&&b.bankfullWidth()<c.bankfullWidth(),"Bankfull width does not grow with flow");
        check(a.bankfullDepth()<b.bankfullDepth()&&b.bankfullDepth()<c.bankfullDepth(),"Bankfull depth does not grow with flow");
        check(a.bankfullVelocity()<b.bankfullVelocity()&&b.bankfullVelocity()<c.bankfullVelocity(),"Bankfull speed does not grow with flow");
        check(a.currentWidth()<b.currentWidth()&&b.currentWidth()<c.currentWidth(),"Current width does not grow with flow");
        check(a.currentDepth()<b.currentDepth()&&b.currentDepth()<c.currentDepth(),"Current depth does not grow with flow");
        check(a.currentVelocity()<b.currentVelocity()&&b.currentVelocity()<c.currentVelocity(),"Current speed does not grow with flow");

        check(FluvialNetwork.classify(.06,.8,2,10)==FluvialNetwork.Planform.CASCADE,"Steep reach is not a cascade");
        check(FluvialNetwork.classify(.012,.8,4,60)==FluvialNetwork.Planform.STRAIGHT,"Hard reach is not straight");
        check(FluvialNetwork.classify(.002,.5,3,10)==FluvialNetwork.Planform.MEANDERING,"Low-gradient erodible reach does not meander");
        check(FluvialNetwork.classify(.008,.1,4,60)==FluvialNetwork.Planform.BRAIDED,"Wide erodible reach does not braid");

        var braided=new FluvialNetwork.Profile(8,8,96,20,3,1.6,8,1,1,.008,.04,4,
            FluvialNetwork.Planform.BRAIDED,3,240);
        var channel=new FluvialNetwork.Channel(1,2,.5,-1,9,1,0,0,-2,0,1,0,5,4,7,braided);
        check(channel.threadCount()==2&&channel.threadFraction()==.5,"Braided reach is not two equal threads");
        close(channel.threadWidth(),4,0,"Braided width split");close(channel.threadDischarge(),4,0,"Braided discharge split");
        close(channel.threadCount()*channel.threadWidth()*braided.currentDepth()*braided.currentVelocity(),braided.meanDischarge(),1e-12,"Combined braided Q=A*V");
        check(channel.insideCurrent()&&channel.insideBankfull(),"Braided current/bankfull masks use the wrong distance");
        var outsideBank=new FluvialNetwork.Channel(1,2,.5,1,11,1,0,0,2,0,1,0,5,4,7,braided);
        check(outsideBank.insideCurrent()&&!outsideBank.insideBankfull(),"Bankfull mask followed a thread instead of the center corridor");
    }

    private record TopologySummary(int maximumOrder) {}
    private static TopologySummary topology(ContinentalHydrology.Root root,ContinentalHydrology.Root dry) {
        int n=root.size(),resolved=0,processed=0,maximum=0;int[] incoming=new int[n],maxIncoming=new int[n],equalMax=new int[n];
        long[] areaCells=new long[n];var queue=new ArrayDeque<Integer>();
        check(n==dry.size(),"Rain changed root support");
        for(int p=0;p<n;p++)if(root.status(p)!=0) {
            resolved++;areaCells[p]=root.sea(p)?0:1;int q=root.downstream(p);if(q>=0)incoming[q]++;
        }
        for(int p=0;p<n;p++)if(root.status(p)!=0&&incoming[p]==0)queue.addLast(p);
        while(!queue.isEmpty()) {
            int p=queue.removeFirst();processed++;
            int expected=maxIncoming[p]==0?(root.sea(p)?0:1):maxIncoming[p]+(equalMax[p]>=2?1:0);
            check(root.fluvial().strahlerOrder(p)==expected,"Strahler recurrence mismatch at "+p);
            maximum=Math.max(maximum,expected);int q=root.downstream(p);
            if(q>=0) {
                areaCells[q]=Math.addExact(areaCells[q],areaCells[p]);
                if(expected>0){if(expected>maxIncoming[q]){maxIncoming[q]=expected;equalMax[q]=1;}else if(expected==maxIncoming[q])equalMax[q]++;}
                if(--incoming[q]==0)queue.addLast(q);
            }
        }
        check(processed==resolved,"Independent topological walk did not consume the drainage DAG");
        check(maximum>=3,"Natural fixture never exercises a Strahler promotion");
        long cellArea=(long)root.step*root.step;
        for(int p=0;p<n;p++) {
            check(root.fluvial().contributingCells(p)==areaCells[p],"Contributing-cell accumulation mismatch at "+p);
            check(root.fluvial().contributingArea(p)==Math.multiplyExact(areaCells[p],cellArea),"Contributing area mismatch at "+p);
            int d=dry.index(root.x(p),root.z(p));check(d==p,"Equal supports use different indices");
            check(root.downstream(p)==dry.downstream(d),"Rain changed fixed-bed routing at "+p);
            check(root.fluvial().contributingCells(p)==dry.fluvial().contributingCells(d),"Contributing area depends on rainfall at "+p);
            check(root.fluvial().strahlerOrder(p)==dry.fluvial().strahlerOrder(d),"Strahler order depends on rainfall at "+p);
        }
        return new TopologySummary(maximum);
    }

    private static int lakes(ContinentalHydrology.Root root,ContinentalHydrology.Root dry) {
        var network=root.fluvial();int components=network.lakes().size(),n=root.size();
        check(components>0,"Natural fixture has no retained lakes");
        int[] counts=new int[components];double[] depths=new double[components];boolean[] visited=new boolean[n];
        var ids=new HashSet<Long>();int connectedComponents=0,maritime=-1;
        for(int p=0;p<n;p++) {
            if(root.sea(p)&&maritime<0)maritime=p;
            var lake=network.lakeForCell(p);if(lake==null)continue;
            counts[lake.component()]++;depths[lake.component()]=Math.max(depths[lake.component()],(root.filled(p)-root.bed(p))/1000.0);
            check(root.filled(p)==lake.surfaceMillimetres(),"Lake component is not level");
            var dryLake=dry.fluvial().lakeForCell(p);check(dryLake!=null&&dryLake.id()==lake.id(),"Lake identity depends on rainfall");
            var sample=root.lakeAt(root.x(p),root.z(p),root.bed(p)/1000.0);
            check(sample!=null&&sample.wet()&&sample.lake().id()==lake.id(),"Fine lake query lost a coarse wet node");
            close(sample.surface(),lake.surface(),0,"Fine lake surface");close(sample.depth(),lake.surface()-root.bed(p)/1000.0,1e-12,"Fine lake depth");
        }
        for(var lake:network.lakes()) {
            check(ids.add(lake.id()),"Duplicate stable lake id");
            check(counts[lake.component()]==lake.cellCount(),"Lake cell count mismatch");
            close(depths[lake.component()],lake.maxDepth(),1e-12,"Lake maximum depth");
            check(lake.spillCell()>=0&&lake.flux()==root.flux(lake.spillCell()),"Lake spill flux mismatch");
            check(lake.outletCell()<0||network.lakeForCell(lake.outletCell())==null||network.lakeForCell(lake.outletCell()).component()!=lake.component(),"Lake outlet remains inside its component");
        }
        var queue=new ArrayDeque<Integer>();
        for(int start=0;start<n;start++)if(!visited[start]&&network.lakeForCell(start)!=null) {
            var lake=network.lakeForCell(start);int seen=0;visited[start]=true;queue.add(start);connectedComponents++;
            while(!queue.isEmpty()) {
                int p=queue.removeFirst();seen++;int px=p%root.bounds.width(),pz=p/root.bounds.width();
                for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)if(Math.abs(dx)+Math.abs(dz)==1) {
                    int x=px+dx,z=pz+dz;if(x<0||z<0||x>=root.bounds.width()||z>=root.bounds.height())continue;
                    int q=z*root.bounds.width()+x;var neighbor=network.lakeForCell(q);
                    if(!visited[q]&&neighbor!=null&&neighbor.component()==lake.component()){visited[q]=true;queue.addLast(q);}
                }
            }
            check(seen==lake.cellCount(),"Same-level lake id is not one D8-connected component");
        }
        check(connectedComponents==components,"Lake component metadata is incomplete");
        check(maritime>=0&&root.lakeAt(root.x(maritime),root.z(maritime),root.bed(maritime)/1000.0-10)==null,"Maritime water was classified as a lake");
        return components;
    }

    private static int lakeBackwater(ContinentalHydrology.Root root) {
        var network=root.fluvial();int inlets=0;
        for(int p=0;p<root.size();p++) {
            var lake=network.receivingLake(p);if(lake==null||network.profile(p)==null)continue;
            check(network.lakeForCell(p)==null,"Profile begins inside a retained lake");
            int q=root.downstream(p);check(q>=0&&network.lakeForCell(q)!=null,"Receiving-lake reach has no lake endpoint");
            var mouth=root.channelAt(root.x(q),root.z(q));
            check(mouth!=null&&network.receivingLake(mouth.sourceSegment())!=null,"Lake inlet vanished at its endpoint");
            var selected=network.receivingLake(mouth.sourceSegment());
            close(mouth.waterSurface(),selected.surface(),1e-12,"River mouth is below downstream lake stage");
            check(mouth.waterSurface()>=mouth.bedElevation(),"Lake backwater did not cover the inlet bed");inlets++;
        }
        check(inlets>0,"Natural fixture has no profiled lake inlet");
        for(int p=0;p<root.size();p++)if(network.lakeForCell(p)!=null)check(network.profile(p)==null,"Flat lake cell received a fake-slope Manning profile");
        return inlets;
    }

    private record GeometrySummary(int segment,int channels,int cascade,int straight,int meandering,int braided) {}
    private static GeometrySummary geometry(ContinentalHydrology.Root root) {
        var network=root.fluvial();int selected=-1,channels=0,cascade=0,straight=0,meandering=0,braided=0,braidedSegment=-1,continuous=0;double bestCurve=-1;
        for(int p=0;p<root.size();p++) {
            var profile=network.profile(p);if(profile==null)continue;channels++;
            switch(profile.planform()) {case CASCADE->cascade++;case STRAIGHT->straight++;case MEANDERING->meandering++;case BRAIDED->braided++;}
            close(profile.currentWidth()*profile.currentDepth()*profile.currentVelocity(),profile.meanDischarge(),1e-12,"Natural current Q=A*V");
            close(profile.bankfullWidth()*profile.bankfullDepth()*profile.bankfullVelocity(),profile.bankfullDischarge(),1e-12,"Natural bankfull Q=A*V");
            int q=root.downstream(p);check(q>=0,"Profile without receiver");
            var start=network.centerline(p,0);var end=network.centerline(p,1);
            close(start.x(),root.x(p),0,"Centerline source x");close(start.z(),root.z(p),0,"Centerline source z");
            close(end.x(),root.x(q),0,"Centerline receiver x");close(end.z(),root.z(q),0,"Centerline receiver z");
            for(int k=1;k<FluvialNetwork.CURVE_SUBDIVISIONS;k++) {
                var point=network.centerline(p,k/(double)FluvialNetwork.CURVE_SUBDIVISIONS);
                check(point.displacement()<=root.step*FluvialNetwork.MAX_DISPLACEMENT_FRACTION+1e-8,"Centerline escaped its coarse corridor");
                if(point.displacement()>bestCurve){bestCurve=point.displacement();selected=p;}
            }
            var next=network.profile(q);if(next!=null) {
                var joined=network.profile(p,1);
                close(joined.meanDischarge(),next.meanDischarge(),1e-12,"Confluence mean-flow continuity");
                close(joined.bankfullWidth(),next.bankfullWidth(),1e-12,"Confluence width continuity");
                close(joined.bankfullDepth(),next.bankfullDepth(),1e-12,"Confluence depth continuity");
                close(joined.currentVelocity(),next.currentVelocity(),1e-12,"Confluence speed continuity");
                check(joined.planform()==profile.planform(),"Segment planform flickers during interpolation");continuous++;
            }
            if(profile.planform()==FluvialNetwork.Planform.BRAIDED&&braidedSegment<0)braidedSegment=p;
        }
        check(channels>0&&selected>=0&&bestCurve>1e-6,"No curved committed channels");
        check(cascade>0&&straight>0&&meandering>0,"Natural fixture does not exercise cascade, straight, and meandering reaches");
        check(continuous>0,"No connected profiled reaches exercise confluence interpolation");
        check(network.maximumSegmentsPerQuery()==25,"Channel query neighborhood is not fixed 5x5");
        check(network.maximumCurveChordsPerQuery()==25*FluvialNetwork.CURVE_SUBDIVISIONS*3,"Channel query work bound changed");
        check(network.channelAt(root.bounds.x()-root.step*2L,root.bounds.z()-root.step*2L)==null,"Out-of-support query found a channel");

        double t=.5;var middle=network.centerline(selected,t);long x=Math.round(middle.x()),z=Math.round(middle.z());
        var first=root.channelAt(x,z);var second=root.channelAt(x,z);check(first!=null&&first.equals(second),"Repeated curved query is stateful");
        check(first.threadDistance()<=root.step*.1+1,"Curved center query missed its local reach");
        close(StrictMath.hypot(first.tangentX(),first.tangentZ()),1,1e-12,"Channel tangent normalization");
        close(first.waterSurface()-first.bedElevation(),first.profile().currentDepth(),1e-12,"Current stage/depth semantics");
        close(first.bankfullWaterSurface()-first.bedElevation(),first.profile().bankfullDepth(),1e-12,"Bankfull surface/depth semantics");
        check(first.waterSurface()<=first.bankfullWaterSurface(),"Current stage exceeds formative surface");

        if(braidedSegment>=0) {
            int q=root.downstream(braidedSegment);var center=network.threadCenterline(braidedSegment,.5,0);
            var left=network.threadCenterline(braidedSegment,.5,-1);var right=network.threadCenterline(braidedSegment,.5,1);
            check(StrictMath.hypot(left.x()-right.x(),left.z()-right.z())>0,"Braided threads do not split");
            close((left.x()+right.x())/2,center.x(),1e-12,"Braided threads are not symmetric in x");
            close((left.z()+right.z())/2,center.z(),1e-12,"Braided threads are not symmetric in z");
            for(int thread:new int[]{-1,1}) {
                var source=network.threadCenterline(braidedSegment,0,thread);
                var receiver=network.threadCenterline(braidedSegment,1,thread);
                close(source.x(),root.x(braidedSegment),0,"Braided source rejoin x");close(source.z(),root.z(braidedSegment),0,"Braided source rejoin z");
                close(receiver.x(),root.x(q),0,"Braided receiver rejoin x");close(receiver.z(),root.z(q),0,"Braided receiver rejoin z");
            }
        }
        return new GeometrySummary(selected,channels,cascade,straight,meandering,braided);
    }

    private static int braidedCounterfactual(TectonicTerrain world,genesis.oracle.ContinentalGroups.Group group) {
        var root=new ContinentalHydrology(world,new RainfallField.Uniform(10_000),1,0,(x,z)->0).root(group);
        var network=root.fluvial();int segment=-1,count=0;
        for(int p=0;p<root.size();p++)if(network.profile(p)!=null&&network.profile(p).planform()==FluvialNetwork.Planform.BRAIDED){count++;if(segment<0)segment=p;}
        check(segment>=0,"Soft, high-flow counterfactual produces no braided reach");
        int q=root.downstream(segment);var center=network.threadCenterline(segment,.5,0);
        var left=network.threadCenterline(segment,.5,-1);var right=network.threadCenterline(segment,.5,1);
        check(StrictMath.hypot(left.x()-right.x(),left.z()-right.z())>0,"Braided threads do not split");
        close((left.x()+right.x())/2,center.x(),1e-12,"Braided threads are not symmetric in x");
        close((left.z()+right.z())/2,center.z(),1e-12,"Braided threads are not symmetric in z");
        for(int thread:new int[]{-1,1}) {
            var source=network.threadCenterline(segment,0,thread);var receiver=network.threadCenterline(segment,1,thread);
            close(source.x(),root.x(segment),0,"Braided source rejoin x");close(source.z(),root.z(segment),0,"Braided source rejoin z");
            close(receiver.x(),root.x(q),0,"Braided receiver rejoin x");close(receiver.z(),root.z(q),0,"Braided receiver rejoin z");
        }
        var profile=network.profile(segment);
        close(2*(profile.currentWidth()/2)*profile.currentDepth()*profile.currentVelocity(),profile.meanDischarge(),1e-12,"Two braided threads do not preserve combined discharge");
        return count;
    }

    private static void deterministic(TectonicTerrain world,ContinentalHydrology.Root first,int segment) {
        var second=new ContinentalHydrology(world,new RainfallField.Uniform(1000),1).root(world.continent(-1,-1));
        int mapped=second.index(first.x(segment),first.z(segment));check(mapped>=0,"Deterministic segment mapping failed");
        check(first.profile(segment).equals(second.profile(mapped)),"Profile depends on build/query order");
        for(int k=0;k<=FluvialNetwork.CURVE_SUBDIVISIONS;k++)
            check(first.fluvial().centerline(segment,k/(double)FluvialNetwork.CURVE_SUBDIVISIONS).equals(second.fluvial().centerline(mapped,k/(double)FluvialNetwork.CURVE_SUBDIVISIONS)),"Curved geometry is not deterministic");
        var point=first.fluvial().centerline(segment,.5);long x=Math.round(point.x()),z=Math.round(point.z());
        check(first.channelAt(x,z).equals(second.channelAt(x,z)),"Channel query differs across cold roots");
    }
}
