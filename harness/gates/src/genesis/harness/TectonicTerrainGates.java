package genesis.harness;

import genesis.core.Params;
import genesis.core.hash.Lattice;
import genesis.core.hash.Hash64;
import genesis.core.tectonics.PlateTopology;
import genesis.oracle.BoundaryForcing.Plate;
import genesis.oracle.JunctionForcing;
import genesis.oracle.CrustProvinces;
import genesis.oracle.TectonicTerrain;
import genesis.oracle.TectonicTerrain.Settings;
import genesis.oracle.TectonicTerrain.Sample;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;

/** Absolute terrain consistency, bounded support and multi-seed morphology screening. */
final class TectonicTerrainGates {
    private static final int SIDE=257;
    private record Components(int count,int largest,int largestPlates,int largestSpan,int cutComponents) {}
    private record Audit(long seed,Sample[] samples,double landPercent,double wideLandPercent,double baseLandPercent,int changedCoasts,
                         Components land,Components water,long sharedLandEdges,double renderMs) {}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void rejects(Runnable job){try{job.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Invalid terrain input accepted");}
    public static void main(String[] args)throws Exception{run();}
    static void run()throws Exception {
        Settings settings=Settings.defaults();Params params=TectonicTerrain.defaultPlateParams();Random random=new Random(77019);
        int cases=0;var digest=MessageDigest.getInstance("SHA-256");var bytes=ByteBuffer.allocate(57);
        for(long seed:new long[]{42,-1,Long.MIN_VALUE})for(int spacing:new int[]{8192,65536,262144})for(int radius:new int[]{1100,1500,2500}) {
            Params p=new Params(Map.of("plateSpacing",(double)spacing,"continentalPercent",53.0));
            Settings s=new Settings(radius,500,1800,4200,1000,180,0,4);var world=new TectonicTerrain(seed,p,s);var topology=new PlateTopology(seed,p);
            for(int trial=0;trial<20;trial++) {
                long x=(random.nextInt(201)-100L)*spacing+random.nextInt(spacing),z=(random.nextInt(201)-100L)*spacing+random.nextInt(spacing);
                if(trial<4){x=(trial%2==0?1:-1)*(Lattice.MAX_COORDINATE-1);z=(trial<2?1:-1)*(Lattice.MAX_COORDINATE-1);}
                if(trial>=4&&trial<8)x=(random.nextInt(40)-20L)*spacing+trial%3-1;
                Sample a=world.sample(x,z);var owner=topology.sample(x,z).owner;
                check(a.owner().id()==owner.id&&a.owner().x()==owner.x&&a.owner().z()==owner.z&&a.owner().vx()==owner.vx&&a.owner().vz()==owner.vz&&a.owner().age()==owner.age,"Terrain changed plate geometry/motion/age");
                check(a.elevation()==a.baseElevation()+a.detail()+a.positiveForcing()+a.negativeForcing()&&a.land()==(a.elevation()>s.seaLevel()),"Terrain component/coast mismatch");
                var larger=neighborhood(world,x,z);var f=JunctionForcing.compose(larger,x,z,spacing,35);
                check(a.positiveForcing()==f.positive()&&a.negativeForcing()==f.negative(),"Terrain forcing uses another crust/configuration");
                var crust=TectonicTerrain.crust(larger,x,z,spacing,radius);
                check(a.crustFraction()==crust.fraction()&&a.crustSites()==crust.supportedSites(),"49-site crust differs from 121-site support");
                check(Math.abs(a.crustFraction()-referenceCrust(larger,x,z,spacing,radius))<1e-12,"Independent crust weights differ");
                Collections.shuffle(larger,random);check(crust.equals(TectonicTerrain.crust(larger,x,z,spacing,radius)),"Crust caller order changed value");
                check(a.equals(new TectonicTerrain(seed,p,s).sample(x,z)),"Cold terrain changed sample");
                check(Double.isFinite(a.elevation())&&a.crustFraction()>=0&&a.crustFraction()<=1,"Unbounded/invalid terrain value");
                fingerprint(digest,bytes,a);cases++;
            }
        }
        var world=new TectonicTerrain(42,params,settings);
        var baseOnly=new TectonicTerrain(42,params,new Settings(1500,500,1800,4200,0,0,0,4));
        var higherSea=new TectonicTerrain(42,params,new Settings(1500,500,1800,4200,1000,180,200,4));
        List<Callable<Sample>> jobs=new ArrayList<>();
        for(int z=0;z<24;z++)for(int x=0;x<24;x++) {
            long wx=-262144+x*32768L,wz=-262144+z*32768L;Sample a=world.sample(wx,wz),b=baseOnly.sample(wx,wz),c=higherSea.sample(wx,wz);
            check(a.baseElevation()==b.baseElevation()&&a.crustFraction()==b.crustFraction()&&a.owner().equals(b.owner()),"Detail/forcing feeds back into crust");
            check(b.elevation()==b.baseElevation()&&b.detail()==0&&b.positiveForcing()==0&&b.negativeForcing()==0,"Zero-detail/forcing controls ignored");
            check(a.elevation()==c.elevation()&&(!c.land()||a.land()),"Fixed higher sea level changed bed or created land");
            check(a.equals(world.sample(-327680+(x+2)*32768L,-327680+(z+2)*32768L)),"Overlapping crop changed shared coordinates");
            if((x+z)%8==0)jobs.add(()->world.sample(wx,wz));
        }
        try(var pool=Executors.newFixedThreadPool(4)){var results=pool.invokeAll(jobs);for(int p=0;p<jobs.size();p++)check(results.get(p).get().equals(jobs.get(p).call()),"Concurrent terrain differs");}
        // Extreme plate-crust presets must remain valid, but are not land-target presets.
        for(int percent:new int[]{0,100}) {
            var all=new TectonicTerrain(42,new Params(Map.of("continentalPercent",(double)percent)),settings);
            for(int i=0;i<20;i++)check(all.sample(i*12289L,-i*8191L).crustFraction()==percent/100.0,"Uniform crust fraction endpoint");
        }
        rejects(()->new Settings(1099,500,1800,4200,1000,180,0,4));
        rejects(()->new TectonicTerrain(42,params,null));rejects(()->world.sample(Long.MAX_VALUE,0));
        rejects(()->TectonicTerrain.crust(List.of(),0,0,65536,1500));
        provinceChecks();componentFixtures();
        List<Audit> audits=new ArrayList<>();
        for(long seed:new long[]{42,-1,137,8675309,0,Long.MIN_VALUE})audits.add(audit(seed));
        double mean=audits.stream().mapToDouble(Audit::wideLandPercent).average().orElseThrow();
        String hash=HexFormat.of().formatHex(digest.digest());
        check(hash.equals("10dafe06b67a3c6a1dc165aff64a8d563e3df2af6cf04b5c7ae52fd14bd8141b"),"Versioned absolute-terrain fingerprint changed");
        for(var a:audits)check(a.wideLandPercent>=50&&a.wideLandPercent<=55,"Default preset left target range in fixed wide-area screening");
        write(audits,cases,mean,hash);render(audits);renderLayers();renderComparison();
        System.out.println("PASS TECTONIC TERRAIN: "+cases+" support/independent/cold cases; component/coast/counterfactual/concurrent tests; six-seed sampled mean land="+mean+"%; fingerprint="+hash);
    }
    private static void fingerprint(MessageDigest digest,ByteBuffer bytes,Sample s) {
        bytes.clear();bytes.putLong(s.owner().id()).putDouble(s.crustFraction()).putDouble(s.baseElevation()).putDouble(s.detail())
            .putDouble(s.positiveForcing()).putDouble(s.negativeForcing()).putDouble(s.elevation()).put((byte)(s.land()?1:0));digest.update(bytes.array());
    }
    private static void provinceChecks() {
        Random random=new Random(882017);
        for(long seed:new long[]{42,-1,Long.MIN_VALUE})for(int spacing:new int[]{32768,1048576}) {
            var provinces=new CrustProvinces(seed,spacing,53);
            for(int trial=0;trial<80;trial++) {
                long x=(random.nextInt(201)-100L)*spacing+random.nextInt(spacing),z=(random.nextInt(201)-100L)*spacing+random.nextInt(spacing);
                if(trial<4){x=(trial%2==0?1:-1)*Lattice.MAX_COORDINATE;z=(trial<2?1:-1)*Lattice.MAX_COORDINATE;}
                var actual=provinces.sample(x,z);CrustProvinces.Province selected=null;long best=Long.MAX_VALUE;
                for(int dz=-3;dz<=3;dz++)for(int dx=-3;dx<=3;dx++) {
                    var p=provinces.site(Math.floorDiv(x,spacing)+dx,Math.floorDiv(z,spacing)+dz);long px=p.x()-x,pz=p.z()-z,d=px*px+pz*pz;
                    if(d<best||d==best&&(selected==null||Long.compareUnsigned(p.id(),selected.id())<0)){best=d;selected=p;}
                }
                check(actual.equals(selected),"9-site province differs from 49-site reference");
                check(actual.equals(new CrustProvinces(seed,spacing,53).sample(x,z)),"Province cold lookup changed");
            }
        }
        rejects(()->new CrustProvinces(42,0,53));rejects(()->new CrustProvinces(42,32768,53).sample(Long.MAX_VALUE,0));
    }
    private static List<Plate> neighborhood(TectonicTerrain world,long x,long z) {
        int spacing=world.plateParams.integer("plateSpacing");List<Plate> sites=new ArrayList<>();long i=Lattice.cell(x,spacing),j=Lattice.cell(z,spacing);
        for(int dz=-5;dz<=5;dz++)for(int dx=-5;dx<=5;dx++)sites.add(world.plate(i+dx,j+dz));return sites;
    }
    private static double referenceCrust(List<Plate> sites,long x,long z,int spacing,int radius) {
        double r=spacing*radius/1000.0,sum=0,land=0;
        for(int i=sites.size()-1;i>=0;i--){var p=sites.get(i);double d=(p.x()-x)*(double)(p.x()-x)+(p.z()-z)*(double)(p.z()-z);if(d>=r*r)continue;
            double w=Math.pow((r*r-d)/(r*r),3);sum+=w;land+=p.crust()*w;}
        return land/sum;
    }
    private static Audit audit(long seed) {
        long start=System.nanoTime();var world=new TectonicTerrain(seed,TectonicTerrain.defaultPlateParams(),Settings.defaults());int spacing=world.plateParams.integer("plateSpacing");
        Sample[] samples=new Sample[SIDE*SIDE];int land=0,base=0,changes=0;long shared=0;
        for(int p=0;p<samples.length;p++) {
            int x=p%SIDE,z=p/SIDE;var s=world.sample(-16L*spacing+x*(spacing/8L),-16L*spacing+z*(spacing/8L));samples[p]=s;
            if(s.land())land++;boolean baseLand=s.baseElevation()>0;if(baseLand)base++;if(s.land()!=baseLand)changes++;
            for(int q:new int[]{x>0?p-1:-1,z>0?p-SIDE:-1})if(q>=0&&s.land()&&samples[q].land()&&s.owner().id()!=samples[q].owner().id())shared++;
        }
        check(shared>0,"Terrain has no cross-plate land connections");
        var components=components(samples,true);check(components.largestPlates>2,"Largest sampled landmass stuck in a plate pair");
        // Separate larger-scale area audit: one fixed jittered sample per S-square across 256S, not coastline metrics.
        int wide=0;
        for(int z=0;z<256;z++)for(int x=0;x<256;x++) {
            long offsetX=Math.floorMod(Hash64.hash(7781,0,x,z),spacing),offsetZ=Math.floorMod(Hash64.hash(7781,1,x,z),spacing);
            if(world.sample((x-128L)*spacing+offsetX,(z-128L)*spacing+offsetZ).land())wide++;
        }
        return new Audit(seed,samples,100.0*land/samples.length,100.0*wide/65536,100.0*base/samples.length,changes,components,components(samples,false),shared,(System.nanoTime()-start)/1e6);
    }
    private static Components components(Sample[] samples,boolean land) {
        boolean[] seen=new boolean[samples.length];int[] queue=new int[samples.length];int count=0,largest=0,plates=0,span=0,cuts=0;
        for(int p=0;p<samples.length;p++)if(!seen[p]&&samples[p].land()==land) {
            int head=0,tail=1,minX=p%SIDE,maxX=minX,minZ=p/SIDE,maxZ=minZ;boolean cut=false;queue[0]=p;seen[p]=true;var owners=new HashSet<Long>();
            while(head<tail){int q=queue[head++],x=q%SIDE,z=q/SIDE;owners.add(samples[q].owner().id());minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
                cut|=x==0||z==0||x==SIDE-1||z==SIDE-1;
                for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)if(dx!=0||dz!=0){int nx=x+dx,nz=z+dz;if(nx<0||nz<0||nx>=SIDE||nz>=SIDE)continue;int r=nz*SIDE+nx;
                    if(!seen[r]&&samples[r].land()==land){seen[r]=true;queue[tail++]=r;}}
            }
            count++;if(cut)cuts++;if(tail>largest){largest=tail;plates=owners.size();span=Math.max(maxX-minX,maxZ-minZ);}
        }
        return new Components(count,largest,plates,span,cuts);
    }
    private static void componentFixtures() {
        var owner=new Plate(1,0,0,0,0,1,100);
        var sea=new Sample(owner,0,-10,0,0,0,-10,false,1,1);
        var land=new Sample(owner,1,10,0,0,0,10,true,1,1);
        Sample[] grid=new Sample[SIDE*SIDE];java.util.Arrays.fill(grid,sea);
        grid[100*SIDE+100]=land;grid[101*SIDE+101]=land;
        check(components(grid,true).equals(new Components(1,2,1,1,0)),"D8 diagonal land fixture");
        grid[0]=land;grid[1]=land;grid[2]=land;
        check(components(grid,true).equals(new Components(2,3,1,2,1)),"Crop-cut component fixture");
    }
    private static void write(List<Audit> audits,int cases,double mean,String hash)throws Exception {
        StringBuilder json=new StringBuilder("{\n  \"experiment\":\"tectonic-terrain-v1\",\n  \"status\":\"exploratory absolute elevation; below-sea-level is not certified ocean\",\n")
            .append("  \"preset\":{\"plateSpacing\":65536,\"continentalPercent\":53,\"crustProvinceScale\":4,\"crustRadiusPermille\":1500,\"seaThreshold\":500,\"seaLevel\":0},\n")
            .append("  \"supportCases\":").append(cases).append(",\n  \"fingerprint\":\"").append(hash).append("\",\n  \"sampledMeanLandPercent\":").append(mean)
            .append(",\n  \"sampling\":\"Morphology: 257x257 over 32S. Wide area: 65536 fixed jittered strata over 256S. Same preset for all seeds; D8 components crop-limited.\",\n  \"audits\":[\n");
        for(int i=0;i<audits.size();i++){var a=audits.get(i);if(i>0)json.append(",\n");json.append("    {\"seed\":\"").append(a.seed).append("\",\"landPercent\":").append(a.landPercent)
            .append(",\"wideLandPercent\":").append(a.wideLandPercent).append(",\"baseLandPercent\":").append(a.baseLandPercent).append(",\"changedCoastSamples\":").append(a.changedCoasts).append(",\"landComponents\":").append(a.land.count)
            .append(",\"largestLandSamples\":").append(a.land.largest).append(",\"largestLandPlateCount\":").append(a.land.largestPlates).append(",\"largestLandSpanPlateSpacings\":").append(a.land.largestSpan/8.0)
            .append(",\"cropCutLandComponents\":").append(a.land.cutComponents).append(",\"belowSeaLevelComponents\":").append(a.water.count).append(",\"cropCutWaterComponents\":").append(a.water.cutComponents)
            .append(",\"crossPlateLandEdges\":").append(a.sharedLandEdges).append(",\"combinedMorphologyAndWideAreaAuditMs\":").append(a.renderMs).append('}');}
        Files.createDirectories(Path.of("build/gallery"));Files.writeString(Path.of("build/tectonic-terrain.json"),json.append("\n  ]\n}\n").toString());
    }
    private static Color heightColor(double height) {
        if(height<=0){double t=Math.min(1,-height/4500);return blend(new Color(105,177,189),new Color(16,44,76),t);}
        if(height<600)return blend(new Color(183,187,126),new Color(104,142,82),height/600);
        if(height<2000)return blend(new Color(104,142,82),new Color(147,125,100),(height-600)/1400);
        return blend(new Color(147,125,100),new Color(240,242,240),Math.min(1,(height-2000)/1800));
    }
    private static Color blend(Color a,Color b,double t){return new Color((int)(a.getRed()+(b.getRed()-a.getRed())*t),(int)(a.getGreen()+(b.getGreen()-a.getGreen())*t),(int)(a.getBlue()+(b.getBlue()-a.getBlue())*t));}
    private static BufferedImage map(Sample[] samples,int layer) {
        var image=new BufferedImage(SIDE,SIDE,BufferedImage.TYPE_INT_RGB);
        for(int p=0;p<samples.length;p++) {
            Sample s=samples[p];Color color;
            if(layer<=1)color=heightColor(layer==0?s.elevation():s.baseElevation());
            else if(layer==2){double f=s.positiveForcing()+s.negativeForcing();color=blend(new Color(237,238,231),f>=0?new Color(175,67,40):new Color(38,101,171),Math.min(1,Math.abs(f)/1200));}
            else color=blend(new Color(35,74,114),new Color(191,160,103),s.crustFraction());
            image.setRGB(p%SIDE,p/SIDE,color.getRGB());
        }
        return image;
    }
    private static Graphics2D canvas(BufferedImage image,String title,String subtitle) {
        var g=image.createGraphics();g.setColor(new Color(246,248,250));g.fillRect(0,0,image.getWidth(),image.getHeight());g.setColor(new Color(25,34,46));
        g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,21));g.drawString(title,20,30);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));g.drawString(subtitle,20,55);return g;
    }
    private static void render(List<Audit> audits)throws Exception {
        var image=new BufferedImage(1200,920,BufferedImage.TYPE_INT_RGB);var g=canvas(image,"Tectonic terrain: connected crust, shared motion, fixed sea level","Six seeds | each view spans 32 plate spacings | same 53% crust preset, no viewport land normalization");
        for(int i=0;i<audits.size();i++){var a=audits.get(i);int ox=20+i%3*395,oy=90+i/3*390;
            g.drawString("Seed "+a.seed+String.format(Locale.ROOT," | %.2f%% local land",a.landPercent),ox,oy);g.drawImage(map(a.samples,0),ox,oy+12,350,350,null);}
        g.drawString("Elevation: deep blue below sea level | tan lowlands | green uplands | brown/white high relief. Model units, not Minecraft Y.",20,882);
        g.drawString("Connected components touching the crop are incomplete measurements. No water component is certified as global ocean.",20,906);
        g.dispose();ImageIO.write(image,"png",Path.of("build/gallery/tectonic-terrain.png").toFile());
    }
    private static void renderLayers()throws Exception {
        var world=new TectonicTerrain(42,TectonicTerrain.defaultPlateParams(),Settings.defaults());Sample[] samples=new Sample[SIDE*SIDE];
        for(int p=0;p<samples.length;p++)samples[p]=world.sample(-262144+p%SIDE*2048L,-262144+p/SIDE*2048L);
        var image=new BufferedImage(1000,1050,BufferedImage.TYPE_INT_RGB);var g=canvas(image,"One terrain recipe, inspectable contributions","Seed 42 | eight plate spacings across | all fields sampled at identical world coordinates");
        String[] labels={"Composed elevation (includes small detail)","Broad crustal base elevation","Signed plate-boundary forcing","Continuous continental crust fraction"};
        for(int k=0;k<4;k++){int ox=20+k%2*490,oy=90+k/2*460;g.drawString(labels[k],ox,oy);g.drawImage(map(samples,k),ox,oy+12,430,430,null);}
        g.drawString("No separate coast mask: land means composed elevation > fixed sea level. Motion can create or remove land near margins.",20,1022);
        g.dispose();ImageIO.write(image,"png",Path.of("build/gallery/tectonic-terrain-layers.png").toFile());
    }
    private static void renderComparison()throws Exception {
        var independent=new TectonicTerrain(42,TectonicTerrain.defaultPlateParams(),new Settings(1500,500,1800,4200,1000,180,0,1));
        var coherent=new TectonicTerrain(42,TectonicTerrain.defaultPlateParams(),Settings.defaults());
        Sample[] before=new Sample[SIDE*SIDE],after=new Sample[SIDE*SIDE];
        for(int p=0;p<before.length;p++){long x=-1048576+p%SIDE*8192L,z=-1048576+p/SIDE*8192L;before[p]=independent.sample(x,z);after[p]=coherent.sample(x,z);}
        var image=new BufferedImage(1000,610,BufferedImage.TYPE_INT_RGB);var g=canvas(image,"Crust organization matters more than the land percentage","Seed 42 | identical plate geometry and velocities | same 32S extent, base heights, sea level and detail");
        g.drawString("Independent crust per plate (screening baseline)",20,86);g.drawString("Correlated 4S crust provinces (candidate)",510,86);
        g.drawImage(map(before,0),20,98,450,450,null);g.drawImage(map(after,0),510,98,450,450,null);
        g.drawString("The new crust assignment feeds BOTH broad elevation and boundary regimes. Provinces are not hydrological roots.",20,578);
        g.drawString("Candidate still has straight plate boundaries, broad flat interiors and no geological history or river-conditioned valleys.",20,600);
        g.dispose();ImageIO.write(image,"png",Path.of("build/gallery/tectonic-crust-comparison.png").toFile());
    }
}
