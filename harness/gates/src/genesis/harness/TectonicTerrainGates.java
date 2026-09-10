package genesis.harness;

import genesis.core.Params;
import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;
import genesis.oracle.ContinentalGroups;
import genesis.oracle.IrregularPlates;
import genesis.oracle.TectonicTerrain;
import genesis.oracle.TectonicTerrain.Sample;
import genesis.oracle.TectonicTerrain.Settings;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;

/** Sparse irregular-plate, finite continent-family and absolute terrain acceptance gates. */
final class TectonicTerrainGates {
    private static final int SIDE=193;
    private record Components(int count,int largest,int largestPlates,int cutComponents) {}
    private record Audit(long seed,Sample[] samples,double localLand,double wideLand,int visiblePlates,int mixedPlates,
                         int wellSampledPlates,int singletonContinents,int multiPlateContinents,Components land,Components water,double millis) {}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void rejects(Runnable job){try{job.run();}catch(IllegalArgumentException|ArithmeticException expected){return;}throw new AssertionError("Invalid terrain input accepted");}
    public static void main(String[] args)throws Exception{run();}

    static void run()throws Exception {
        Random random=new Random(77019);int supportCases=0;
        var digest=MessageDigest.getInstance("SHA-256");var bytes=ByteBuffer.allocate(128);
        for(long seed:new long[]{42,-1,Long.MIN_VALUE})for(int spacing:new int[]{8192,65536,524288,1048576}) {
            Params params=new Params(Map.of("plateSpacing",(double)spacing,"continentalPercent",53.0));
            var plates=new IrregularPlates(seed,params,220,140);var world=new TectonicTerrain(seed,params,Settings.defaults());
            for(int trial=0;trial<24;trial++) {
                long x=(random.nextInt(201)-100L)*spacing+random.nextInt(spacing),z=(random.nextInt(201)-100L)*spacing+random.nextInt(spacing);
                if(trial<4){x=(trial%2==0?1:-1)*(Lattice.MAX_COORDINATE-1);z=(trial<2?1:-1)*(Lattice.MAX_COORDINATE-1);}
                var compact=plates.sample(x,z,3);var larger=plates.sample(x,z,5);Sample sample=world.sample(x,z);
                check(compact.owner().equals(larger.owner())&&compact.neighbor().equals(larger.neighbor()),"7x7 plate support differs from 11x11 reference");
                check(sample.owner().id()==compact.owner().id(),"Terrain and irregular partition select different owners");
                check(sample.elevation()==sample.baseElevation()+sample.detail()+sample.positiveForcing()+sample.negativeForcing(),"Terrain components do not compose exactly");
                check(sample.land()==(sample.elevation()>Settings.defaults().seaLevel()),"Land does not follow fixed sea level");
                check(sample.continentPlateCount()>=1&&sample.continentPlateCount()<=4,"Continental group escaped 1..4 cap");
                check(Double.isFinite(sample.elevation())&&sample.crustFraction()>=0&&sample.crustFraction()<=1&&sample.boundaryDistance()>=0,"Invalid terrain value");
                check(sample.equals(new TectonicTerrain(seed,params,Settings.defaults()).sample(x,z)),"Cold sample changed");
                check(sample.equals(world.sample(x,z,5)),"Terrain fields differ with larger support");
                fingerprint(digest,bytes,sample);supportCases++;
            }
        }
        groupChecks();attributeChecks();counterfactualChecks();barrierChecks();componentFixtures();
        List<Audit> audits=new ArrayList<>();for(long seed:new long[]{42,-1,137,8675309,0,Long.MIN_VALUE})audits.add(audit(seed));
        double mean=audits.stream().mapToDouble(Audit::wideLand).average().orElseThrow();
        for(Audit audit:audits) {
            check(audit.wideLand>=50&&audit.wideLand<=55,"Default preset left 50..55% wide-area target for seed "+audit.seed+": "+audit.wideLand);
            check(audit.wellSampledPlates>10&&audit.mixedPlates>=audit.wellSampledPlates*.85,"Too many well-sampled plates are wholly land or water");
            check(audit.singletonContinents>audit.multiPlateContinents,"Singleton continents are not the common case");
            check(audit.land.largestPlates<=4,"A sampled landmass crossed the constructed four-plate cap");
        }
        String fingerprint=HexFormat.of().formatHex(digest.digest());
        check(fingerprint.equals("e9f11af63c11466bf6ffaea5af7752ff3d4ceb8b22e999337e23563a831c2583"),"Versioned v2 terrain fingerprint changed");
        write(audits,supportCases,mean,fingerprint);render(audits);renderArchitecture();
        System.out.println("PASS TECTONIC TERRAIN V2: "+supportCases+" bounded-support/cold cases; 1..4 plate continents; mixed submerged plates; six-seed mean land="+mean+"%; fingerprint="+fingerprint);
    }

