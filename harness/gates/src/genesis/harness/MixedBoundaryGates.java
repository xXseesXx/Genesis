package genesis.harness;

import genesis.core.hash.Lattice;
import genesis.oracle.MaritimeEnvelope;
import genesis.oracle.MaritimeEnvelope.Key;
import genesis.oracle.MixedBoundaryEnvelope;
import genesis.oracle.MixedBoundaryEnvelope.Edge;
import genesis.oracle.MixedBoundaryEnvelope.Type;
import genesis.oracle.MixedBoundaryLandmass;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;

/** Mixed boundary topology and land-connection evidence; no river/physical-divide claim. */
final class MixedBoundaryGates {
    private static final int SIDE = 257;
    private record Pixel(Key owner, boolean land, int water, int ridge, boolean reserve) {}
    private record Audit(long seed, int spacing, Pixel[] pixels, int roots, int bridges, int land,
                         int completeOwned, int completeLand, int interiorWater, double ms) {}
    private static final class Fixture {
        final long seed; final int spacing; final MixedBoundaryEnvelope envelope;
        final Map<Key,MixedBoundaryLandmass> roots = new HashMap<>();
        Fixture(long seed, int spacing) { this.seed=seed; this.spacing=spacing; envelope=new MixedBoundaryEnvelope(seed,spacing,spacing/32); }
        Pixel sample(long x, long z) {
            var s=envelope.sample(x,z);
            var a=roots.computeIfAbsent(s.owner(), key->new MixedBoundaryLandmass(seed,spacing,spacing/32,key,53));
            int p=a.index(x,z); return new Pixel(s.owner(),a.land(p),a.water(p),a.ridge(p),s.ocean());
        }
    }
    private static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    private static void rejects(Runnable run) {
        try { run.run(); } catch(IllegalArgumentException|ArithmeticException expected) { return; }
        throw new AssertionError("Invalid mixed-boundary input accepted");
    }
    public static void main(String[] args) throws Exception { run(); }
    static void run() throws Exception {
        contracts(); List<Audit> audits=new ArrayList<>();
        for(long seed:new long[]{42,-1,137}) audits.add(audit(seed,524288));
        audits.add(audit(42,65536)); stability(audits.get(0));
        write(audits); render(audits.subList(0,3));
        System.out.println("PASS R2c MIXED: bounded symmetric ridge pairs, larger-support nearest reference, shared junctions, actual land bridges, 53% full-cell budget, water connectivity/cold/order/concurrency; NOT physical drainage");
    }
    private static void contracts() {
        Random random=new Random(14097);
        for(int spacing:new int[]{64,65536,524288,1048576}) for(long seed:new long[]{42,-1,Long.MIN_VALUE}) {
            var e=new MixedBoundaryEnvelope(seed,spacing,Math.max(1,spacing/32));
            for(int k=0;k<12;k++) {
                Key key=new Key(random.nextInt(2001)-1000,random.nextInt(2001)-1000);
                if(k==0) key=new Key(Lattice.MAX_COORDINATE/spacing-4,-Lattice.MAX_COORDINATE/spacing+4);
                var center=e.site(key); Key best=null; BigInteger distance=null;
                for(int dz=-4;dz<=4;dz++) for(int dx=-4;dx<=4;dx++) if(dx!=0||dz!=0) {
                    var s=e.site(new Key(key.i()+dx,key.j()+dz));
                    BigInteger x=BigInteger.valueOf(s.x()).subtract(BigInteger.valueOf(center.x()));
                    BigInteger z=BigInteger.valueOf(s.z()).subtract(BigInteger.valueOf(center.z()));
                    BigInteger d=x.multiply(x).add(z.multiply(z));
                    if(distance==null||d.compareTo(distance)<0||d.equals(distance)&&s.key().compareTo(best)<0) { distance=d;best=s.key(); }
                }
                check(e.nearest(key).equals(best),"Fixed nearest support differs from 9x9 BigInteger reference");
                Key partner=e.partner(key);
                if(partner!=null) {
                    check(key.equals(e.partner(partner)),"Partner is not mutual");
                    Edge edge=new Edge(key,partner);
                    check(edge.equals(new Edge(partner,key))&&e.type(edge)==Type.RIDGE,"Shared edge identity/type disagreement");
                    // Empty diameter disk independently certifies an actual shared Voronoi face.
                    var other=e.site(partner); long mx=center.x()+other.x(),mz=center.z()+other.z();
                    var middle=e.sample(Math.floorDiv(mx,2),Math.floorDiv(mz,2));
                    check(!middle.ocean()&&middle.ridgeStrength()>0,"Mutual-pair midpoint lost its land bridge contract");
                    BigInteger radius=distance;
                    for(int dz=-4;dz<=4;dz++) for(int dx=-4;dx<=4;dx++) {
                        Key q=new Key(key.i()+dx,key.j()+dz); if(q.equals(key)||q.equals(partner)) continue;
                        var s=e.site(q);
                        BigInteger x=BigInteger.valueOf(2*s.x()-mx),z=BigInteger.valueOf(2*s.z()-mz);
                        check(x.multiply(x).add(z.multiply(z)).compareTo(radius)>0,"Ridge pair lacks an empty midpoint disk");
                    }
                }
                var cold=new MixedBoundaryEnvelope(seed,spacing,Math.max(1,spacing/32));
                check(java.util.Objects.equals(partner,cold.partner(key)),"Cold pair decision differs");
                // Independent 9x9 four-corner clearance, not the implementation's bisector-gap test.
                long x=center.x()+random.nextInt(spacing)-spacing/2,z=center.z()+random.nextInt(spacing)-spacing/2;
                var actual=e.sample(x,z); var chosen=e.site(actual.owner()); boolean ocean=false;
                long i=Math.floorDiv(x,spacing),j=Math.floorDiv(z,spacing);
                for(int dz=-4;dz<=4;dz++)for(int dx=-4;dx<=4;dx++) {
                    var candidate=e.site(new Key(i+dx,j+dz));
                    if(candidate.key().equals(actual.owner())||candidate.key().equals(actual.partner()))continue;
                    for(int cz:new int[]{-e.collar,e.collar})for(int cx:new int[]{-e.collar,e.collar}) {
                        long qx=x+cx,qz=z+cz;
                        long ax=qx-candidate.x(),az=qz-candidate.z(),bx=qx-chosen.x(),bz=qz-chosen.z();
                        if(ax*ax+az*az<=bx*bx+bz*bz)ocean=true;
                    }
                }
                check(actual.ocean()==ocean,"Mixed reserve differs from independent corner clearance");
            }
        }
        rejects(()->new Edge(new Key(0,0),new Key(0,0)));
        rejects(()->new MixedBoundaryEnvelope(42,65536,1));
        rejects(()->new MixedBoundaryLandmass(42,524288,16384,new Key(Lattice.MAX_COORDINATE/524288,0),53));
        var low=new MixedBoundaryLandmass(42,524288,16384,new Key(0,0),50);
        var high=new MixedBoundaryLandmass(42,524288,16384,new Key(0,0),55);
        for(int p=0;p<low.size();p++) check(!low.land(p)||high.land(p),"Increasing total-cell coverage erased land");
        rejects(()->low.index(low.x0+1,low.z0)); rejects(()->low.index(low.x0-low.step,low.z0));
        rejects(()->new MixedBoundaryLandmass(42,524288,16384,new Key(0,0),0));
    }
    private static Audit audit(long seed,int spacing) {
        long start=System.nanoTime(); Fixture f=new Fixture(seed,spacing); Pixel[] pixels=new Pixel[SIDE*SIDE];
        Set<Edge> bridges=new HashSet<>(); int land=0,water=0;
        for(int p=0;p<pixels.length;p++) {
            var s=f.sample(-spacing+p%SIDE*(long)(spacing/64),-spacing+p/SIDE*(long)(spacing/64)); pixels[p]=s;
            check(s.water!=3,"Global owner lookup exposed unmodeled neighbor");
            if(s.land) {land++;check(!s.reserve,"Land obstructs ocean contract");}
            if(s.water==2) water++;
            if(s.ridge>0) check(s.land,"Shared ridge band not realized as land");
            for(int dz=-1;dz<=0;dz++) for(int dx=-1;dx<=1;dx++) {
                if(dz==0&&dx>=0) continue;
                int x=p%SIDE+dx,z=p/SIDE+dz; if(x<0||z<0||x>=SIDE) continue;
                var q=pixels[z*SIDE+x];
                if(s.land&&q.land&&!s.owner.equals(q.owner)) {
                    Edge edge=new Edge(s.owner,q.owner); check(f.envelope.type(edge)==Type.RIDGE,"Land crosses an ocean boundary");
                    if(Math.abs(dx)+Math.abs(dz)==1) bridges.add(edge);
                }
            }
        }
        int totalOwned=0,totalLand=0;
        for(var a:f.roots.values()) { verifyRoot(a,f.envelope);totalOwned+=a.ownedCount;totalLand+=a.landCount; }
        check(!bridges.isEmpty(),"Mixed design produced no D4 inter-cell land connection");
        check(totalLand/(double)totalOwned>=0.50&&totalLand/(double)totalOwned<=0.55,"Complete-cell area target missed");
        // Display boundary flags are a diagnostic only, never ocean seeds for the generator.
        int[] set=new int[pixels.length];for(int p=0;p<set.length;p++)set[p]=p;
        for(int p=0;p<pixels.length;p++) if(!pixels[p].land) {
            if(p%SIDE>0&&!pixels[p-1].land)join(set,p,p-1);
            if(p>=SIDE&&!pixels[p-SIDE].land)join(set,p,p-SIDE);
        }
        boolean[] edge=new boolean[set.length];
        for(int p=0;p<set.length;p++)if(!pixels[p].land&&(p%SIDE==0||p%SIDE==SIDE-1||p/SIDE==0||p/SIDE==SIDE-1))edge[root(set,p)]=true;
        for(int p=0;p<set.length;p++)if(pixels[p].reserve)check(edge[root(set,p)],"Sampled ocean reserve is enclosed by land");
        return new Audit(seed,spacing,pixels,f.roots.size(),bridges.size(),land,totalOwned,totalLand,water,(System.nanoTime()-start)/1e6);
    }
    private static void verifyRoot(MixedBoundaryLandmass a,MixedBoundaryEnvelope e) {
        int n=a.size(),owned=0,count=0,first=-1; int[] landSet=new int[n],waterSet=new int[n];boolean[] ocean=new boolean[n];
        for(int p=0;p<n;p++){landSet[p]=p;waterSet[p]=p;}
        for(int p=0;p<n;p++) {
            var s=e.sample(a.x(p),a.z(p)); ocean[p]=s.ocean(); boolean own=s.owner().equals(a.owner); if(own)owned++;
            if(a.land(p)) {count++;first=p;check(own&&!s.ocean()&&a.water(p)==0,"Invalid own land");}
            if(own&&s.ridgeStrength()>0)check(a.land(p),"Mandatory ridge missing");
            if(s.ocean())check(a.water(p)==1&&a.ridge(p)==0,"Junction reserve is not retained or has an obstructing ridge");
            int expected=a.land(p)?0:own||s.ocean()?2:3;
            check(a.water(p)==expected||expected==2&&a.water(p)==1,"Water ownership class mismatch");
            for(int q:new int[]{p%193==0?-1:p-1,p<193?-1:p-193}) if(q>=0) {
                if(a.land(p)&&a.land(q))join(landSet,p,q);
                if(!a.land(p)&&!a.land(q)&&a.water(p)!=3&&a.water(q)!=3)join(waterSet,p,q);
            }
        }
        check(owned==a.ownedCount&&count==a.landCount&&count==owned*53/100,"Full owned-cell budget mismatch");
        boolean[] terminal=new boolean[n];for(int p=0;p<n;p++)if(ocean[p])terminal[root(waterSet,p)]=true;
        boolean hasOwnOutlet=false;
        for(int p=0;p<n;p++) {
            if(a.land(p)) check(root(landSet,p)==root(landSet,first),"Own land disconnected");
            if(a.water(p)==1||a.water(p)==2)check((a.water(p)==1)==terminal[root(waterSet,p)],"Independent water connectivity disagreement");
            if(a.land(p))for(int q:new int[]{p%193==0?-1:p-1,p%193==192?-1:p+1,p<193?-1:p-193,p>=n-193?-1:p+193})
                if(q>=0&&a.water(q)==1)hasOwnOutlet=true;
        }
        check(hasOwnOutlet,"Cell land lacks sampled ocean-adjacent outlet candidates");
    }
    private static int root(int[] set,int p){while(set[p]!=p){set[p]=set[set[p]];p=set[p];}return p;}
    private static void join(int[] set,int p,int q){set[root(set,p)]=root(set,q);}
    private static void stability(Audit a)throws Exception {
        List<Integer> points=new ArrayList<>();for(int z=37;z<213;z+=13)for(int x=29;x<225;x+=11)points.add(z*SIDE+x);
        Collections.shuffle(points,new Random(82));
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Callable<Boolean>> jobs=new ArrayList<>();
            for(int t=0;t<2;t++){final boolean reverse=t==1;jobs.add(()->{
                Fixture cold=new Fixture(a.seed,a.spacing);
                for(int k=0;k<points.size();k++){int p=points.get(reverse?points.size()-1-k:k);
                    if(!a.pixels[p].equals(cold.sample(-a.spacing+p%SIDE*(long)(a.spacing/64),-a.spacing+p/SIDE*(long)(a.spacing/64))))return false;}
                return true;});}
            for(var result:pool.invokeAll(jobs))check(result.get(),"Cold cropped/strided/concurrent/order sample mismatch");
        }
    }
    private static void write(List<Audit> audits)throws Exception {
        StringBuilder rows=new StringBuilder();for(var a:audits){if(!rows.isEmpty())rows.append(",\n");rows.append(String.format(Locale.ROOT,
            "    {\"seed\":%d,\"spacing\":%d,\"roots\":%d,\"d4RidgeBridges\":%d,\"displayLandFraction\":%.6f,\"fullRootLandFraction\":%.6f,\"fullRootOwnedSamples\":%d,\"fullRootLandSamples\":%d,\"unconnectedWaterSamples\":%d,\"fullAuditMs\":%.3f}",
            a.seed,a.spacing,a.roots,a.bridges,a.land/(double)a.pixels.length,a.completeLand/(double)a.completeOwned,a.completeOwned,a.completeLand,a.interiorWater,a.ms));}
        Files.createDirectories(Path.of("build/gallery"));Files.writeString(Path.of("build/mixed-boundary.json"),"""
            {
              "experiment":"mixed-boundary-v1",
              "status":"paired-cell land connection and ocean topology; not physical drainage",
              "landTargetPercentOfTotalOwnedArea":53,
              "maxCellsPerMaritimeGroup":2,
              "window":"[-S,3S] inclusive, 257 square, canonical step S/64",
              "nearestReferenceCases":144,
              "audits":[
            %s
              ]
            }
            """.formatted(rows));
    }
    private static void render(List<Audit> audits)throws Exception {
        BufferedImage image=new BufferedImage(1150,495,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        g.setColor(new Color(245,247,249));g.fillRect(0,0,1150,495);g.setColor(new Color(25,34,46));g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,19));
        g.drawString("R2c: land joins across shared ridge contracts; ocean junctions stay open",20,28);
        g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));g.drawString("S = 524288 | 53% of total owned mesh area | disjoint two-cell pairs, not final continent architecture",20,52);
        for(int k=0;k<audits.size();k++) {
            var a=audits.get(k);BufferedImage map=new BufferedImage(SIDE,SIDE,BufferedImage.TYPE_INT_RGB);
            for(int p=0;p<a.pixels.length;p++){var s=a.pixels[p];Color c=s.land?(s.ridge>0?new Color(169,126,84):new Color(126,158,106))
                :s.water==2?new Color(84,176,183):s.reserve?new Color(21,54,90):new Color(40,82,125);map.setRGB(p%SIDE,p/SIDE,c.getRGB());}
            int x=20+k*375;g.drawString(String.format(Locale.ROOT,"Seed %d | view %.1f%% land | %d ridge joins",a.seed,100.0*a.land/a.pixels.length,a.bridges),x,88);
            g.drawImage(map,x,100,330,330,null);
        }
        g.drawString("Brown: mandatory shared ridge land (constraint, not simulated mountain heights). Green: land. Blue: water.",20,459);
        g.drawString("Ocean wins at mixed junctions. Rivers and physically preserved divides remain to be implemented; main viewer unchanged.",20,482);
        g.dispose();ImageIO.write(image,"png",Path.of("build/gallery/mixed-boundary.png").toFile());
    }
}
