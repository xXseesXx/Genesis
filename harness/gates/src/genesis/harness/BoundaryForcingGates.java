package genesis.harness;

import genesis.core.Params;
import genesis.core.hash.Lattice;
import genesis.core.tectonics.PlateTopology;
import genesis.oracle.ActiveHydrology;
import genesis.oracle.BoundaryForcing;
import genesis.oracle.BoundaryForcing.Plate;
import genesis.oracle.BoundaryForcing.Edge;
import genesis.oracle.BoundaryForcing.Regime;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;

/** Existing plate vectors -> shared forcing -> explicit drainage compatibility counterexample. */
final class BoundaryForcingGates {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static Edge edge(int avx,int avz,int bvx,int bvz,int ac,int bc) {
        return BoundaryForcing.describe(new Plate(1,0,0,avx,avz,ac,200),new Plate(2,1024,0,bvx,bvz,bc,400),35);
    }
    private static List<Edge> scenarios(){return List.of(edge(4,0,-4,0,1,1),edge(4,0,-4,0,0,1),edge(-4,0,4,0,1,1),
        edge(-4,0,4,0,0,0),edge(0,4,0,-4,1,1),edge(4,2,4,2,1,1));}
    private static void rejects(Runnable action){try{action.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Invalid forcing input accepted");}
    public static void main(String[] args)throws Exception{run();}
    static void run()throws Exception {
        var cases=scenarios();
        check(cases.get(0).regime()==Regime.COLLISION&&BoundaryForcing.anomaly(cases.get(0),0)==64,"Continental compression profile");
        check(cases.get(1).regime()==Regime.SUBDUCTION&&cases.get(1).descendingSide()==-1
            &&BoundaryForcing.anomaly(cases.get(1),-250)<0&&BoundaryForcing.anomaly(cases.get(1),300)>0,"Trench/arc polarity");
        check(cases.get(2).regime()==Regime.CONTINENTAL_RIFT&&BoundaryForcing.anomaly(cases.get(2),0)==-48
            &&BoundaryForcing.anomaly(cases.get(2),450)>0,"Continental rift and shoulders");
        check(cases.get(3).regime()==Regime.OCEAN_SPREADING&&BoundaryForcing.anomaly(cases.get(3),0)>0
            &&BoundaryForcing.anomaly(cases.get(3),150)>BoundaryForcing.anomaly(cases.get(3),0),"Spreading ridge with axial notch");
        check(cases.get(4).regime()==Regime.TRANSFORM&&cases.get(4).shearQ()!=0,"Shear motion lost");
        check(cases.get(5).regime()==Regime.QUIET&&cases.get(5).normalQ()==0&&cases.get(5).shearQ()==0,"Equal absolute motion is not relative quiet");
        check(edge(-4,0,4,0,0,1).regime()==Regime.RIFTED_MARGIN,"Mixed extension missing");
        check(edge(4,0,-4,0,0,0).descendingSide()==1,"Older ocean plate convention");
        for(var e:cases) {
            check(e.equals(BoundaryForcing.describe(e.second(),e.first(),35)),"Edge changed with caller side");
            check(BoundaryForcing.anomaly(e,-1000)==0&&BoundaryForcing.anomaly(e,1000)==0
                &&BoundaryForcing.anomaly(e,Integer.MIN_VALUE)==0,"Profile support not compact");
            var a=e.first();var b=e.second();
            var shifted=BoundaryForcing.describe(new Plate(a.id(),a.x()+9000000,a.z()-2000000,a.vx()+17,a.vz()-9,a.crust(),a.age()),
                new Plate(b.id(),b.x()+9000000,b.z()-2000000,b.vx()+17,b.vz()-9,b.crust(),b.age()),35);
            check(e.normalQ()==shifted.normalQ()&&e.shearQ()==shifted.shearQ(),"Common motion changed projected relative velocity");
            for(int u=-1000;u<=1000;u+=7)check(BoundaryForcing.anomaly(e,u)==BoundaryForcing.anomaly(shifted,u),"Common translation/velocity changed relative forcing");
        }
        check(BoundaryForcing.anomaly(edge(8,0,-8,0,1,1),0)==128,"Doubling relative speed did not double center relief");
        var tiedOcean=BoundaryForcing.describe(new Plate(1,0,0,4,0,0,200),new Plate(2,1024,0,-4,0,0,200),35);
        check(tiedOcean.descendingSide()==-1,"Ocean age tie is not canonical");
        Random random=new Random(62744);
        for(long seed:new long[]{42,-1,Long.MIN_VALUE}) {
            var params=Params.defaults();var topology=new PlateTopology(seed,params);var forcing=new BoundaryForcing(seed,params);
            for(int trial=0;trial<100;trial++) {
                long x=(long)(random.nextInt(2001)-1000)*16384+random.nextInt(16384),z=(long)(random.nextInt(2001)-1000)*16384+random.nextInt(16384);
                if(trial==0){x=Lattice.MAX_COORDINATE-4000;z=-Lattice.MAX_COORDINATE+5000;}
                var s=topology.sample(x,z);var f=forcing.sample(x,z);var a=f.edge().first();var b=f.edge().second();
                check((a.id()==s.owner.id&&b.id()==s.neighbor.id)||(a.id()==s.neighbor.id&&b.id()==s.owner.id),"Forcing invented different plate ownership");
                BigInteger dx=BigInteger.valueOf(b.x()).subtract(BigInteger.valueOf(a.x())),dz=BigInteger.valueOf(b.z()).subtract(BigInteger.valueOf(a.z()));
                BigInteger vx=BigInteger.valueOf((long)a.vx()-b.vx()),vz=BigInteger.valueOf((long)a.vz()-b.vz());
                BigInteger length=dx.multiply(dx).add(dz.multiply(dz)).sqrt();
                check(f.edge().normalQ()==vx.multiply(dx).add(vz.multiply(dz)).multiply(BigInteger.valueOf(1024)).divide(length).longValueExact(),"Normal projection BigInteger mismatch");
                check(f.edge().shearQ()==vx.multiply(dz).subtract(vz.multiply(dx)).multiply(BigInteger.valueOf(1024)).divide(length).longValueExact(),"Shear projection BigInteger mismatch");
                int old=f.edge().regime()==Regime.COLLISION||f.edge().regime()==Regime.SUBDUCTION?1:
                    f.edge().regime()==Regime.QUIET||f.edge().regime()==Regime.TRANSFORM?0:-1;
                check(old==s.boundaryType,"Reference regimes contradict core vector classification");
                check(f.equals(new BoundaryForcing(seed,params).sample(x,z)),"Cold reference field differs");
            }
        }
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<BoundaryForcing.Sample> job=()->new BoundaryForcing(42,Params.defaults()).sample(-77777,88888);
            var results=pool.invokeAll(List.of(job,job));check(results.get(0).get().equals(results.get(1).get()),"Concurrent forcing differs");
        }
        // Params extremes stay inside the bounded descriptor arithmetic.
        for(int spacing:new int[]{8192,262144})new BoundaryForcing(Long.MAX_VALUE,new Params(Map.of("plateSpacing",(double)spacing,"plateSpeed",128.0))).sample(-999999,1234567);
        rejects(()->BoundaryForcing.describe(cases.get(0).first(),cases.get(0).first(),35));
        rejects(()->new Plate(0,0,0,Integer.MIN_VALUE,0,1,0));
        rejects(()->new BoundaryForcing(42,Params.defaults()).sample(Long.MAX_VALUE,0));
        coupling(cases.get(0),cases.get(2));render(cases);
        Files.createDirectories(Path.of("build/gallery"));Files.writeString(Path.of("build/boundary-forcing.json"),"""
            {
              "experiment":"boundary-forcing-v1",
              "source":"existing core PlateTopology sites, velocities, crust and age",
              "status":"reference cross-edge profiles; not production elevation or a continuous multi-edge field",
              "projectionReferenceCases":300,
              "scenarioCenterAnomalies":{"collision":64,"continentalRift":-48,"oceanSpreading":24,"transform":0,"quiet":0},
              "coupling":{"baseCrest":120,"compressedCrest":184,"riftedCrest":72,"oldBasinSpill":80,"riftedSpill":72,"invalidDivideDetected":true},
              "speedMeaning":"fixed model velocity units; no physical time calibration; profile amplitude proportional to relative normal motion",
              "profileWidth":"plateSpacing/4, fixed in v1; not speed dependent",
              "tripleJunctionComposition":"unresolved; closest-edge switching can be discontinuous"
            }
            """);
        System.out.println("PASS BOUNDARY FORCING: existing plate motion/crust -> canonical profiles; 300 BigInteger/core checks, speed/common-motion/side/cold/concurrent cases; rift forcing exposes invalid drainage divide");
    }
    private static void coupling(Edge collision,Edge rift) {
        int w=9,h=5,n=w*h;int[] bed=new int[n],east=ActiveHydrologyGates.emptyCrests(n),south=ActiveHydrologyGates.emptyCrests(n);
        boolean[] active=new boolean[n],term=new boolean[n];Arrays.fill(active,true);long[] rain=new long[n];
        for(int p=0;p<n;p++){int x=p%w;term[p]=x==0||x==8;bed[p]=term[p]?0:x==1?80:x<4?20:10;rain[p]=x==2||x==3?1:0;
            if(x==3)east[p]=120+BoundaryForcing.anomaly(collision,0);}
        var stable=ActiveHydrologyGates.verify(w,h,bed,active,term,rain,east,south);check(stable.filled(21)==80,"Compressed ridge lost local spill");
        east[21]=120+BoundaryForcing.anomaly(rift,0);
        var opened=ActiveHydrologyGates.verify(w,h,bed,active,term,rain,east,south);
        check(opened.filled(21)==72&&opened.downstream(21)==22&&opened.flux(21)>0,"Rifted pass did not expose boundary crossing");
        boolean[] left=active.clone();for(int p=0;p<n;p++)if(p%w>=4&&!term[p])left[p]=false;
        var isolated=ActiveHydrology.solve(w,h,bed,left,term,rain,east,south);check(isolated.filled(21)!=opened.filled(21),"Invalid divide hidden by ownership mask");
    }
    private static Color regimeColor(Regime r){return switch(r){case COLLISION->new Color(164,94,57);case SUBDUCTION->new Color(148,78,141);
        case CONTINENTAL_RIFT,RIFTED_MARGIN->new Color(78,154,148);case OCEAN_SPREADING->new Color(73,123,179);case TRANSFORM->new Color(191,161,77);case QUIET->new Color(175,183,190);};}
    private static void render(List<Edge> cases)throws Exception {
        int size=257,step=256;long origin=-32768;var params=Params.defaults();var topology=new PlateTopology(42,params);var forcing=new BoundaryForcing(42,params);
        BufferedImage image=new BufferedImage(1200,910,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        g.setColor(new Color(245,247,249));g.fillRect(0,0,1200,910);g.setColor(new Color(25,34,46));g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,20));
        g.drawString("Plate motion becomes shared boundary forcing",20,29);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));
        g.drawString("Seed 42 | existing plate vectors | reference anomalies, not absolute terrain height or an ocean mask",20,54);
        BufferedImage[] maps={new BufferedImage(size,size,BufferedImage.TYPE_INT_RGB),new BufferedImage(size,size,BufferedImage.TYPE_INT_RGB),new BufferedImage(size,size,BufferedImage.TYPE_INT_RGB)};
        for(int z=0;z<size;z++)for(int x=0;x<size;x++){
            long wx=origin+x*(long)step,wz=origin+z*(long)step;var s=topology.sample(wx,wz);var f=forcing.sample(wx,wz);
            int r=(int)Math.floorMod(s.owner.id*31,80);maps[0].setRGB(x,z,new Color(145+r,180,190-r/2).getRGB());
            maps[1].setRGB(x,z,(Math.abs(f.offsetPermille())<700?regimeColor(f.edge().regime()):new Color(224,228,232)).getRGB());
            int v=Math.min(170,Math.abs(f.anomaly())/5);maps[2].setRGB(x,z,(f.anomaly()>=0?new Color(225,225-v,220-v):new Color(225-v,225-v/2,230)).getRGB());
        }
        String[] labels={"Plate direction / speed","Closest-boundary regime","Signed relief anomaly (+warm / -blue)"};
        for(int panel=0;panel<3;panel++){int ox=20+panel*395;g.setColor(new Color(25,34,46));g.drawString(labels[panel],ox,83);g.drawImage(maps[panel],ox,95,350,350,null);}
        // Arrows are sampled once at each existing site; magnitude is velocity, not random decoration.
        g.setColor(new Color(30,44,58));
        for(int j=-3;j<=3;j++)for(int i=-3;i<=3;i++){
            var s=topology.site(i,j);int x=20+(int)((s.x-origin)*350/(256L*step)),y=95+(int)((s.z-origin)*350/(256L*step));
            if(x<25||x>365||y<100||y>440)continue;double dx=s.vx*.32,dz=s.vz*.32,length=Math.hypot(dx,dz);
            int ex=(int)Math.round(x+dx),ey=(int)Math.round(y+dz);g.drawLine(x,y,ex,ey);
            if(length>0){double ux=dx/length,uz=dz/length;g.drawLine(ex,ey,(int)(ex-6*ux+3*uz),(int)(ey-6*uz-3*ux));g.drawLine(ex,ey,(int)(ex-6*ux-3*uz),(int)(ey-6*uz+3*ux));}
        }
        g.drawString("Regimes: brown collision | purple subduction | teal rift | blue spreading | gold shear | gray quiet",20,473);
        for(int k=0;k<cases.size();k++){
            var e=cases.get(k);int ox=20+k%3*395,oy=515+k/3*185;
            g.setColor(new Color(25,34,46));g.drawString(e.regime().toString().replace('_',' '),ox,oy);
            g.setColor(new Color(212,217,224));g.drawLine(ox,oy+76,ox+350,oy+76);g.drawLine(ox+175,oy+12,ox+175,oy+136);
            int lastX=ox,lastY=oy+76;g.setColor(regimeColor(e.regime()));
            for(int p=0;p<=350;p++){int u=-1000+p*2000/350,y=oy+76-BoundaryForcing.anomaly(e,u)*3/5;g.drawLine(lastX,lastY,ox+p,y);lastX=ox+p;lastY=y;}
            g.setColor(new Color(65,77,90));g.drawString(k==4?"Shear retained; no invented vertical forcing":k==5?"Same velocity on both sides": "A side           boundary           B side",ox,oy+151);
        }
        g.setColor(new Color(25,34,46));g.drawString("Profiles use controlled vectors/crusts. Fixed width and illustrative gains; closest-edge junction blending is not yet solved.",20,894);
        g.dispose();Files.createDirectories(Path.of("build/gallery"));ImageIO.write(image,"png",Path.of("build/gallery/boundary-forcing.png").toFile());
    }
}