    private static void groupChecks() {
        var groups=new ContinentalGroups(42);Map<Long,Integer> sizes=new HashMap<>();int cells=0;
        for(long j=-50;j<50;j++)for(long i=-50;i<50;i++) {
            var group=groups.group(i,j);check(group.size()>=1&&group.size()<=4,"Invalid group size");
            boolean member=false;for(var cell:group.members())member|=cell.i()==i&&cell.j()==j;
            check(member,"Plate is not a member of its own continent");
            for(var cell:group.members())check(groups.group(cell.i(),cell.j()).id()==group.id(),"Group membership is not reciprocal");
            sizes.put(group.id(),group.size());cells++;
        }
        long singles=sizes.values().stream().filter(v->v==1).count();check(singles>sizes.size()/2,"Singles do not dominate continent construction");
        check(sizes.values().stream().anyMatch(v->v==3)&&sizes.values().stream().anyMatch(v->v==4),"Multi-plate pattern classes missing");
        check(cells==10000,"Group coverage fixture changed");
    }
    private static void attributeChecks() {
        Params params=TectonicTerrain.defaultPlateParams();var plates=new IrregularPlates(42,params,220,140);
        int small=0,large=0,recent=0;var scales=new HashSet<Integer>();
        for(int j=-20;j<=20;j++)for(int i=-20;i<=20;i++) {
            var site=plates.site(i,j);scales.add(site.scalePermille());if(site.scalePermille()<800)small++;if(site.scalePermille()>1450)large++;
            if(site.recentFracture()){recent++;check(site.scalePermille()<=750&&site.age()<=params.integer("crustAgeMax")/12,"Fracture class is not small and young");}
        }
        check(small>100&&large>50&&recent>100&&scales.size()>500,"Plate scale/fracture distribution collapsed");
        var plain=new IrregularPlates(42,params,0,0);int changed=0;
        int spacing=params.integer("plateSpacing");for(int z=-64;z<=64;z++)for(int x=-64;x<=64;x++)
            if(plates.sample(x*spacing/16L,z*spacing/16L).owner().id()!=plain.sample(x*spacing/16L,z*spacing/16L).owner().id())changed++;
        check(changed>500,"Warped/serrated ownership is indistinguishable from the plain power diagram");
    }
    private static void counterfactualChecks()throws Exception {
        Params params=TectonicTerrain.defaultPlateParams();Settings d=Settings.defaults();
        var world=new TectonicTerrain(42,params,d);
        var flat=new TectonicTerrain(42,params,new Settings(d.coastBlendPermille(),d.seaThreshold(),d.landHeight(),d.oceanDepth(),
            d.forcingPermille(),d.detailHeight(),d.seaLevel(),d.plateWarpPermille(),d.plateRoughnessPermille(),0,0));
        var baseOnly=new TectonicTerrain(42,params,new Settings(d.coastBlendPermille(),d.seaThreshold(),d.landHeight(),d.oceanDepth(),
            0,0,d.seaLevel(),d.plateWarpPermille(),d.plateRoughnessPermille(),d.plateRelief(),d.plateTilt()));
        var highSea=new TectonicTerrain(42,params,new Settings(d.coastBlendPermille(),d.seaThreshold(),d.landHeight(),d.oceanDepth(),
            d.forcingPermille(),d.detailHeight(),200,d.plateWarpPermille(),d.plateRoughnessPermille(),d.plateRelief(),d.plateTilt()));
        int changed=0;List<Callable<Sample>> jobs=new ArrayList<>();
        for(int z=-12;z<12;z++)for(int x=-12;x<12;x++) {
            long wx=x*32768L,wz=z*32768L;Sample a=world.sample(wx,wz),b=flat.sample(wx,wz),c=baseOnly.sample(wx,wz),h=highSea.sample(wx,wz);
            check(a.owner().id()==b.owner().id()&&a.crustFraction()==b.crustFraction(),"Plate relief fed back into partition/crust");
            if(a.baseElevation()!=b.baseElevation())changed++;
            check(c.elevation()==c.baseElevation()&&c.detail()==0&&c.positiveForcing()==0&&c.negativeForcing()==0,"Zero detail/forcing ignored");
            check(a.elevation()==h.elevation()&&(!h.land()||a.land()),"Sea level changed the bed or created land");
            check(a.equals(world.sample(wx,wz)),"Repeated sample changed");if((x+z)%11==0)jobs.add(()->world.sample(wx,wz));
        }
        check(changed>300,"Plate datum/tilt do not affect broad terrain");
        try(var pool=Executors.newFixedThreadPool(4)){var results=pool.invokeAll(jobs);for(int i=0;i<jobs.size();i++)check(results.get(i).get().equals(jobs.get(i).call()),"Concurrent sample changed");}
        rejects(()->new Settings(39,500,1800,4200,1000,180,0,180,80,450,600));
        rejects(()->new IrregularPlates(42,params,301,80));rejects(()->world.sample(Long.MAX_VALUE,0));
    }

