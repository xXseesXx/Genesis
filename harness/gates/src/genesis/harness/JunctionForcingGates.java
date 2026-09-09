package genesis.harness;

import genesis.core.Params;
import genesis.core.hash.Lattice;
import genesis.core.tectonics.PlateTopology;
import genesis.oracle.BoundaryForcing;
import genesis.oracle.ActiveHydrology;
import genesis.oracle.BoundaryForcing.Plate;
import genesis.oracle.JunctionForcing;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;

/** Bounded-support/reference, composition invariants and code-generated junction diagnostics. */
final class JunctionForcingGates {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void rejects(Runnable job){try{job.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Invalid junction input accepted");}
    private static Plate plate(long id,long x,long z,int vx,int vz,int crust){return new Plate(id,x,z,vx,vz,crust,400);}
    private static List<Plate> fixture(boolean mixed) {
        return List.of(plate(1,-8192,0,32,16,1),plate(2,8192,0,-32,-8,1),plate(3,0,16384,mixed?32:0,mixed?32:-32,mixed?0:1));
    }
    private static List<Plate> neighborhood(PlateTopology topology,long x,long z,int radius) {
        List<Plate> sites=new ArrayList<>();long i=Lattice.cell(x,topology.spacing),j=Lattice.cell(z,topology.spacing);
        for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++)sites.add(Plate.from(topology.site(i+dx,j+dz)));
        return sites;
    }
    public static void main(String[] args)throws Exception{run();}
    static void run()throws Exception {
        var pair=List.of(plate(1,-8192,0,4,0,1),plate(2,8192,0,-4,0,1));
        var center=JunctionForcing.compose(pair,0,0,16384,35);
        check(center.anomaly()==64&&center.positive()==64&&center.negative()==0&&center.activePairs()==1,"Two-plate center gain");
        check(JunctionForcing.compose(pair,-8192,0,16384,35).anomaly()==0,"Forcing not compact in plate interior");
        var collision=BoundaryForcing.describe(pair.get(0),pair.get(1),35);
        check(JunctionForcing.profile(collision,0)==64&&JunctionForcing.profile(collision,1)==0,"Continuous profile endpoints");
        check(Math.abs(JunctionForcing.profile(collision,1-1e-7))<1e-10,"Profile has support jump");
        var doubleSpeed=pair.stream().map(p->new Plate(p.id(),p.x(),p.z(),p.vx()*2,p.vz()*2,p.crust(),p.age())).toList();
        var quiet=pair.stream().map(p->new Plate(p.id(),p.x(),p.z(),7,-3,p.crust(),p.age())).toList();
        for(int x=-8192;x<=8192;x+=64) {
            check(JunctionForcing.compose(doubleSpeed,x,0,16384,35).anomaly()==2*JunctionForcing.compose(pair,x,0,16384,35).anomaly(),"Explicit speed scaling changed under blend");
            check(JunctionForcing.compose(quiet,x,0,16384,35).anomaly()==0,"Common motion invented new relief");
        }
        // Exact symmetric triple point: all three weights=1, so equal pair blend, not triple amplification.
        var triple=fixture(false);double expected=0;
        for(int a=0;a<3;a++)for(int b=a+1;b<3;b++)expected+=JunctionForcing.profile(BoundaryForcing.describe(triple.get(a),triple.get(b),35),0)/3;
        var junction=JunctionForcing.compose(triple,0,6144,16384,35);
        check(junction.supportedPlates()==3&&junction.activePairs()==3&&Math.abs(junction.anomaly()-expected)<1e-10,"Triple-point shared blend");
        var four=List.of(plate(1,-8192,-8192,4,4,1),plate(2,8192,-8192,-4,4,1),
            plate(3,-8192,8192,4,-4,1),plate(4,8192,8192,-4,-4,1));
        var fourWay=JunctionForcing.compose(four,0,0,16384,35);
        check(fourWay.supportedPlates()==4&&fourWay.activePairs()==6,"Soft four-way policy changed: diagonal pairs intentionally included");
        check(fourWay.anomaly()>0&&fourWay.anomaly()<=JunctionForcing.profile(BoundaryForcing.describe(four.get(0),four.get(3),35),0),"Junction amplified above largest pair contribution");
        for(boolean mixed:new boolean[]{false,true}) {
            var plates=fixture(mixed);var shuffled=new ArrayList<>(plates);Collections.reverse(shuffled);
            var moved=plates.stream().map(p->new Plate(p.id(),p.x()+1234567,p.z()-987654,p.vx()+7,p.vz()-11,p.crust(),p.age())).toList();
            for(int z=4096;z<=8192;z+=127)for(int x=-4096;x<=4096;x+=131) {
                var value=JunctionForcing.compose(plates,x,z,16384,35);
                check(value.equals(JunctionForcing.compose(shuffled,x,z,16384,35)),"Fixture input order changed output");
                check(value.equals(JunctionForcing.compose(moved,x+1234567,z-987654,16384,35)),"Common position/motion changed composition");
                check(value.positive()>=0&&value.negative()<=0&&value.anomaly()==value.positive()+value.negative(),"Signed diagnostic ledger");
                double reference=reference(plates,x,z,16384,35);
                check(Math.abs(value.anomaly()-reference)<1e-8,"Independent reduction differs");
            }
            for(int z=5000;z<7300;z++) {
                double left=JunctionForcing.compose(plates,-1,z,16384,35).anomaly();
                double right=JunctionForcing.compose(plates,1,z,16384,35).anomaly();
                check(Math.abs(left-right)<2,"Controlled owner-switch jump");
            }
        }
        Random random=new Random(671991);int cases=0;double maxStep=0;
        long started=System.nanoTime();
        for(long seed:new long[]{42,-1,Long.MIN_VALUE})for(int spacing:new int[]{8192,16384,262144})for(int jitter:new int[]{0,50}) {
            var params=new Params(Map.of("plateSpacing",(double)spacing,"plateJitter",(double)jitter,"plateSpeed",128.0));
            var topology=new PlateTopology(seed,params);var field=new JunctionForcing(seed,params);
            for(int t=0;t<40;t++) {
                long x=(random.nextInt(2001)-1000L)*spacing+random.nextInt(spacing),z=(random.nextInt(2001)-1000L)*spacing+random.nextInt(spacing);
                if(t<4){x=(t%2==0?1:-1)*(Lattice.MAX_COORDINATE-1);z=(t<2?1:-1)*(Lattice.MAX_COORDINATE-1);}
                if(t>=4&&t<12)x=(random.nextInt(100)-50L)*spacing+(t%3-1); // Changing query-neighborhood seams.
                var actual=field.sample(x,z);var large=neighborhood(topology,x,z,5);
                check(actual.equals(JunctionForcing.compose(large,x,z,spacing,35)),"25-site window differs from 121-site reference");
                check(Math.abs(actual.anomaly()-reference(large,x,z,spacing,35))<1e-8,"World independent pair reduction differs");
                Collections.shuffle(large,random);
                check(actual.equals(JunctionForcing.compose(large,x,z,spacing,35)),"World contributor permutation changed output");
                check(actual.equals(new JunctionForcing(seed,params).sample(x,z)),"Cold point mismatch");
                double delta=Math.max(Math.abs(actual.anomaly()-field.sample(x+1,z).anomaly()),Math.abs(actual.anomaly()-field.sample(x,z+1).anomaly()));
                maxStep=Math.max(maxStep,delta);
                check(delta<128*1024.0/spacing,"Sampled one-block step exceeds conservative regression bound");
                cases++;
            }
        }
        var field=new JunctionForcing(42,Params.defaults());
        List<Callable<JunctionForcing.Sample>> jobs=new ArrayList<>();
        for(int p=0;p<80;p++){final long x=p*8191L-99999;jobs.add(()->field.sample(x,-x));}
        try(var pool=Executors.newFixedThreadPool(4)) {
            var results=pool.invokeAll(jobs);
            for(int p=0;p<jobs.size();p++)check(results.get(p).get().equals(jobs.get(p).call()),"Shared-instance concurrency mismatch");
        }
        // Identical absolute coordinates in differently cropped/strided request simulations.
        for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
            long wx=-65536+x*2048L,wz=32768+z*2048L;
            check(field.sample(wx,wz).equals(new JunctionForcing(42,Params.defaults()).sample(-69632+(x+2)*2048L,28672+(z+2)*2048L)),"Crop/common-point sampling mismatch");
        }
        rejects(()->JunctionForcing.compose(List.of(),0,0,16384,35));
        rejects(()->JunctionForcing.compose(List.of(pair.get(0),pair.get(0)),0,0,16384,35));
        rejects(()->JunctionForcing.compose(pair,0,0,0,35));
        rejects(()->JunctionForcing.profile(collision,Double.NaN));
        rejects(()->field.sample(Long.MAX_VALUE,0));
        int loweredCrest=divideCounterexample(pair);
        String fingerprint=fingerprint(field);
        check(fingerprint.equals("98592caffd243e40cc176d9eb05be51b47f77b9534bef2bd9d85fcb648e573d2"),"Versioned junction output fingerprint changed");
        double checkMs=(System.nanoTime()-started)/1e6;
        render();Files.writeString(Path.of("build/junction-forcing.json"),"""
            {
              "experiment":"junction-forcing-v1",
              "status":"bounded soft multi-plate height anomaly; not absolute elevation or drainage-safe terrain",
              "largerSupportCases":%d,
              "siteCandidates":25,
              "referenceSiteCandidates":121,
              "maxPairCandidates":300,
              "sampledMaxOneBlockHeightDelta":%s,
              "supportAndSamplingCheckMs":%s,
              "sampleFingerprint":"%s",
              "blendDivideCounterexample":{"pairCrest":84,"blendedCrest":%d,"originalSpill":80,"divideInvalidated":true},
              "continuousRecipe":"C0 in ideal real arithmetic; floating output, not a slope-continuity theorem",
              "junctionPolicy":"convex soft pair blend; includes nearby pairs without certified shared Voronoi faces",
              "preservedDrainageDivides":false,
              "productionFieldsChanged":false
            }
            """.formatted(cases,Double.toString(maxStep),Double.toString(checkMs),fingerprint,loweredCrest));
        System.out.println("PASS JUNCTION FORCING: "+cases+" larger-support/independent checks; shared triple points, side/order/motion/crop/concurrency; sampled maximum one-block delta="+maxStep);
    }
    private static String fingerprint(JunctionForcing field)throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");var bytes=ByteBuffer.allocate(32);
        for(int z=0;z<32;z++)for(int x=0;x<32;x++) {
            var s=field.sample(-32768+x*2048L,-32768+z*2048L);bytes.clear();
            bytes.putDouble(s.anomaly()).putDouble(s.positive()).putDouble(s.negative()).putInt(s.supportedPlates()).putInt(s.activePairs());
            digest.update(bytes.array());
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    /** Scalar forcing imposed on a controlled saddle, not a complete sampled tectonic basin. */
    private static int divideCounterexample(List<Plate> pair) {
        var three=List.of(pair.get(0),pair.get(1),plate(3,0,16384,0,0,1));
        int blended=20+(int)Math.floor(JunctionForcing.compose(three,0,6144,16384,35).anomaly());
        check(blended<80,"Fixture should weaken a previously separating crest");
        int w=9,h=5,n=w*h;int[] bed=new int[n],east=ActiveHydrologyGates.emptyCrests(n),south=ActiveHydrologyGates.emptyCrests(n);
        boolean[] active=new boolean[n],term=new boolean[n];long[] rain=new long[n];Arrays.fill(active,true);
        for(int p=0;p<n;p++){int x=p%w;term[p]=x==0||x==8;bed[p]=term[p]?0:x==1?80:x<4?20:10;rain[p]=x==2||x==3?1:0;if(x==3)east[p]=84;}
        check(ActiveHydrologyGates.verify(w,h,bed,active,term,rain,east,south).filled(21)==80,"Initial collision pass did not isolate basin");
        east[21]=blended;var open=ActiveHydrologyGates.verify(w,h,bed,active,term,rain,east,south);
        check(open.filled(21)==blended&&open.downstream(21)==22&&open.flux(21)>0,"Blended pass leak was hidden");
        boolean[] left=active.clone();for(int p=0;p<n;p++)if(p%w>=4&&!term[p])left[p]=false;
        check(ActiveHydrology.solve(w,h,bed,left,term,rain,east,south).filled(21)!=open.filled(21),"Stale owner divide accepted after composition");
        return blended;
    }
    /** Separately enumerate ordered pairs, normalize after summing signed contributions in reverse order.
     * Reuses the versioned profile, but not production weight/reduction/projection code.
     */
    private static double reference(List<Plate> sites,long x,long z,int spacing,int ratio) {
        double min=Double.POSITIVE_INFINITY;
        for(var p:sites)min=Math.min(min,(p.x()-x)*(double)(p.x()-x)+(p.z()-z)*(double)(p.z()-z));
        double sum=0,weights=0;
        for(int a=sites.size()-1;a>=0;a--)for(int b=sites.size()-1;b>=0;b--)if(a!=b) {
            var pa=sites.get(a);var pb=sites.get(b);
            double ta=2*((pa.x()-x)*(double)(pa.x()-x)+(pa.z()-z)*(double)(pa.z()-z)-min)/(spacing*(double)spacing);
            double tb=2*((pb.x()-x)*(double)(pb.x()-x)+(pb.z()-z)*(double)(pb.z()-z)-min)/(spacing*(double)spacing);
            if(ta>=1||tb>=1)continue;
            double w=(1-3*ta*ta+2*ta*ta*ta)*(1-3*tb*tb+2*tb*tb*tb)/2;
            var edge=BoundaryForcing.describe(pa,pb,ratio);var first=edge.first();var second=edge.second();
            double dx=second.x()-first.x(),dz=second.z()-first.z();
            double u=((x-first.x()-dx/2)*dx+(z-first.z()-dz/2)*dz)/StrictMath.sqrt(dx*dx+dz*dz)/(spacing/4.0);
            sum+=w*JunctionForcing.profile(edge,u);weights+=w;
        }
        return sum/Math.max(1,weights);
    }
    private static Color color(double height) {
        int v=(int)Math.min(190,Math.abs(height)/5);
        return height>=0?new Color(235,235-v,229-v):new Color(235-v,235-v/2,235);
    }
    private static void render()throws Exception {
        int n=257;BufferedImage image=new BufferedImage(1200,875,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        g.setColor(new Color(246,248,250));g.fillRect(0,0,1200,875);g.setColor(new Color(25,34,46));g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,21));
        g.drawString("Toward tectonic terrain: shared junction forcing",20,30);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));
        g.drawString("Seed 42 | same plate vectors and profile families | warm = positive anomaly, blue = negative; not land/ocean",20,55);
        var params=Params.defaults();var old=new BoundaryForcing(42,params);var composed=new JunctionForcing(42,params);
        BufferedImage[] maps=new BufferedImage[3];for(int p=0;p<3;p++)maps[p]=new BufferedImage(n,n,BufferedImage.TYPE_INT_RGB);
        for(int z=0;z<n;z++)for(int x=0;x<n;x++) {
            long wx=-32768+x*256L,wz=-32768+z*256L;var sample=composed.sample(wx,wz);
            maps[0].setRGB(x,z,color(old.sample(wx,wz).anomaly()).getRGB());maps[1].setRGB(x,z,color(sample.anomaly()).getRGB());
            int count=sample.supportedPlates();maps[2].setRGB(x,z,(count<=1?new Color(231,235,239):count==2?new Color(134,183,190):new Color(177,107,177)).getRGB());
        }
        String[] labels={"Old closest-edge selection","New soft multi-edge composition","Support: gray 1 | teal 2 | purple 3+"};
        for(int p=0;p<3;p++){int ox=20+p*395;g.drawString(labels[p],ox,85);g.drawImage(maps[p],ox,98,350,350,null);}
        g.drawString("No winner switch in the composed anomaly. Soft influence is not a certified face map or a preserved ridge contract.",20,478);
        for(int k=0;k<2;k++) {
            int ox=20+k*590,oy=520;var plates=fixture(k==1);g.setColor(new Color(25,34,46));
            g.drawString(k==0?"Controlled collision triple junction":"Controlled mixed-crust / mixed-motion junction",ox,oy);
            g.setColor(new Color(212,217,224));g.drawLine(ox,oy+170,ox+550,oy+170);g.drawLine(ox+275,oy+15,ox+275,oy+260);
            // A cut through the exact three-way tie: x=-8192..8192 at z=6144.
            for(int layer=0;layer<3;layer++) {
                g.setColor(layer==0?new Color(24,47,63):layer==1?new Color(185,103,64):new Color(58,123,178));
                int lastX=ox,lastY=oy+170;
                for(int p=0;p<=550;p++) {
                    var s=JunctionForcing.compose(plates,-8192+p*16384L/550,6144,16384,35);
                    double value=layer==0?s.anomaly():layer==1?s.positive():s.negative();int y=oy+170-(int)Math.round(value*.30);
                    if(p>0)g.drawLine(lastX,lastY,ox+p,y);lastX=ox+p;lastY=y;
                }
            }
            g.setColor(new Color(65,77,90));g.drawString("-8192                         junction                         +8192 blocks",ox,oy+282);
        }
        g.setColor(new Color(25,34,46));g.drawString("Sections: dark total | brown positive pair contributions | blue negative pair contributions. Cancellation is deliberate and visible.",20,837);
        g.drawString("Next: broad crustal elevation + fixed sea-level calibration. Hydrological divides must be solved or revalidated afterward.",20,861);
        g.dispose();Files.createDirectories(Path.of("build/gallery"));ImageIO.write(image,"png",Path.of("build/gallery/junction-forcing.png").toFile());
    }
}
