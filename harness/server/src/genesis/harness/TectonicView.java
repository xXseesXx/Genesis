package genesis.harness;

import com.sun.net.httpserver.HttpExchange;
import genesis.core.Params;
import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;
import genesis.oracle.TectonicTerrain;
import genesis.oracle.TectonicTerrain.Settings;
import genesis.oracle.TectonicTerrain.Sample;
import genesis.oracle.ContinentalHydrology;
import genesis.oracle.ContinentalHydrology.Root;
import genesis.oracle.RainfallField;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/** Separate read-only API for the Java21 tectonic candidate; never aliases production fields. */
final class TectonicView {
    static final String MODEL="tectonic-experimental";
    record Spec(String id,String label,int value,int min,int max,int step) {}
    static final List<Spec> SPECS=List.of(
        new Spec("plateSpacing","Plate spacing",524288,8192,1048576,4096),new Spec("plateJitter","Plate-site jitter %",50,0,50,1),
        new Spec("plateSpeed","Motion component limit",64,1,128,1),new Spec("transformRatio","Transform threshold %",35,0,100,1),
        new Spec("continentalPercent","Crust extent (not land %)",53,0,100,1),new Spec("crustAgeMax","Synthetic age maximum",3000,100,4500,100),
        new Spec("coastBlendPermille","Coast blend width / S",120,40,300,10),
        new Spec("seaThreshold","Base coast fraction, permille",500,200,800,10),new Spec("landHeight","Continental base height",1800,100,6000,100),
        new Spec("oceanDepth","Oceanic base depth",4200,100,10000,100),new Spec("forcingPermille","Boundary relief gain, permille",1000,0,3000,50),
        new Spec("detailHeight","Small-detail amplitude",180,0,1000,10),new Spec("seaLevel","Fixed sea-level datum",0,-2000,2000,25),
        new Spec("plateWarpPermille","Plate-edge warp / S",220,0,300,10),new Spec("plateRoughnessPermille","Plate-edge serration",140,0,200,10),
        new Spec("plateRelief","Motion-driven height gain",450,0,1200,50),new Spec("plateTilt","Motion-driven tilt gain",600,0,1200,50),
        new Spec("elevationOffset","Global bed offset (30% land preset)",-1700,-4000,0,25),
        new Spec("rainfallMm","Uniform rainfall (model mm/year)",1000,0,10000,100));
    enum Layer {
        elevation("Terrain","Absolute model height; coast at the configured sea level",-4500,4000),
        heightMap("Height + contours","High-contrast elevation relative to sea level, in model metres; contours are display-only",-6000,3000),
        riverMap("Rivers + lakes","Complete continent overflow and rainfall-weighted coarse rivers; not carved fine terrain",0,1),
        rainfall("Rainfall","Barebones uniform map, model mm/year; replaceable coordinate-local climate input",0,10000),
        runoff("River discharge","Nearest canonical node runoff, in billions of rain-mm × model-block²/year",0,100000),
        lakeDepth("Depression fill","Potential fill to the lowest spill path, model metres; assumes eventual filling",0,1000),
        waterSurface("Spill surface","Nearest canonical node's minimum overflow level, absolute model metres",-2000,4000),
        drainageStatus("Drainage status","0 unavailable/unresolved; 1 routed; 2 prescribed maritime-reserve water",0,2),
        base("Crustal base","Continental/oceanic base plus the smoothly joined plate surface",-5000,3000),
        plateSurface("Plate surface","Per-plate datum and tilt, softly joined near boundaries",-1600,1600),
        forcing("Boundary relief","Sum of positive and negative net pair contributions",-1600,1600),
        positive("Positive relief","Positive net pair contributions, not uplift per year",0,1600),
        negative("Negative relief","Negative net pair contributions, not subsidence per year",-1600,0),
        detail("Small detail","Subordinate four-octave interior detail",-180,180),
        land("Land / low bed","Tan above fixed sea level; blue is NOT certified ocean",0,1),
        crustFraction("Crust blend","Continental body support before the deep inter-family margin",0,1),
        continentId("Continent identity","Exact seeded plate-family identity; colors do not imply height",0,1),
        continentPlateCount("Plate family size","Maximum plate budget for this continental family: 1 through 4; drowned members can reduce the land-bearing count",1,4),
        plateCrust("Local plate crust","Crust at this location; plates are not globally land or ocean",0,1),
        plateId("Plate identity","Colors hash exact IDs; inspect for the full integer",0,1),
        plateScale("Plate scale","Seeded relative plate-size class in permille",600,1800),
        plateDatum("Plate datum","Motion-driven owner height before global offset; compression raises, extension lowers",-6800,6800),
        plateTiltX("Plate tilt X","Motion-driven height change across S toward +X; compressed side rises",-6800,6800),
        plateTiltZ("Plate tilt Z","Motion-driven height change across S toward +Z; compressed side rises",-6800,6800),
        recentFracture("Recent fracture","Synthetic young small-plate class; no time-evolving fracture history",0,1),
        boundaryDistance("Boundary distance","Approximate signed-score distance to the closest competing plate",0,1048576),
        velocityX("Motion X","Candidate plate velocity X in model units",-128,128),
        velocityZ("Motion Z","Candidate plate velocity Z in model units",-128,128),
        age("Crust age","Synthetic inherited plate age; not reconstructed history",0,4500),
        junctionSites("Junction support","Count of sites with positive soft junction weights",1,12),
        regime("Boundary regime","Closest-boundary diagnostic, not the composed terrain selector",0,6),
        normal("Compression","Closest-pair normal motion; positive = convergence",-256,256),
        shear("Shear","Signed closest-pair shear in canonical orientation",-256,256);
        final String label,description;final double min,max;
        Layer(String label,String description,double min,double max){this.label=label;this.description=description;this.min=min;this.max=max;}
    }
    private record HydroKey(long seed,Params params,Settings settings,int rain) {}
    private static final Map<HydroKey,ContinentalHydrology> HYDRO=new LinkedHashMap<>(4,.75f,true);
    private static synchronized ContinentalHydrology hydrology(TectonicTerrain world,int rain) {
        var key=new HydroKey(world.seed,world.plateParams,world.settings,rain);var found=HYDRO.get(key);if(found!=null)return found;
        var result=new ContinentalHydrology(world,new RainfallField.Uniform(rain));HYDRO.put(key,result);if(HYDRO.size()>2)HYDRO.remove(HYDRO.keySet().iterator().next());return result;
    }
    record Request(TectonicTerrain world,long x,long z,long step,int width,int height,Layer layer,int contourInterval,ContinentalHydrology hydro) {}
    record Raster(BufferedImage image,double landFraction,long nanos) {}
    private TectonicView() {}
    static Map<String,String> query(String raw) {
        Map<String,String> values=new LinkedHashMap<>();if(raw==null)return values;
        if(raw.length()>8192)throw new IllegalArgumentException("Query too long");
        for(String item:raw.split("&")){String[] pair=item.split("=",2);String key=URLDecoder.decode(pair[0],StandardCharsets.UTF_8),value=pair.length==2?URLDecoder.decode(pair[1],StandardCharsets.UTF_8):"";
            if(values.put(key,value)!=null)throw new IllegalArgumentException("Duplicate query key: "+key);
            if(!List.of("seed","x","z","step","width","height","layer","contourInterval").contains(key)&&SPECS.stream().noneMatch(s->s.id.equals(key)))throw new IllegalArgumentException("Unknown tectonic parameter: "+key);
        }
        return values;
    }
    static Request request(Map<String,String> query) {
        Map<String,Integer> settings=new LinkedHashMap<>();Map<String,Double> plate=new LinkedHashMap<>();
        for(Spec s:SPECS){int value=Integer.parseInt(query.getOrDefault(s.id,Integer.toString(s.value)));if(value<s.min||value>s.max)throw new IllegalArgumentException("Invalid "+s.id);settings.put(s.id,value);
            if(List.of("plateSpacing","plateJitter","plateSpeed","transformRatio","continentalPercent","crustAgeMax").contains(s.id))plate.put(s.id,(double)value);}
        var config=new Settings(settings.get("coastBlendPermille"),settings.get("seaThreshold"),settings.get("landHeight"),settings.get("oceanDepth"),
            settings.get("forcingPermille"),settings.get("detailHeight"),settings.get("seaLevel"),settings.get("plateWarpPermille"),
            settings.get("plateRoughnessPermille"),settings.get("plateRelief"),settings.get("plateTilt"),settings.get("elevationOffset"));
        long seed=number(query,"seed",42),x=number(query,"x",-786432),z=number(query,"z",-786432),step=number(query,"step",4096);
        int width=Math.toIntExact(number(query,"width",384)),height=Math.toIntExact(number(query,"height",384));
        if(width<1||height<1||width>512||height>512||step<1||step>1048576)throw new IllegalArgumentException("Dimensions 1..512; step 1..1048576 required");
        Lattice.check(x);Lattice.check(z);Lattice.check(Math.addExact(x,Math.multiplyExact(width-1L,step)));Lattice.check(Math.addExact(z,Math.multiplyExact(height-1L,step)));
        int contours=Math.toIntExact(number(query,"contourInterval",25));
        if(contours!=0&&(contours<5||contours>1000))throw new IllegalArgumentException("Contour interval: 0 (off) or 5..1000 model metres");
        var world=new TectonicTerrain(seed,new Params(plate),config);
        return new Request(world,x,z,step,width,height,Layer.valueOf(query.getOrDefault("layer","elevation")),contours,hydrology(world,settings.get("rainfallMm")));
    }
    private static long number(Map<String,String> q,String key,long fallback){return Long.parseLong(q.getOrDefault(key,Long.toString(fallback)));}
    static void handle(HttpExchange exchange)throws IOException {
        String path=exchange.getRequestURI().getPath();var query=query(exchange.getRequestURI().getRawQuery());
        if(path.equals("/api/tectonic/meta")){Server.send(exchange,200,"application/json",bytes(metadata()));return;}
        if(!path.equals("/api/tectonic/render")&&!path.equals("/api/tectonic/sample")){Server.send(exchange,404,"text/plain",bytes("Unknown tectonic endpoint"));return;}
        // Point requests have no raster extent; numeric-limit inspectors must not require a fictitious halo.
        if(path.endsWith("/sample")){
            if(!query.getOrDefault("width","1").equals("1")||!query.getOrDefault("height","1").equals("1"))throw new IllegalArgumentException("Point inspection accepts only unit dimensions");
            query.put("width","1");query.put("height","1");
        }
        Request request=request(query);
        if(path.endsWith("/sample")){Server.send(exchange,200,"application/json",bytes(sampleJson(request)));return;}
        var result=render(request);var out=new ByteArrayOutputStream();ImageIO.write(result.image,"png",out);
        exchange.getResponseHeaders().set("X-World-Model",MODEL);exchange.getResponseHeaders().set("X-World-Version",TectonicTerrain.VERSION);
        exchange.getResponseHeaders().set("X-Render-Ms",Double.toString(result.nanos/1e6));exchange.getResponseHeaders().set("X-Land-Fraction",Double.toString(result.landFraction));
        exchange.getResponseHeaders().set("X-Contour-Interval",Integer.toString(contourInterval(request)));
        exchange.getResponseHeaders().set("X-Hydrology-Version",ContinentalHydrology.VERSION);
        Server.send(exchange,200,"image/png",out.toByteArray());
    }
    static Raster render(Request r) {
        long start=System.nanoTime();var image=new BufferedImage(r.width,r.height,BufferedImage.TYPE_INT_RGB);int land=0;
        int interval=contourInterval(r);double[][] heights=interval>0?new double[r.height+1][r.width+1]:null;
        Sample[] samples=hydroLayer(r.layer)?new Sample[r.width*r.height]:null;
        long[] familyIds=samples==null?null:new long[samples.length];
        Map<Long,Root> roots=new LinkedHashMap<>();
        if(samples!=null) {
            var groups=new LinkedHashMap<Long,genesis.oracle.ContinentalGroups.Group>();long vertices=0;
            // Preflight bounds/work before any expensive full-continent solve. No partial roots.
            for(int z=0;z<r.height;z++)for(int x=0;x<r.width;x++) {
                var s=r.world.sample(r.x+x*r.step,r.z+z*r.step);samples[z*r.width+x]=s;
                Sample node=canonicalSample(r,r.x+x*r.step,r.z+z*r.step,s);var g=r.hydro.group(node);familyIds[z*r.width+x]=g.id();
                if(groups.containsKey(g.id()))continue;groups.put(g.id(),g);
                try{var b=r.hydro.bounds(g);vertices+=b.width()*(long)b.height();}catch(IllegalArgumentException unsupported){roots.put(g.id(),null);}
                if(groups.size()>24||vertices>8_000_000)throw new IllegalArgumentException("Hydrology view spans too many complete continents; zoom in (24 families / 8 million support vertices maximum)");
            }
            for(var g:groups.values())if(!roots.containsKey(g.id()))roots.put(g.id(),r.hydro.root(g));
        }
        for(int z=0;z<r.height;z++)for(int x=0;x<r.width;x++) {
            long wx=r.x+x*r.step,wz=r.z+z*r.step;var s=samples==null?r.world.sample(wx,wz):samples[z*r.width+x];if(s.land())land++;
            if(samples!=null){image.setRGB(x,z,hydroColor(r,s,roots.get(familyIds[z*r.width+x]),wx,wz).getRGB());continue;}
            if(r.layer==Layer.rainfall){image.setRGB(x,z,color(r.layer,r.hydro.rain(wx,wz),0,0).getRGB());continue;}
            double value=value(r.world,s,r.layer,wx,wz);long id=r.layer==Layer.continentId?s.continentId():s.owner().id();
            image.setRGB(x,z,color(r.layer,value,r.world.settings.seaLevel(),id).getRGB());
            if(heights!=null)heights[z][x]=s.elevation();
        }
        if(heights!=null) {
            // Explicit world-coordinate halo: no canvas-edge contours or crop normalization.
            for(int z=0;z<r.height;z++)heights[z][r.width]=r.world.sample(Math.min(Lattice.MAX_COORDINATE,r.x+r.width*r.step),r.z+z*r.step).elevation();
            for(int x=0;x<r.width;x++)heights[r.height][x]=r.world.sample(r.x+x*r.step,Math.min(Lattice.MAX_COORDINATE,r.z+r.height*r.step)).elevation();
            for(int z=0;z<r.height;z++)for(int x=0;x<r.width;x++) {
                double h=heights[z][x]-r.world.settings.seaLevel(),gradient=StrictMath.hypot(heights[z][x+1]-heights[z][x],heights[z+1][x]-heights[z][x]);
                image.setRGB(x,z,contourColor(new Color(image.getRGB(x,z)),h,gradient,interval).getRGB());
            }
        }
        return new Raster(image,land/(double)(r.width*r.height),System.nanoTime()-start);
    }
    static boolean hydroLayer(Layer layer){return switch(layer){case riverMap,runoff,lakeDepth,waterSurface,drainageStatus->true;default->false;};}
    private static Sample canonicalSample(Request r,long x,long z,Sample fallback) {
        long nx=r.hydro.snap(x),nz=r.hydro.snap(z);
        if(Math.abs(nx)>Lattice.MAX_COORDINATE||Math.abs(nz)>Lattice.MAX_COORDINATE)return fallback;
        return nx==x&&nz==z?fallback:r.world.sample(nx,nz);
    }
    private static double hydroValue(Root root,int p,Layer layer) {
        if(root==null||root.status(p)==0)return 0;
        return switch(layer){case riverMap->root.flux(p)>0?1:0;case runoff->root.flux(p)/1e9;case lakeDepth->root.lakeDepth(p);case waterSurface->root.filled(p)/1000.0;case drainageStatus->root.status(p);default->throw new IllegalArgumentException("Not a drainage field");};
    }
    private static Color hydroColor(Request r,Sample s,Root root,long x,long z) {
        int p=root==null?-1:root.index(x,z);if(root==null||root.status(p)==0)return new Color(184,96,159);
        if(r.layer!=Layer.riverMap)return color(r.layer,hydroValue(root,p,r.layer),r.world.settings.seaLevel(),0);
        Color base=color(Layer.elevation,s.elevation(),r.world.settings.seaLevel(),0);
        if(root.status(p)==2&&s.elevation()<=r.world.settings.seaLevel())return base;
        if(root.lakeDepth(p)>.05&&root.flux(p)>0&&s.elevation()<root.filled(p)/1000.0)base=blend(new Color(64,157,181),new Color(24,90,143),root.lakeDepth(p)/250);
        var river=root.river(x,z);
        if(river.flux()>0) {
            double relative=river.flux()/(16_000.0*root.step*root.step),width=root.step*Math.min(.48,.18+.045*StrictMath.log(relative)/StrictMath.log(2));
            double alpha=Math.max(0,Math.min(1,(width-river.distance())/(root.step*.12)+.5));
            base=blend(base,new Color(13,92,157),alpha);
        }
        return base;
    }
    static int contourInterval(Request r) {
        if(r.layer!=Layer.heightMap||r.contourInterval==0)return 0;
        int interval=r.contourInterval;
        // Deterministic zoom LOD, never a statistic of the displayed crop.
        while(interval<r.step*8000.0/r.world.plateParams.integer("plateSpacing"))interval*=2;
        return interval;
    }
    static Color contourColor(Color color,double height,double gradient,int interval) {
        if(gradient<1e-9||interval==0)return color;
        double nearest=StrictMath.rint(height/interval),distance=Math.abs(height-nearest*interval)/gradient;
        boolean major=Math.floorMod((long)nearest,5)==0;
        // Suppress unresolved lines on steep faces instead of blacking out the map.
        if(gradient>interval*.8)return color;
        double alpha=Math.max(0,Math.min(1,(major?.9:.65)-distance))*(major?.9:.65);
        return blend(color,new Color(24,22,41),alpha);
    }
    static double value(TectonicTerrain w,Sample s,Layer layer,long x,long z) {
        return switch(layer){case elevation,heightMap->s.elevation();case base->s.baseElevation();case plateSurface->s.plateSurface();case detail->s.detail();case forcing->s.positiveForcing()+s.negativeForcing();
            case positive->s.positiveForcing();case negative->s.negativeForcing();case land->s.land()?1:0;case crustFraction->s.crustFraction();case plateCrust->s.owner().crust();
            case continentId,plateId->0;case continentPlateCount->s.continentPlateCount();case plateScale->s.plateScalePermille();case plateDatum->s.plateDatum();
            case plateTiltX->s.plateTiltX();case plateTiltZ->s.plateTiltZ();case recentFracture->s.recentFracture()?1:0;case boundaryDistance->s.boundaryDistance();
            case velocityX->s.owner().vx();case velocityZ->s.owner().vz();case age->s.owner().age();case junctionSites->s.junctionSites();
            case regime->w.boundary(x,z).edge().regime().ordinal();case normal->w.boundary(x,z).edge().normalQ()/1024.0;case shear->w.boundary(x,z).edge().shearQ()/1024.0;
            case riverMap,rainfall,runoff,lakeDepth,waterSurface,drainageStatus->throw new IllegalArgumentException("Drainage fields require the complete hydrology context");};
    }
    static Color color(Layer layer,double value,int seaLevel,long id) {
        if(layer==Layer.rainfall)return blend(new Color(235,223,172),new Color(31,117,176),value/2500);
        if(layer==Layer.runoff)return blend(new Color(241,236,215),new Color(12,75,159),StrictMath.log1p(value)/StrictMath.log(10001));
        if(layer==Layer.lakeDepth)return blend(new Color(240,234,210),new Color(29,112,174),StrictMath.log1p(value)/StrictMath.log(501));
        if(layer==Layer.waterSurface)return color(Layer.heightMap,value,seaLevel,0);
        if(layer==Layer.drainageStatus)return value==0?new Color(184,96,159):value==2?new Color(30,93,147):new Color(114,160,101);
        if(layer==Layer.heightMap) {
            double h=value-seaLevel;
            double[] stops={-6000,-3000,-1000,-200,0,100,300,600,1000,1800,3000};
            int[] colors={0x11183f,0x254f9d,0x187ab5,0x56c9ce,0xb5eee0,0x4caa66,0xa4c75a,0xead369,0xe79848,0xb55358,0xf5dced};
            if(h<stops[0])return new Color(colors[0]);
            for(int k=1;k<stops.length;k++)if(h<stops[k])return blend(new Color(colors[k-1]),new Color(colors[k]),(h-stops[k-1])/(stops[k]-stops[k-1]));
            return new Color(colors[colors.length-1]);
        }
        if(layer==Layer.plateId||layer==Layer.continentId){long h=Hash64.mix(id);return new Color(70+(int)(h&127),70+(int)((h>>>8)&127),70+(int)((h>>>16)&127));}
        if(layer==Layer.regime)return new Color[]{new Color(175,183,190),new Color(164,94,57),new Color(148,78,141),new Color(78,154,148),new Color(73,123,179),new Color(125,169,101),new Color(191,161,77)}[(int)value];
        if(layer==Layer.elevation||layer==Layer.base) {
            double h=value-seaLevel;if(h<=0)return blend(new Color(105,177,189),new Color(16,44,76),-h/4500);
            if(h<600)return blend(new Color(183,187,126),new Color(104,142,82),h/600);
            if(h<2000)return blend(new Color(104,142,82),new Color(147,125,100),(h-600)/1400);
            return blend(new Color(147,125,100),new Color(240,242,240),(h-2000)/1800);
        }
        if(layer==Layer.crustFraction||layer==Layer.plateCrust||layer==Layer.land||layer==Layer.recentFracture)return blend(new Color(35,74,114),new Color(191,160,103),value);
        if(layer==Layer.age||layer==Layer.junctionSites||layer==Layer.continentPlateCount||layer==Layer.plateScale||layer==Layer.boundaryDistance)
            return blend(new Color(223,232,227),new Color(112,69,134),(value-layer.min)/(layer.max-layer.min));
        return blend(new Color(237,238,231),value>=0?new Color(175,67,40):new Color(38,101,171),Math.abs(value)/Math.max(Math.abs(layer.min),Math.abs(layer.max)));
    }
    private static Color blend(Color a,Color b,double t){t=Math.max(0,Math.min(1,t));return new Color((int)(a.getRed()+(b.getRed()-a.getRed())*t),(int)(a.getGreen()+(b.getGreen()-a.getGreen())*t),(int)(a.getBlue()+(b.getBlue()-a.getBlue())*t));}
    static String sampleJson(Request r) {
        var s=r.world.sample(r.x,r.z);var boundary=r.world.boundary(r.x,r.z);var edge=boundary.edge();
        Root root=null;try{root=r.hydro.root(r.hydro.group(canonicalSample(r,r.x,r.z,s)));}catch(IllegalArgumentException unsupported){/* complete support unavailable near numeric guard */}
        int node=root==null?-1:root.index(r.x,r.z);int status=root==null?0:root.status(node);
        StringBuilder out=new StringBuilder("{\"model\":").append(Server.quote(MODEL)).append(",\"version\":").append(Server.quote(TectonicTerrain.VERSION))
            .append(",\"hydrologyVersion\":").append(Server.quote(ContinentalHydrology.VERSION))
            .append(",\"seed\":").append(Server.quote(Long.toString(r.world.seed))).append(",\"x\":").append(Server.quote(Long.toString(r.x))).append(",\"z\":").append(Server.quote(Long.toString(r.z))).append(",\"fields\":{");
        for(var layer:Layer.values()){if(out.charAt(out.length()-1)!='{')out.append(',');out.append(Server.quote(layer.name())).append(':');
            if(layer==Layer.plateId)out.append(Server.quote(Long.toString(s.owner().id())));else if(layer==Layer.continentId)out.append(Server.quote(Long.toString(s.continentId())));
            else if(layer==Layer.regime)out.append(Server.quote(edge.regime().name()));
            else if(layer==Layer.rainfall)out.append(r.hydro.rain(r.x,r.z));
            else if(hydroLayer(layer)){if(status==0&&layer==Layer.waterSurface)out.append("null");else out.append(hydroValue(root,node,layer));}
            else out.append(value(r.world,s,layer,r.x,r.z));}
        out.append("},\"hydrology\":{\"status\":").append(status).append(",\"step\":").append(r.hydro.step);
        if(root!=null&&root.active(node)) {
            int q=root.downstream(node);out.append(",\"familyId\":").append(Server.quote(Long.toString(root.group.id())))
                .append(",\"nodeX\":").append(Server.quote(Long.toString(root.x(node)))).append(",\"nodeZ\":").append(Server.quote(Long.toString(root.z(node))))
                .append(",\"source\":").append(Server.quote(Long.toString(root.source(node)))).append(",\"flux\":").append(Server.quote(Long.toString(root.flux(node))))
                .append(",\"supplied\":").append(Server.quote(Long.toString(root.supplied()))).append(",\"discharged\":").append(Server.quote(Long.toString(root.discharged())))
                .append(",\"unresolved\":").append(Server.quote(Long.toString(root.unresolved()))).append(",\"activeCells\":").append(root.activeCells)
                .append(",\"downstream\":");
            if(q<0)out.append("null");else out.append("{\"x\":").append(Server.quote(Long.toString(root.x(q)))).append(",\"z\":").append(Server.quote(Long.toString(root.z(q)))).append('}');
        }
        return out.append("},\"closestBoundary\":{\"first\":").append(Server.quote(Long.toString(edge.first().id()))).append(",\"second\":").append(Server.quote(Long.toString(edge.second().id())))
            .append(",\"descendingSide\":").append(edge.descendingSide()).append("},\"waterStatus\":\"complete-continent coarse overflow to prescribed maritime reserve; no fine channel realization\"}").toString();
    }
    static String metadata() {
        StringBuilder out=new StringBuilder("{\"model\":").append(Server.quote(MODEL)).append(",\"version\":").append(Server.quote(TectonicTerrain.VERSION))
            .append(",\"hydrologyVersion\":").append(Server.quote(ContinentalHydrology.VERSION)).append(",\"params\":[");
        for(var s:SPECS){if(out.charAt(out.length()-1)!='[')out.append(',');out.append("{\"id\":").append(Server.quote(s.id)).append(",\"label\":").append(Server.quote(s.label)).append(",\"default\":").append(s.value)
            .append(",\"min\":").append(s.min).append(",\"max\":").append(s.max).append(",\"step\":").append(s.step).append('}');}
        out.append("],\"fields\":[");for(var layer:Layer.values()){if(out.charAt(out.length()-1)!='[')out.append(',');out.append("{\"id\":").append(Server.quote(layer.name())).append(",\"label\":").append(Server.quote(layer.label))
            .append(",\"description\":").append(Server.quote(layer.description)).append(",\"min\":").append(layer.min).append(",\"max\":").append(layer.max).append('}');}
        return out.append("]}").toString();
    }
    private static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
}