    private static void barrierChecks() {
        Params p=new Params(Map.of("plateSpacing",8192.0,"plateSpeed",128.0,"continentalPercent",100.0));
        Settings extreme=new Settings(300,200,6000,100,3000,1000,-2000,300,200,1200,1200);
        var world=new TectonicTerrain(42,p,extreme);int crossings=0;
        // Locate ownership transitions independently, then inspect actual bed through
        // each transition at one-block resolution, under the most land-favoring controls.
        for(int z=-8;z<=8;z++)for(int x=-40;x<40;x++) {
            long ax=x*1024L,bx=ax+1024,wz=z*8192L+317;
            Sample a=world.sample(ax,wz),b=world.sample(bx,wz);
            if(a.continentId()==b.continentId())continue;
            long id=a.continentId();
            while(bx-ax>1){long mid=(ax+bx)>>1;if(world.sample(mid,wz).continentId()==id)ax=mid;else bx=mid;}
            a=world.sample(ax,wz);b=world.sample(bx,wz);
            check(!a.land()&&!b.land()&&a.elevation()==-4000&&b.elevation()==-4000,"Family divide flooded upward under extreme parameters");crossings++;
        }
        check(crossings>100,"Too few independently located family crossings");
    }

    private static Audit audit(long seed) {
        long start=System.nanoTime();var world=new TectonicTerrain(seed,TectonicTerrain.defaultPlateParams(),Settings.defaults());int spacing=world.plateParams.integer("plateSpacing");
        Sample[] samples=new Sample[SIDE*SIDE];Map<Long,int[]> plateState=new HashMap<>();var continentSizes=new HashMap<Long,Integer>();int land=0;
        for(int p=0;p<samples.length;p++) {
            int px=p%SIDE,pz=p/SIDE;long wx=-6L*spacing+px*(spacing/16L),wz=-6L*spacing+pz*(spacing/16L);Sample s=world.sample(wx,wz);samples[p]=s;if(s.land())land++;
            int[] counts=plateState.computeIfAbsent(s.owner().id(),k->new int[2]);counts[s.land()?1:0]++;
            continentSizes.put(s.continentId(),s.continentPlateCount());
            if(px>0&&s.land()&&samples[p-1].land()&&s.continentId()!=samples[p-1].continentId())
                check(separated(world,wx-spacing/16L,wz,wx,wz),"No sampled water saddle between neighboring continent families");
            if(pz>0&&s.land()&&samples[p-SIDE].land()&&s.continentId()!=samples[p-SIDE].continentId())
                check(separated(world,wx,wz-spacing/16L,wx,wz),"No sampled water saddle between neighboring continent families");
        }
        int wide=0;for(int z=0;z<128;z++)for(int x=0;x<128;x++) {
            long ox=Math.floorMod(Hash64.hash(7781,0,x,z),spacing),oz=Math.floorMod(Hash64.hash(7781,1,x,z),spacing);
            if(world.sample((x-64L)*spacing+ox,(z-64L)*spacing+oz).land())wide++;
        }
        int well=0,mixed=0;for(int[] counts:plateState.values())if(counts[0]+counts[1]>=16){well++;if(counts[0]>0&&counts[1]>0)mixed++;}
        int singleton=(int)continentSizes.values().stream().filter(v->v==1).count(),multi=continentSizes.size()-singleton;
        return new Audit(seed,samples,100.0*land/samples.length,100.0*wide/(128*128),plateState.size(),mixed,well,singleton,multi,
            components(samples,true,world,spacing),components(samples,false,world,spacing),(System.nanoTime()-start)/1e6);
    }
    private static boolean separated(TectonicTerrain world,long x0,long z0,long x1,long z1) {
        for(int k=1;k<8;k++)if(!world.sample(x0+(x1-x0)*k/8,z0+(z1-z0)*k/8).land())return true;return false;
    }
    private static Components components(Sample[] samples,boolean land) {
        return components(samples,land,null,0);
    }
    private static Components components(Sample[] samples,boolean land,TectonicTerrain world,int spacing) {
        boolean[] seen=new boolean[samples.length];int[] queue=new int[samples.length];int count=0,largest=0,plates=0,cuts=0;
        for(int p=0;p<samples.length;p++)if(!seen[p]&&samples[p].land()==land) {
            int head=0,tail=1;boolean cut=false;queue[0]=p;seen[p]=true;var owners=new HashSet<Long>();long continent=samples[p].continentId();
            while(head<tail){int q=queue[head++],x=q%SIDE,z=q/SIDE;owners.add(samples[q].owner().id());cut|=x==0||z==0||x==SIDE-1||z==SIDE-1;
                for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)if(dx!=0||dz!=0){int nx=x+dx,nz=z+dz;if(nx<0||nz<0||nx>=SIDE||nz>=SIDE)continue;int r=nz*SIDE+nx;
                    if(!seen[r]&&samples[r].land()==land){
                        if(land&&samples[r].continentId()!=samples[q].continentId()) {
                            // Coarse pixels may straddle a narrow strait. Refine the physical
                            // segment; never treat a differing ID as an invisible wall.
                            check(world!=null&&separated(world,-6L*spacing+x*(spacing/16L),-6L*spacing+z*(spacing/16L),
                                -6L*spacing+nx*(spacing/16L),-6L*spacing+nz*(spacing/16L)),"Land connects across continental families");
                            continue;
                        }
                        seen[r]=true;queue[tail++]=r;
                    }}
            }
            count++;if(cut)cuts++;if(tail>largest){largest=tail;plates=owners.size();}
        }
        return new Components(count,largest,plates,cuts);
    }
    private static void componentFixtures() {
        var owner=new genesis.oracle.BoundaryForcing.Plate(1,0,0,0,0,1,100);
        var sea=new Sample(owner,7,1,0,-10,0,0,0,0,-10,false,1,1,1000,0,0,0,false,10);
        var land=new Sample(owner,7,1,1,10,0,0,0,0,10,true,1,1,1000,0,0,0,false,10);
        Sample[] grid=new Sample[SIDE*SIDE];Arrays.fill(grid,sea);grid[100*SIDE+100]=land;grid[101*SIDE+101]=land;
        check(components(grid,true).equals(new Components(1,2,1,0)),"D8 diagonal fixture failed");grid[0]=land;grid[1]=land;grid[2]=land;
        check(components(grid,true).equals(new Components(2,3,1,1)),"Crop-cut fixture failed");
    }
    private static void fingerprint(MessageDigest digest,ByteBuffer bytes,Sample s) {
        bytes.clear();bytes.putLong(s.owner().id()).putLong(s.continentId()).putInt(s.continentPlateCount()).putDouble(s.crustFraction())
            .putDouble(s.baseElevation()).putDouble(s.plateSurface()).putDouble(s.detail()).putDouble(s.positiveForcing()).putDouble(s.negativeForcing())
            .putDouble(s.elevation()).putInt(s.plateScalePermille()).putDouble(s.plateDatum()).putDouble(s.plateTiltX()).putDouble(s.plateTiltZ())
            .put((byte)(s.land()?1:0)).put((byte)(s.recentFracture()?1:0));digest.update(bytes.array(),0,bytes.position());
    }

    private static void write(List<Audit> audits,int cases,double mean,String fingerprint)throws Exception {
        StringBuilder json=new StringBuilder("{\n  \"experiment\":\"tectonic-terrain-v2\",\n  \"status\":\"sparse irregular plates and finite continental-crust groups; water terminals unresolved\",\n")
            .append("  \"preset\":{\"plateSpacing\":524288,\"continentalPercent\":53,\"plateWarpPermille\":220,\"plateRoughnessPermille\":140,\"seaLevel\":0},\n")
            .append("  \"construction\":{\"maximumPlatesPerContinentalGroup\":4,\"singletonsDominant\":true,\"nominalPlateDensityVsV1\":0.015625},\n")
            .append("  \"supportCases\":").append(cases).append(",\n  \"fingerprint\":\"").append(fingerprint).append("\",\n  \"sampledMeanLandPercent\":").append(mean)
            .append(",\n  \"sampling\":\"Morphology: 193x193 over 12S. Area: 16384 fixed jittered strata over 128S. No viewport normalization.\",\n  \"audits\":[\n");
        for(int i=0;i<audits.size();i++){Audit a=audits.get(i);if(i>0)json.append(",\n");json.append("    {\"seed\":\"").append(a.seed).append("\",\"localLandPercent\":").append(a.localLand).append(",\"wideLandPercent\":").append(a.wideLand)
            .append(",\"visiblePlates\":").append(a.visiblePlates).append(",\"mixedWellSampledPlates\":").append(a.mixedPlates).append(",\"wellSampledPlates\":").append(a.wellSampledPlates)
            .append(",\"singletonContinents\":").append(a.singletonContinents).append(",\"multiPlateContinents\":").append(a.multiPlateContinents)
            .append(",\"largestLandmassPlateCount\":").append(a.land.largestPlates).append(",\"landComponents\":").append(a.land.count).append(",\"waterComponents\":").append(a.water.count)
            .append(",\"auditMs\":").append(a.millis).append('}');}
        Files.createDirectories(Path.of("build/gallery"));Files.writeString(Path.of("build/tectonic-terrain.json"),json.append("\n  ]\n}\n").toString());
    }
    private static BufferedImage map(Sample[] samples,int layer) {
        var image=new BufferedImage(SIDE,SIDE,BufferedImage.TYPE_INT_RGB);for(int p=0;p<samples.length;p++){Sample s=samples[p];Color c=switch(layer){
            case 0->height(s.elevation());case 1->idColor(s.owner().id());case 2->idColor(s.continentId());case 3->blend(new Color(35,74,114),new Color(191,160,103),s.crustFraction());
            case 4->signed(s.plateSurface(),1400);default->blend(new Color(223,232,227),new Color(112,69,134),(s.plateScalePermille()-600)/1200.0);};image.setRGB(p%SIDE,p/SIDE,c.getRGB());}return image;
    }
    private static void render(List<Audit> audits)throws Exception {
        var image=new BufferedImage(1200,720,BufferedImage.TYPE_INT_RGB);var g=canvas(image,"Sparse tectonic terrain v2","Six seeds | each crop spans 12 plate spacings | fixed 53% preset, no viewport normalization");
        for(int i=0;i<audits.size();i++){Audit a=audits.get(i);int ox=20+i%3*395,oy=85+i/3*300;g.drawString("Seed "+a.seed+String.format(Locale.ROOT," | %.2f%% land | %d plates",a.localLand,a.visiblePlates),ox,oy);g.drawImage(map(a.samples,0),ox,oy+10,280,280,null);}
        g.drawString("Land components are separated continental-crust families of 1-4 plates. Low bed is not yet certified ocean.",20,704);g.dispose();ImageIO.write(image,"png",Path.of("build/gallery/tectonic-terrain.png").toFile());
    }
    private static void renderArchitecture()throws Exception {
        var world=new TectonicTerrain(42,TectonicTerrain.defaultPlateParams(),Settings.defaults());Sample[] samples=new Sample[SIDE*SIDE];
        for(int p=0;p<samples.length;p++)samples[p]=world.sample(-786432+p%SIDE*8192L,-786432+p/SIDE*8192L);
        var image=new BufferedImage(1200,860,BufferedImage.TYPE_INT_RGB);var g=canvas(image,"Plate architecture and terrain use the same world coordinates","Seed 42 | three plate spacings, matching the viewer overview | sparse plates with shared motion, datum and tilt");
        String[] labels={"Composed elevation","Irregular plate identity","Continental group identity (1-4 plates)","Continental crust fraction","Joined plate datum + tilt","Seeded relative plate scale"};
        for(int k=0;k<6;k++){int ox=20+k%3*390,oy=85+k/3*375;g.drawString(labels[k],ox,oy);g.drawImage(map(samples,k),ox,oy+10,350,350,null);}
        g.drawString("Plate borders are warped power-cell edges. Continents are separate crust bodies; a plate can contain both continental and oceanic terrain.",20,842);g.dispose();ImageIO.write(image,"png",Path.of("build/gallery/tectonic-plate-architecture.png").toFile());
    }
    private static Graphics2D canvas(BufferedImage image,String title,String subtitle){var g=image.createGraphics();g.setColor(new Color(246,248,250));g.fillRect(0,0,image.getWidth(),image.getHeight());g.setColor(new Color(25,34,46));g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,21));g.drawString(title,20,30);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));g.drawString(subtitle,20,55);return g;}
    private static Color height(double h){if(h<=0)return blend(new Color(105,177,189),new Color(16,44,76),Math.min(1,-h/4500));if(h<600)return blend(new Color(183,187,126),new Color(104,142,82),h/600);if(h<2000)return blend(new Color(104,142,82),new Color(147,125,100),(h-600)/1400);return blend(new Color(147,125,100),new Color(240,242,240),Math.min(1,(h-2000)/1800));}
    private static Color idColor(long id){long h=Hash64.mix(id);return new Color(70+(int)(h&127),70+(int)((h>>>8)&127),70+(int)((h>>>16)&127));}
    private static Color signed(double value,double scale){return blend(new Color(237,238,231),value>=0?new Color(175,67,40):new Color(38,101,171),Math.min(1,Math.abs(value)/scale));}
    private static Color blend(Color a,Color b,double t){t=Math.max(0,Math.min(1,t));return new Color((int)(a.getRed()+(b.getRed()-a.getRed())*t),(int)(a.getGreen()+(b.getGreen()-a.getGreen())*t),(int)(a.getBlue()+(b.getBlue()-a.getBlue())*t));}
}
