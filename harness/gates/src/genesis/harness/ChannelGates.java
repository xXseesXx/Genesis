package genesis.harness;

import genesis.core.Generator;
import genesis.core.Params;
import genesis.core.fields.FieldRegistry;
import genesis.core.fields.Fields;
import genesis.core.hash.Lattice;
import genesis.core.hydro.BoundaryPorts;
import genesis.core.hydro.CoarseChannels;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;

/** World-coordinate ownership and seam tests; diagnostics are code-rendered, not AI artwork. */
final class ChannelGates {
    private ChannelGates() {}
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void run() throws Exception {
        portOwnership(); distanceReference(); overlapAndCaches(); diagnostics();
        System.out.println("PASS CHANNEL: canonical face/child ownership, integer distance reference, shuffled/cold/concurrent overlap, exact independently stitched PNGs");
    }
    private static void portOwnership() {
        for (long seed : new long[]{0,42,-1,Long.MIN_VALUE,Long.MAX_VALUE}) for (int base : new int[]{1024,1025,1026,4096,4097,4098,16383,16384}) {
            var ports = new BoundaryPorts(seed, new Params(Map.of("coarseSpacing", (double) base)));
            for (int level : new int[]{0,1,5,20}) for (long i : new long[]{-5,-1,0,3}) for (long j : new long[]{-3,-1,0,4}) {
                var east = ports.face(level,i,j,2); var west = ports.face(level,i+1,j,4);
                var south = ports.face(level,i,j,3); var north = ports.face(level,i,j+1,1);
                check(east.equals(west) && south.equals(north), "Neighbour cells disagree on exact face identity");
                for (var face : List.of(east,south)) {
                    var p = ports.port(face); var repeat = new BoundaryPorts(seed,new Params(Map.of("coarseSpacing",(double)base))).port(face);
                    check(p.x == repeat.x && p.z == repeat.z, "Cold port mismatch");
                    long s = ports.spacing(level), along = face.axis == 0 ? p.z-face.j*s : p.x-face.i*s;
                    check(along > s/4 && along < 3*s/4 && along%base!=0, "Port leaves central half or lies on a child corner");
                    for(int finer=0;finer<level;finer++) {
                        var child=ports.childFace(p,finer); long cs=ports.spacing(finer);
                        check(p.x >= child.i*cs && p.z >= child.j*cs && p.x < (child.i+1)*cs && p.z < (child.j+1)*cs, "Inherited port outside unique child face");
                        check(child.axis==face.axis && (child.axis==0?p.x==child.i*cs:p.z==child.j*cs), "Child face moves crossing");
                    }
                }
            }
            long limit=Lattice.MAX_COORDINATE/base;
            ports.port(ports.face(0,limit-1,0,2)); ports.port(ports.face(0,-limit,0,4));
        }
        var ports = new BoundaryPorts(42,Params.defaults());
        rejects(()->ports.spacing(-1)); rejects(()->ports.spacing(21)); rejects(()->ports.face(0,Long.MAX_VALUE,0,2));
        rejects(()->ports.port(new BoundaryPorts.Face(0,0,Long.MAX_VALUE,0)));
        rejects(()->ports.childFace(ports.port(ports.face(0,0,0,1)),0));
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch(IllegalArgumentException|ArithmeticException expected) { return; }
        throw new AssertionError("Invalid port input accepted");
    }
    private static void distanceReference() {
        Params params = new Params(Map.of("coarseSpacing",16384.0)); int s=params.integer("coarseSpacing");
        FieldRegistry fixture=new FieldRegistry.Builder().add(Fields.FLOW_DIRECTION,(x,z)->2).build();
        var channels=new CoarseChannels(42,params,fixture); Random random=new Random(4321);
        for(int trial=0;trial<400;trial++) {
            long x=random.nextInt(6*s)-3L*s,z=random.nextInt(6*s)-3L*s,i=Math.floorDiv(x,s),j=Math.floorDiv(z,s);
            long squared=(long)channels.distanceCap*channels.distanceCap,portSquared=squared;
            // Larger 5x5 support and BigInteger cross products are independent of production bounds.
            for(long cj=j-2;cj<=j+2;cj++)for(long ci=i-2;ci<=i+2;ci++) {
                var c=channels.cell(ci,cj);
                for(var p:c.crossings()) {
                    long dx=x-p.x,dz=z-p.z; portSquared=Math.min(portSquared,dx*dx+dz*dz);
                    long ax=x-c.hubX,az=z-c.hubZ,bx=p.x-c.hubX,bz=p.z-c.hubZ;
                    long dot=ax*bx+az*bz,len=bx*bx+bz*bz;
                    long distance=dot<=0?ax*ax+az*az:dot>=len?dx*dx+dz*dz:
                        BigInteger.valueOf(ax).multiply(BigInteger.valueOf(bz)).subtract(BigInteger.valueOf(az).multiply(BigInteger.valueOf(bx))).pow(2).divide(BigInteger.valueOf(len)).longValueExact();
                    squared=Math.min(squared,distance);
                }
            }
            check(channels.channelDistance(x,z)==BigInteger.valueOf(squared).sqrt().intValueExact(),"Capped channel distance mismatch");
            check(channels.portDistance(x,z)==BigInteger.valueOf(portSquared).sqrt().intValueExact(),"Capped port distance mismatch");
        }
        for(long i=-4;i<=4;i++)for(long j=-4;j<=4;j++) {
            var a=channels.cell(i,j);var b=channels.cell(i+1,j);
            var expectedFace=new BoundaryPorts.Face(0,0,i+1,j);
            var common=Arrays.stream(a.crossings()).filter(p->p.owner.equals(expectedFace)).findFirst();
            check(common.isPresent(),"Missing east-going port");var p=common.get();
            check(Arrays.stream(b.crossings()).anyMatch(q->q.owner.equals(p.owner)&&q.x==p.x&&q.z==p.z),"Crossing mismatch across edge");
            check(channels.channelDistance(p.x,p.z)==0&&channels.portDistance(p.x,p.z)==0,"Crossing is not on geometry");
            check(channels.channelDistance(p.x-1,p.z)<=1&&channels.channelDistance(p.x+1,p.z)<=1,"Guide breaks across shared edge");
            var copy=a.crossings();copy[0]=null;check(a.crossings()[0]!=null,"Cell exposes mutable crossing array");
        }
        var absent=new CoarseChannels(42,params,new FieldRegistry.Builder().add(Fields.FLOW_DIRECTION,(x,z)->-1).build());
        check(absent.channelDistance(0,0)==absent.distanceCap&&absent.portDistance(0,0)==absent.distanceCap,"Invented route in unresolved fixture");
    }
    private static void overlapAndCaches() throws Exception {
        Generator g=new Generator(42,Params.defaults());var channels=new CoarseChannels(42,g.params,g.fields);
        for(var field:List.of(Fields.CHANNEL_DISTANCE,Fields.PORT_DISTANCE)) {
            Object[] whole=g.fields.values(field,-10000,-9000,137,97,83);
            Object[] zoomed=new Generator(42,Params.defaults()).fields.values(field,-10000,-9000,274,49,42);
            for(int z=0;z<42;z++)for(int x=0;x<49;x++)check(zoomed[z*49+x].equals(whole[z*2*97+x*2]),"Zoom changes world-coordinate geometry");
            for(int z0:new int[]{0,11,41})for(int x0:new int[]{0,17,57}) {
                Object[] crop=new Generator(42,Params.defaults()).fields.values(field,-10000+x0*137L,-9000+z0*137L,137,31,29);
                for(int z=0;z<29;z++)for(int x=0;x<31;x++)check(crop[z*31+x].equals(whole[(z0+z)*97+x0+x]),"Arbitrary crop changes a world field");
            }
        }
        int[] expected=new int[128];List<Integer> order=new ArrayList<>();
        for(int k=0;k<128;k++){expected[k]=channels.channelDistance(k*311L-17000,k*197L-13000);order.add(k);}
        Collections.shuffle(order,new Random(5));
        for(int k:order)check(expected[k]==channels.channelDistance(k*311L-17000,k*197L-13000),"Query-order dependency");
        for(int i=0;i<700;i++)channels.cell(i,-31);
        for(int k:order)check(expected[k]==channels.channelDistance(k*311L-17000,k*197L-13000),"Eviction changes channel geometry");
        try(var pool=Executors.newFixedThreadPool(4)) {
            List<Callable<Boolean>> jobs=new ArrayList<>();
            for(int k:order)jobs.add(()->expected[k]==channels.channelDistance(k*311L-17000,k*197L-13000));
            for(var job:pool.invokeAll(jobs))check(job.get(),"Concurrent geometry mismatch");
        }
        for(long x:new long[]{-Lattice.MAX_COORDINATE,Lattice.MAX_COORDINATE}) {
            check(channels.channelDistance(x,0)>=0,"Coordinate-limit query");
            check(channels.portDistance(x,x)>=0,"Coordinate-limit port query");
        }
    }
    private static void diagnostics() throws Exception {
        Files.createDirectories(Path.of("build/gallery"));
        var layers=new Renderer.Layer[]{new Renderer.Layer(Fields.SEA_MASK,.4),new Renderer.Layer(Fields.CHANNEL_DISTANCE,1),new Renderer.Layer(Fields.PORT_DISTANCE,1)};
        long origin=-65536,step=256;int size=512;
        var whole=Renderer.render(new Generator(42,Params.defaults()),layers,origin,origin,step,size,size).image();
        var stitched=new BufferedImage(size,size,BufferedImage.TYPE_INT_RGB);
        var graphics=stitched.createGraphics();
        for(int q:new int[]{3,0,2,1}) {
            int x=q%2*256,z=q/2*256;
            var tile=Renderer.render(new Generator(42,Params.defaults()),layers,origin+x*step,origin+z*step,step,256,256).image();
            graphics.drawImage(tile,x,z,null);
        }
        graphics.dispose();
        int differences=0;
        for(int z=0;z<size;z++)for(int x=0;x<size;x++)if(whole.getRGB(x,z)!=stitched.getRGB(x,z))differences++;
        check(differences==0,"Canonical render differs across cold independent tile cuts");
        var sheet=new BufferedImage(1056,584,BufferedImage.TYPE_INT_RGB);var pen=sheet.createGraphics();
        pen.setColor(new Color(0x10181b));pen.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
        pen.setFont(new Font("SansSerif",Font.PLAIN,15));pen.setColor(new Color(0xe3e9e5));
        pen.drawString("ONE WORLD-COORDINATE QUERY",16,25);pen.drawString("FOUR COLD TILES / SHUFFLED ORDER",544,25);
        pen.drawImage(whole,8,40,null);pen.drawImage(stitched,536,40,null);
        pen.setColor(new Color(0xffffff));pen.setStroke(new BasicStroke(1,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,1,new float[]{5,5},0));
        pen.drawLine(792,40,792,552);pen.drawLine(536,296,1048,296);
        pen.drawString("Exact pixel differences: 0. Cyan = coarse guides, gold = shared ports. Dashed white = tile cuts only.",16,575);pen.dispose();
        ImageIO.write(sheet,"png",Path.of("build/gallery/canonical-seam-audit.png").toFile());
        var relief=Renderer.render(new Generator(42,Params.defaults()),new Renderer.Layer[]{new Renderer.Layer(Fields.BASE_ELEVATION,1),new Renderer.Layer(Fields.CHANNEL_DISTANCE,1),new Renderer.Layer(Fields.PORT_DISTANCE,1)},origin,origin,step,size,size).image();
        ImageIO.write(relief,"png",Path.of("build/gallery/canonical-guides-elevation.png").toFile());
    }
}
