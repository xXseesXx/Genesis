package genesis.harness;

import genesis.core.Generator;
import genesis.core.Params;
import genesis.core.fields.FieldId;
import genesis.core.fields.FieldRegistry;
import genesis.core.fields.Fields;
import genesis.core.hash.Lattice;
import genesis.core.hydro.BoundaryPorts;
import genesis.core.hydro.CoarseRunoff;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

/** Independent source-forward accumulation of the SAME world graph, not finite D8 routing. */
public final class RunoffGates {
    private static final int[] DX = {0,0,1,0,-1}, DZ = {0,-1,0,1,0};
    private static final List<FieldId<Integer>> FIELDS = List.of(Fields.COARSE_RUNOFF,Fields.RUNOFF_STATUS,Fields.CHANNEL_FLOW);
    private static void check(boolean b,String message) { if(!b)throw new AssertionError(message); }
    public static void run() throws Exception {
        fixtures();
        for (Generator.Model model : Generator.Model.values()) for(long seed : new long[]{42,-1,Long.MIN_VALUE})
            forwardReference(new Generator(seed,Params.defaults(),model));
        geometry(); radiusExtension(); orderAndSupport(); diagnostics();
        System.out.println("PASS RUNOFF: independent source-forward reference, exact window balances, partial frontiers, bounded worst-case work, shared-port flow, cold/order/zoom/concurrency");
    }
    private static void fixtures() {
        for(int radius : new int[]{1,12,32}) {
            Params p = new Params(Map.of("seaSearchRadius",(double)radius)); int s=p.integer("coarseSpacing");
            AtomicInteger reads = new AtomicInteger();
            var inputs = new FieldRegistry.Builder()
                .add(Fields.DRAINAGE_RANK,(x,z)->{reads.incrementAndGet();long d=Math.abs(x/s)+Math.abs(z/s);return d>radius?-1:(int)d;})
                .add(Fields.FLOW_DIRECTION,(x,z)->Math.abs(x/s)+Math.abs(z/s)>radius?-1:x>0?4:x<0?2:z>0?1:z<0?3:0).build();
            var graph=new CoarseRunoff(p,inputs);var terminal=graph.cell(0,0);
            int diamond=1+2*radius*(radius+1);
            check(terminal.units==diamond-1&&terminal.status==1,"Maximal upstream diamond / unknown frontier");
            check(reads.get()<=5*diamond,"Cold dependency work exceeded 5 rank reads per diamond anchor");
            int coldReads=reads.get();
            check(graph.cell(radius+1,0).units==-1&&graph.cell(radius+1,0).status==-1,"Unknown rain silently became zero");
            System.out.println("RUNOFF cold radius "+radius+": "+(diamond-1)+" sources, "+coldReads+" rank reads (bound "+(5*diamond)+")");
        }
        var water=new FieldRegistry.Builder().add(Fields.DRAINAGE_RANK,(x,z)->0).add(Fields.FLOW_DIRECTION,(x,z)->0).build();
        var graph=new CoarseRunoff(Params.defaults(),water);
        check(graph.cell(0,0).units==0&&graph.cell(0,0).status==0,"Water creates rain");
        long limit=Lattice.MAX_COORDINATE/4096;
        check(graph.cell(limit,limit).units==0&&graph.cell(limit,limit).status==1,"Numeric edge concealed");
        var bad=new FieldRegistry.Builder().add(Fields.DRAINAGE_RANK,(x,z)->1).add(Fields.FLOW_DIRECTION,(x,z)->2).build();
        try { new CoarseRunoff(Params.defaults(),bad).cell(0,0);throw new AssertionError("Non-descending graph accepted"); }
        catch(IllegalArgumentException expected) { }
    }
    private static void forwardReference(Generator g) {
        int r=g.params.integer("seaSearchRadius"),s=g.params.integer("coarseSpacing"),inner=40,n=inner+2*(r+1),halo=r+1;
        long ox=-32-halo,oz=-30-halo;
        int[] ranks=new int[n*n],dirs=new int[n*n],expected=new int[n*n],open=new int[n*n];
        for(int z=0;z<n;z++)for(int x=0;x<n;x++) {
            int k=z*n+x;ranks[k]=g.fields.get(Fields.DRAINAGE_RANK,(ox+x)*s,(oz+z)*s);dirs[k]=g.fields.get(Fields.FLOW_DIRECTION,(ox+x)*s,(oz+z)*s);
        }
        // Forward-walk every source through immutable directions. No reverse recursion/memo.
        for(int z=1;z<n-1;z++)for(int x=1;x<n-1;x++) {
            int k=z*n+x;if(ranks[k]<0)continue;
            boolean frontier=false;
            for(int d=1;d<=4;d++)if(ranks[(z+DZ[d])*n+x+DX[d]]<0)frontier=true;
            int cx=x,cz=z,steps=0;
            while(cx>=0&&cx<n&&cz>=0&&cz<n) {
                int at=cz*n+cx;
                if(ranks[k]>0)expected[at]++;
                if(frontier)open[at]=1;
                int d=dirs[at];if(d==0)break;
                check(d>0&&++steps<=r,"World path failed terminal reachability");
                cx+=DX[d];cz+=DZ[d];
            }
        }
        long rain=0,inflow=0,outflow=0,discharge=0,unknown=0;int wet=0,partial=0;
        for(int z=halo;z<halo+inner;z++)for(int x=halo;x<halo+inner;x++) {
            int k=z*n+x;long wx=(ox+x)*s,wz=(oz+z)*s;
            int flow=g.fields.get(Fields.COARSE_RUNOFF,wx,wz),status=g.fields.get(Fields.RUNOFF_STATUS,wx,wz);
            if(ranks[k]<0){check(flow==-1&&status==-1,"Unknown reference mismatch");rain++;unknown++;continue;}
            check(flow==expected[k],"Forward/reference runoff mismatch "+g.model+" seed "+g.seed+" at "+wx+","+wz);
            check(status==open[k],"Forward/reference frontier mismatch");
            if(flow>0)wet++;if(status==1)partial++;
            int d=dirs[k];
            if(d==0)discharge+=flow;else {
                rain++;
                if(x+DX[d]<halo||x+DX[d]>=halo+inner||z+DZ[d]<halo||z+DZ[d]>=halo+inner)outflow+=flow;
            }
            for(int side=1;side<=4;side++) {
                int nx=x+DX[side],nz=z+DZ[side],opposite=side<=2?side+2:side-2;
                if(nx>=halo&&nx<halo+inner&&nz>=halo&&nz<halo+inner)continue;
                if(dirs[nz*n+nx]==opposite)inflow+=g.fields.get(Fields.COARSE_RUNOFF,(ox+nx)*s,(oz+nz)*s);
            }
        }
        check(rain+inflow==outflow+discharge+unknown,"World-window ownership ledger does not balance");
        check(wet>0,"Reference never exercised runoff");
        System.out.println("RUNOFF "+g.model.id+" seed "+g.seed+": rain "+rain+" + in "+inflow+" = out "+outflow+" + terminals "+discharge+" + unresolved "+unknown+"; open "+partial);
    }
    private static void geometry() {
        var g=new Generator(42,Params.defaults(),Generator.Model.CONTINENTAL);var ports=new BoundaryPorts(42,g.params);int s=g.params.integer("coarseSpacing"),crossings=0,confluences=0;
        for(int j=-40;j<32;j++)for(int i=-40;i<32;i++) {
            int d=g.fields.get(Fields.FLOW_DIRECTION,(long)i*s,(long)j*s);if(d<=0)continue;
            var p=ports.port(ports.face(0,i,j,d));int units=g.fields.get(Fields.COARSE_RUNOFF,(long)i*s,(long)j*s);
            check(g.fields.get(Fields.CHANNEL_FLOW,p.x,p.z)==units,"Port transferred destination total instead of source flow");
            // One block either side still belongs to the same transfer, including odd ports.
            check(g.fields.get(Fields.CHANNEL_FLOW,p.x+DX[d],p.z+DZ[d])==units,"Downstream half of crossing has wrong flow");
            check(g.fields.get(Fields.CHANNEL_FLOW,p.x-DX[d],p.z-DZ[d])==units,"Upstream half of crossing has wrong flow");
            int total=g.fields.get(Fields.COARSE_RUNOFF,(long)(i+DX[d])*s,(long)(j+DZ[d])*s);
            if(total>units+1)confluences++;crossings++;
        }
        check(crossings>100&&confluences>10,"No branching river geometry tested");
    }
    private static void orderAndSupport() throws Exception {
        Params p=new Params(Map.of("coarseSpacing",1025.0,"seaSearchRadius",32.0,"continentScale",16385.0));
        Generator reference=new Generator(Long.MAX_VALUE,p,Generator.Model.CONTINENTAL);
        List<long[]> points=new ArrayList<>();List<int[]> expected=new ArrayList<>();
        for(int k=0;k<5000;k++) {
            long x=(k%100-50)*1025L,z=(k/100-25)*1025L;points.add(new long[]{x,z});
            int[] v=new int[FIELDS.size()];for(int f=0;f<v.length;f++)v[f]=reference.fields.get(FIELDS.get(f),x,z);expected.add(v);
        }
        List<Integer> order=new ArrayList<>();for(int k=0;k<points.size();k++)order.add(k);Collections.shuffle(order,new Random(814));
        Generator fresh=new Generator(Long.MAX_VALUE,p,Generator.Model.CONTINENTAL);
        try(var pool=Executors.newFixedThreadPool(4)) {
            List<Callable<Boolean>> jobs=new ArrayList<>();for(int k:order)jobs.add(()->{
                long[] point=points.get(k);for(int f=0;f<FIELDS.size();f++)if(fresh.fields.get(FIELDS.get(f),point[0],point[1])!=expected.get(k)[f])return false;return true;
            });
            for(var result:pool.invokeAll(jobs))check(result.get(),"Shuffled concurrent/evicted runoff mismatch");
        }
        for(long x:new long[]{-Lattice.MAX_COORDINATE,Lattice.MAX_COORDINATE})for(long z:new long[]{x,0})for(var f:FIELDS)
            check(reference.fields.get(f,x,z).equals(new Generator(Long.MAX_VALUE,p,Generator.Model.CONTINENTAL).fields.get(f,x,z)),"Numeric support query differs cold");
        // Pointwise overlap and common-point zoom are also covered by all-field world gates.
    }
    private static void radiusExtension() {
        var small=new Generator(-1,new Params(Map.of("seaSearchRadius",4.0)),Generator.Model.CONTINENTAL);
        var large=new Generator(-1,Params.defaults(),Generator.Model.CONTINENTAL);int changed=0,closed=0;
        for(int j=-40;j<40;j++)for(int i=-40;i<40;i++) {
            long x=i*4096L,z=j*4096L;int rank=small.fields.get(Fields.DRAINAGE_RANK,x,z);if(rank<0)continue;
            check(small.fields.get(Fields.FLOW_DIRECTION,x,z).equals(large.fields.get(Fields.FLOW_DIRECTION,x,z)),"Extending search changed existing resolved route");
            int a=small.fields.get(Fields.COARSE_RUNOFF,x,z),b=large.fields.get(Fields.COARSE_RUNOFF,x,z);
            check(b>=a,"Adding resolved sources lost committed runoff");if(b>a)changed++;
            if(small.fields.get(Fields.RUNOFF_STATUS,x,z)==0){check(a==b,"Closed catchment missed upstream dependency");closed++;}
        }
        check(changed>20&&closed>20,"Radius extension fixture lacks partial/closed catchments");
    }
    public static void diagnostics() throws Exception {
        Files.createDirectories(Path.of("build/gallery"));var g=new Generator(-1,Params.defaults(),Generator.Model.CONTINENTAL);
        var sheet=new BufferedImage(1056,1140,BufferedImage.TYPE_INT_RGB);var pen=sheet.createGraphics();
        pen.setColor(new Color(0x10181b));pen.fillRect(0,0,1056,1140);pen.setColor(new Color(0xe3e9e5));pen.setFont(new Font("SansSerif",Font.PLAIN,15));
        var layers=List.of(new Renderer.Layer[]{new Renderer.Layer(Fields.BASE_ELEVATION,1),new Renderer.Layer(Fields.CHANNEL_FLOW,1)},
            new Renderer.Layer[]{new Renderer.Layer(Fields.COARSE_RUNOFF,1)},new Renderer.Layer[]{new Renderer.Layer(Fields.RUNOFF_STATUS,1)},
            new Renderer.Layer[]{new Renderer.Layer(Fields.CHANNEL_FLOW,1)});
        String[] names={"Terrain + flow-weighted guides","Resolved upstream rain / log display","Teal: closed / amber: open / pink: unknown","Shared transfers / still coarse, uncarved geometry"};
        for(int k=0;k<4;k++) {
            int x=8+k%2*528,z=32+k/2*554;pen.drawString(names[k],x,z-10);
            pen.drawImage(Renderer.render(g,layers.get(k),-131072,-122880,256,512,512).image(),x,z,null);
        }
        pen.drawString("Same world coordinates in every panel. Exact resolved graph totals; unknown interior rainfall is not invented or discarded.",8,1131);
        pen.dispose();ImageIO.write(sheet,"png",Path.of("build/gallery/continental-runoff.png").toFile());
    }
    public static void main(String[] args) throws Exception { run(); }
}
