package genesis.harness;

import com.sun.net.httpserver.HttpExchange;
import genesis.core.Params;
import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;
import genesis.oracle.TectonicTerrain;
import genesis.oracle.TectonicTerrain.Settings;
import genesis.oracle.TectonicTerrain.Sample;
import genesis.oracle.ClimateField;
import genesis.oracle.ContinentalHydrology;
import genesis.oracle.ContinentalHydrology.Root;
import genesis.oracle.FluvialNetwork;
import genesis.oracle.TerrainSubstrate;
import genesis.oracle.WindField;
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
    static final String WATER_STATUS="bounded fine channel corridors and connected-lake masks over complete-continent routing; fine-grid downhill/cross-divide proof and transient storage absent";
    /** Per-request cold-work guard; 4M vertices retain about 322 MB at 80.5 B/vertex. */
    static final int MAX_HYDRO_FAMILIES=20;
    static final long MAX_HYDRO_VERTICES=4_000_000;
    static final long MAX_HYDRO_ESTIMATED_BYTES=322_000_000;
    record HydroBudget(int families,long vertices) {}
    record Spec(String id,String label,int value,int min,int max,int step) {}
    static final List<Spec> SPECS=List.of(
        new Spec("size","Native upscale (1 = 256 high)",1,1,4,1),new Spec("plateSpacing","Base plate spacing at size 1",2048,2048,262144,1024),new Spec("plateJitter","Plate-site jitter %",50,0,50,1),
        new Spec("plateSpeed","Motion component limit",64,1,128,1),new Spec("transformRatio","Transform threshold %",35,0,100,1),
        new Spec("continentalPercent","Crust extent (not land %)",53,0,100,1),new Spec("crustAgeMax","Synthetic age maximum",3000,100,4500,100),
        new Spec("coastBlendPermille","Coast blend width / S",120,40,300,10),
        new Spec("seaThreshold","Base coast fraction, permille",500,200,800,10),new Spec("landHeight","Continental relief, size-1 blocks",56,4,192,1),
        new Spec("oceanDepth","Oceanic relief, size-1 blocks",131,4,255,1),new Spec("forcingPermille","Boundary relief gain, permille",1000,0,3000,50),
        new Spec("detailHeight","Small-detail amplitude, blocks",6,0,64,1),new Spec("seaLevel","Size-1 sea Y",63,1,254,1),
        new Spec("plateWarpPermille","Plate-edge warp / S",220,0,300,10),new Spec("plateRoughnessPermille","Plate-edge serration",140,0,200,10),
        new Spec("plateRelief","Motion-driven datum, blocks",14,0,96,1),new Spec("plateTilt","Motion-driven tilt, blocks",19,0,96,1),
        new Spec("elevationOffset","Relative bed offset, blocks",-53,-192,0,1),
        new Spec("rainfallMm","Mean precipitation scale (model mm/year)",1000,0,10000,100),
        new Spec("erosionStrength","Erosion exposure, permille",700,0,3000,50));
    enum Layer {
        erodedTerrain("Eroded terrain","Rainfall-driven bedrock incision per complete continent; interpolated coarse erosion depth",0,255),
        erosionDepth("Erosion depth","Removed bedrock at the nearest canonical node, blocks",0,64),
        hardness("Terrain hardness","Exposed substrate resistance: 0 soft, 1 resistant",0,1),
        elevation("Before erosion","Original tectonic surface Y before hydraulic erosion",0,255),
        heightMap("Height + contours","Original tectonic surface before erosion, with display-only contours",0,255),
        riverMap("Rivers + lakes","Curved discharge-sized channel threads, bankfull corridors and connected flat-surface lake masks; coarse zoom adds a display-only pixel-footprint cue",0,1),
        windDirection("Wind direction","Continuous divergence-free flow angle from +X toward +Z, degrees",-180,180),
        windSpeed("Wind speed","Continuous non-stagnating model wind magnitude",.6,1.4),
        humidity("Humidity","Steady advected atmospheric moisture fraction",0,1),
        rainfall("Rainfall","Terrain-aware precipitation after moisture advection and orographic lift, model mm/year",0,10000),
        soilDepth("Soil depth","Weathering- and moisture-dependent soil/regolith thickness, blocks",0,8),
        bedrockElevation("Bedrock elevation","Eroded surface minus the soil/regolith column, Y blocks",0,255),
        bedrockDepth("Bedrock depth","Depth from ground surface to coherent parent rock, blocks",0,8),
        rockType("Rock type","0 basalt / 1 granite / 2 shale / 3 limestone / 4 sandstone",0,4),
        infiltration("Infiltration fraction","Annual precipitation fraction admitted into soil/rock storage",0,1),
        runoffFraction("Runoff fraction","Annual precipitation fraction routed over the surface",0,1),
        drainage("Ground drainage","0 well drained / 1 moderate / 2 poor",0,2),
        runoff("River discharge","Nearest canonical node effective runoff, in billions of runoff-mm × model-block²/year",0,100000),
        lakeDepth("Depression fill","Potential fill to the lowest spill path, Minecraft blocks; assumes eventual filling",0,64),
        waterSurface("Spill surface","Nearest canonical node's minimum overflow surface Y",0,255),
        drainageStatus("Drainage status","0 unavailable/unresolved; 1 routed; 2 prescribed maritime-reserve water",0,2),
        base("Crustal base","Unbounded native-block base before detail and final build-height fit",-192,255),
        plateSurface("Plate surface","Per-plate datum and tilt in native blocks, softly joined near boundaries",-96,96),
        forcing("Boundary relief","Positive plus negative boundary relief in native blocks",-96,96),
        positive("Positive relief","Positive boundary relief in native blocks, not uplift per year",0,96),
        negative("Negative relief","Negative boundary relief in native blocks, not subsidence per year",-96,0),
        detail("Small detail","Subordinate four-octave native-block detail",-64,64),
        land("Land / low bed","Tan above fixed sea level; blue is NOT certified ocean",0,1),
        crustFraction("Crust blend","Continental body support before the deep inter-family margin",0,1),
        continentId("Continent identity","Exact seeded plate-family identity; colors do not imply height",0,1),
        continentPlateCount("Plate family size","Maximum plate budget for this continental family: 1 through 4; drowned members can reduce the land-bearing count",1,4),
        plateCrust("Local plate crust","Crust at this location; plates are not globally land or ocean",0,1),
        plateId("Plate identity","Colors hash exact IDs; inspect for the full integer",0,1),
        plateScale("Plate scale","Seeded relative plate-size class in permille",600,1800),
        plateDatum("Plate datum","Motion-driven owner height in native blocks; compression raises, extension lowers",-192,192),
        plateTiltX("Plate tilt X","Native-block height change across S toward +X; compressed side rises",-192,192),
        plateTiltZ("Plate tilt Z","Native-block height change across S toward +Z; compressed side rises",-192,192),
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
    private record HydroKey(long seed,Params params,Settings settings,int rain,int erosion,
                            String terrainVersion,String hydrologyVersion,String fluvialVersion,
                            String climateVersion,String windVersion,String substrateVersion) {}
    private static final Map<HydroKey,ContinentalHydrology> HYDRO=new LinkedHashMap<>(4,.75f,true);
    private static synchronized ContinentalHydrology hydrology(TectonicTerrain world,int rain,int erosion) {
        var key=new HydroKey(world.seed,world.plateParams,world.settings,rain,erosion,TectonicTerrain.VERSION,ContinentalHydrology.VERSION,
            FluvialNetwork.VERSION,ClimateField.VERSION,WindField.VERSION,TerrainSubstrate.VERSION);
        var found=HYDRO.get(key);if(found!=null)return found;
        var climate=new ClimateField(world.seed,world.plateParams.integer("plateSpacing"),rain);
        var result=new ContinentalHydrology(world,climate,12,erosion,TerrainSubstrate.seeded(world));HYDRO.put(key,result);if(HYDRO.size()>2)HYDRO.remove(HYDRO.keySet().iterator().next());return result;
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
            settings.get("plateRoughnessPermille"),settings.get("plateRelief"),settings.get("plateTilt"),settings.get("elevationOffset"),settings.get("size"));
        long seed=number(query,"seed",42),x=number(query,"x",-3072),z=number(query,"z",-3072),step=number(query,"step",16);
        int width=Math.toIntExact(number(query,"width",384)),height=Math.toIntExact(number(query,"height",384));
        if(width<1||height<1||step<1||step>1048576)throw new IllegalArgumentException("Dimensions must be positive; step 1..1048576 required");
        Lattice.check(x);Lattice.check(z);Lattice.check(Math.addExact(x,Math.multiplyExact(width-1L,step)));Lattice.check(Math.addExact(z,Math.multiplyExact(height-1L,step)));
        int contours=Math.toIntExact(number(query,"contourInterval",5));
        if(contours!=0&&(contours<1||contours>256))throw new IllegalArgumentException("Contour interval: 0 (off) or 1..256 blocks");
        var world=new TectonicTerrain(seed,new Params(plate),config);
        return new Request(world,x,z,step,width,height,Layer.valueOf(query.getOrDefault("layer","erodedTerrain")),contours,hydrology(world,settings.get("rainfallMm"),settings.get("erosionStrength")));
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
        exchange.getResponseHeaders().set("X-Fluvial-Version",FluvialNetwork.VERSION);
        exchange.getResponseHeaders().set("X-Climate-Version",ClimateField.VERSION);
        exchange.getResponseHeaders().set("X-Wind-Version",WindField.VERSION);
        exchange.getResponseHeaders().set("X-Substrate-Version",TerrainSubstrate.VERSION);
        exchange.getResponseHeaders().set("X-World-Height",Integer.toString(request.world.worldHeight()));exchange.getResponseHeaders().set("X-Sea-Level",Integer.toString(request.world.seaLevel()));
        exchange.getResponseHeaders().set("X-Native-Scale",Integer.toString(request.world.settings.size()));
        Server.send(exchange,200,"image/png",out.toByteArray());
    }
    static Raster render(Request r) {
        long start=System.nanoTime();var image=new BufferedImage(r.width,r.height,BufferedImage.TYPE_INT_RGB);int land=0;
        int interval=contourInterval(r);double[][] heights=interval>0?new double[r.height+1][r.width+1]:null;
        Sample[] samples=hydroLayer(r.layer)?new Sample[r.width*r.height]:null;
        long[] familyIds=samples==null?null:new long[samples.length];
        Map<Long,Root> roots=new LinkedHashMap<>();
        if(samples!=null) {
            var support=hydrologySupport(r,samples,familyIds,true);roots.putAll(support.unavailable());
            for(var g:support.groups().values())if(!roots.containsKey(g.id()))roots.put(g.id(),r.hydro.root(g));
        }
        for(int z=0;z<r.height;z++)for(int x=0;x<r.width;x++) {
            long wx=r.x+x*r.step,wz=r.z+z*r.step;var s=samples==null?r.world.sample(wx,wz):samples[z*r.width+x];if(s.land())land++;
            if(samples!=null){image.setRGB(x,z,hydroColor(r,s,roots.get(familyIds[z*r.width+x]),wx,wz).getRGB());continue;}
            if(r.layer==Layer.windDirection||r.layer==Layer.windSpeed) {
                image.setRGB(x,z,color(r.layer,windValue(r.hydro.climate.wind(wx,wz),r.layer),0,1,0).getRGB());continue;
            }
            double value=value(r.world,s,r.layer,wx,wz);long id=r.layer==Layer.continentId?s.continentId():s.owner().id();
            image.setRGB(x,z,color(r.layer,value,r.world.seaLevel(),r.world.settings.size(),id).getRGB());
            if(heights!=null)heights[z][x]=s.elevation();
        }
        if(heights!=null) {
            // Explicit world-coordinate halo: no canvas-edge contours or crop normalization.
            for(int z=0;z<r.height;z++)heights[z][r.width]=r.world.sample(Math.min(Lattice.MAX_COORDINATE,r.x+r.width*r.step),r.z+z*r.step).elevation();
            for(int x=0;x<r.width;x++)heights[r.height][x]=r.world.sample(r.x+x*r.step,Math.min(Lattice.MAX_COORDINATE,r.z+r.height*r.step)).elevation();
            for(int z=0;z<r.height;z++)for(int x=0;x<r.width;x++) {
                double h=heights[z][x]-r.world.seaLevel(),gradient=StrictMath.hypot(heights[z][x+1]-heights[z][x],heights[z+1][x]-heights[z][x]);
                image.setRGB(x,z,contourColor(new Color(image.getRGB(x,z)),h,gradient,interval).getRGB());
            }
        }
        return new Raster(image,land/(double)(r.width*r.height),System.nanoTime()-start);
    }
    private record HydroSupport(Map<Long,genesis.oracle.ContinentalGroups.Group> groups,Map<Long,Root> unavailable,long vertices) {
        HydroBudget budget(){return new HydroBudget(groups.size(),vertices);}
    }
    /** Cold complete-continent work implied by this raster, before any root is built. */
    static HydroBudget hydrologyBudget(Request r) {
        if(!hydroLayer(r.layer))return new HydroBudget(0,0);
        return hydrologySupport(r,null,null,false).budget();
    }
    static long estimatedHydrologyBytes(long vertices) {
        if(vertices<0)throw new IllegalArgumentException("Negative hydrology work");
        return Math.floorDiv(Math.addExact(Math.multiplyExact(vertices,805),9),10);
    }
    private static HydroSupport hydrologySupport(Request r,Sample[] samples,long[] familyIds,boolean enforce) {
        var groups=new LinkedHashMap<Long,genesis.oracle.ContinentalGroups.Group>();Map<Long,Root> unavailable=new LinkedHashMap<>();long vertices=0;
        // Bound all cold work before building any full continent: never return partial roots.
        // Unsupported boxes near the numeric guard stay null (pink) and consume no vertices.
        for(int z=0;z<r.height;z++)for(int x=0;x<r.width;x++) {
            int pixel=z*r.width+x;long wx=r.x+x*r.step,wz=r.z+z*r.step;var s=r.world.sample(wx,wz);
            if(samples!=null)samples[pixel]=s;
            Sample node=canonicalSample(r,wx,wz,s);var g=r.hydro.group(node);
            if(familyIds!=null)familyIds[pixel]=g.id();
            if(groups.containsKey(g.id()))continue;groups.put(g.id(),g);
            try{var b=r.hydro.bounds(g);vertices=Math.addExact(vertices,Math.multiplyExact((long)b.width(),b.height()));}catch(IllegalArgumentException unsupported){unavailable.put(g.id(),null);}
            if(enforce&&(groups.size()>MAX_HYDRO_FAMILIES||vertices>MAX_HYDRO_VERTICES||estimatedHydrologyBytes(vertices)>MAX_HYDRO_ESTIMATED_BYTES))
                throw new IllegalArgumentException("Hydrology view spans too many complete continents; zoom in ("+MAX_HYDRO_FAMILIES+" families / "+MAX_HYDRO_VERTICES+" support vertices / about "+MAX_HYDRO_ESTIMATED_BYTES+" measured root-array bytes maximum)");
        }
        return new HydroSupport(groups,unavailable,vertices);
    }
    static boolean hydroLayer(Layer layer){return switch(layer) {
        case erodedTerrain,erosionDepth,hardness,riverMap,humidity,rainfall,soilDepth,bedrockElevation,bedrockDepth,
             rockType,infiltration,runoffFraction,drainage,runoff,lakeDepth,waterSurface,drainageStatus->true;
        default->false;
    };}
    private static Sample canonicalSample(Request r,long x,long z,Sample fallback) {
        long nx=r.hydro.snap(x),nz=r.hydro.snap(z);
        if(Math.abs(nx)>Lattice.MAX_COORDINATE||Math.abs(nz)>Lattice.MAX_COORDINATE)return fallback;
        return nx==x&&nz==z?fallback:r.world.sample(nx,nz);
    }
    private static double hydroValue(Root root,int p,Layer layer) {
        if(root==null||root.status(p)==0)return 0;
        return switch(layer) {
            case erodedTerrain->root.bed(p)/1000.0;case erosionDepth->root.erosionDepth(p);case hardness->root.hardness(p);
            case riverMap->root.flux(p)>0?1:0;case humidity->root.humidity(p);case rainfall->root.rainfall(p);
            case soilDepth,bedrockDepth->root.soilDepth(p);case bedrockElevation->root.bedrockElevation(p);case rockType->root.rock(p).ordinal();
            case infiltration->root.infiltrationPermille(p)/1000.0;case runoffFraction->root.runoffPermille(p)/1000.0;case drainage->root.drainage(p).ordinal();
            case runoff->root.flux(p)/1e9;case lakeDepth->root.lakeDepth(p);case waterSurface->root.filled(p)/1000.0;case drainageStatus->root.status(p);
            default->throw new IllegalArgumentException("Not a drainage field");
        };
    }
    private static double windValue(WindField.Wind wind,Layer layer) {
        return switch(layer){case windDirection->StrictMath.toDegrees(wind.directionRadians());case windSpeed->wind.speed();default->throw new IllegalArgumentException("Not a wind field");};
    }
    private static Color hydroColor(Request r,Sample s,Root root,long x,long z) {
        int p=root==null?-1:root.index(x,z);if(root==null||root.status(p)==0)return new Color(184,96,159);
        double eroded=root.elevation(x,z,s.elevation(),r.world.seaLevel());
        if(r.layer==Layer.erodedTerrain)return color(Layer.elevation,eroded,r.world.seaLevel(),r.world.settings.size(),0);
        if(r.layer!=Layer.riverMap)return color(r.layer,hydroValue(root,p,r.layer),r.world.seaLevel(),r.world.settings.size(),0);
        Color base=color(Layer.elevation,eroded,r.world.seaLevel(),r.world.settings.size(),0);
        if(root.status(p)==2&&s.elevation()<=r.world.seaLevel())return base;
        var lake=root.lakeAt(x,z,eroded);
        if(lake!=null&&lake.wet()&&lake.lake().flux()>0) {
            double depthScale=lake.depth()/Math.max(.001,lake.lake().maxDepth());
            base=blend(new Color(72,165,188),new Color(20,82,143),.35+.55*depthScale);
        }
        var channel=root.channelAt(x,z);
        if(channel!=null) {
            if(channel.insideBankfull())base=blend(base,new Color(73,126,144),.20);
            // Cap display coverage at two canonical drainage cells. This is enough to
            // join the detail raster while keeping common world samples invariant at
            // wider zoom levels.
            double alpha=currentChannelAlpha(channel,Math.min(r.step,2L*root.step));
            if(alpha>0)base=blend(base,new Color(10,83,153),alpha);
        }
        return base;
    }
    /** Exact thread mask at fine scale; display-only pixel-footprint coverage when it is sub-pixel. */
    static double currentChannelAlpha(FluvialNetwork.Channel channel,long pixelStep) {
        if(channel==null||pixelStep<1)return 0;double width=channel.threadWidth(),radius=width/2;
        if(channel.insideCurrent()) {
            double edge=radius-channel.threadDistance();return .78+.18*Math.min(1,edge/Math.max(.25,width*.18));
        }
        if(width>=pixelStep)return 0;
        // Project an axis-aligned square pixel onto the channel normal. A centerline
        // crossing its footprint remains visible without changing the physical mask.
        double halfFootprint=pixelStep*.5*(Math.abs(channel.tangentX())+Math.abs(channel.tangentZ()));
        double reach=radius+halfFootprint;if(channel.threadDistance()>reach||halfFootprint<=0)return 0;
        double penetration=(reach-channel.threadDistance())/halfFootprint;
        return .42+.18*Math.max(0,Math.min(1,penetration));
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
            case erodedTerrain,erosionDepth,hardness,riverMap,windDirection,windSpeed,humidity,rainfall,soilDepth,bedrockElevation,bedrockDepth,
                 rockType,infiltration,runoffFraction,drainage,runoff,lakeDepth,waterSurface,drainageStatus->throw new IllegalArgumentException("Climate, ground and drainage fields require their shared context");};
    }
    static Color color(Layer layer,double value,int seaLevel,int size,long id) {
        if(layer==Layer.erosionDepth)return blend(new Color(240,234,210),new Color(170,53,29),value/(24*size));
        if(layer==Layer.hardness)return blend(new Color(227,207,139),new Color(72,65,92),value);
        if(layer==Layer.windDirection)return new Color(Color.HSBtoRGB((float)((value+180)/360.0),.68f,.88f));
        if(layer==Layer.windSpeed)return blend(new Color(229,241,235),new Color(50,83,164),(value-.6)/.8);
        if(layer==Layer.humidity)return blend(new Color(211,177,112),new Color(37,119,176),value);
        if(layer==Layer.rainfall)return blend(new Color(235,223,172),new Color(31,117,176),value/2500);
        if(layer==Layer.soilDepth||layer==Layer.bedrockDepth)return blend(new Color(222,205,169),new Color(80,67,46),value/8);
        if(layer==Layer.bedrockElevation)return color(Layer.elevation,value,seaLevel,size,0);
        if(layer==Layer.rockType)return new Color(new int[]{0x454b52,0xc2b1aa,0x777264,0xd6d2ad,0xc59254}[Math.max(0,Math.min(4,(int)value))]);
        if(layer==Layer.infiltration)return blend(new Color(181,132,75),new Color(63,151,169),value);
        if(layer==Layer.runoffFraction)return blend(new Color(231,221,177),new Color(24,91,170),value);
        if(layer==Layer.drainage)return new Color(new int[]{0xb89b52,0x75a56c,0x3d78a9}[Math.max(0,Math.min(2,(int)value))]);
        if(layer==Layer.runoff)return blend(new Color(241,236,215),new Color(12,75,159),StrictMath.log1p(value)/StrictMath.log(10001));
        if(layer==Layer.lakeDepth)return blend(new Color(240,234,210),new Color(29,112,174),StrictMath.log1p(value/size)/StrictMath.log(65));
        if(layer==Layer.waterSurface)return color(Layer.heightMap,value,seaLevel,size,0);
        if(layer==Layer.drainageStatus)return value==0?new Color(184,96,159):value==2?new Color(30,93,147):new Color(114,160,101);
        if(layer==Layer.heightMap) {
            double h=(value-seaLevel)/size;
            double[] stops={-63,-40,-24,-8,0,8,24,48,80,128,192};
            int[] colors={0x11183f,0x254f9d,0x187ab5,0x56c9ce,0xb5eee0,0x4caa66,0xa4c75a,0xead369,0xe79848,0xb55358,0xf5dced};
            if(h<stops[0])return new Color(colors[0]);
            for(int k=1;k<stops.length;k++)if(h<stops[k])return blend(new Color(colors[k-1]),new Color(colors[k]),(h-stops[k-1])/(stops[k]-stops[k-1]));
            return new Color(colors[colors.length-1]);
        }
        if(layer==Layer.plateId||layer==Layer.continentId){long h=Hash64.mix(id);return new Color(70+(int)(h&127),70+(int)((h>>>8)&127),70+(int)((h>>>16)&127));}
        if(layer==Layer.regime)return new Color[]{new Color(175,183,190),new Color(164,94,57),new Color(148,78,141),new Color(78,154,148),new Color(73,123,179),new Color(125,169,101),new Color(191,161,77)}[(int)value];
        if(layer==Layer.elevation||layer==Layer.base) {
            double h=(value-seaLevel)/size;if(h<=0)return blend(new Color(105,177,189),new Color(16,44,76),-h/63);
            if(h<32)return blend(new Color(183,187,126),new Color(104,142,82),h/32);
            if(h<96)return blend(new Color(104,142,82),new Color(147,125,100),(h-32)/64);
            return blend(new Color(147,125,100),new Color(240,242,240),(h-96)/96);
        }
        if(layer==Layer.crustFraction||layer==Layer.plateCrust||layer==Layer.land||layer==Layer.recentFracture)return blend(new Color(35,74,114),new Color(191,160,103),value);
        if(layer==Layer.age||layer==Layer.junctionSites||layer==Layer.continentPlateCount||layer==Layer.plateScale||layer==Layer.boundaryDistance) {
            double shown=layer==Layer.boundaryDistance?value/size:value;
            return blend(new Color(223,232,227),new Color(112,69,134),(shown-layer.min)/(layer.max-layer.min));
        }
        double shown=value/size;
        return blend(new Color(237,238,231),shown>=0?new Color(175,67,40):new Color(38,101,171),Math.abs(shown)/Math.max(Math.abs(layer.min),Math.abs(layer.max)));
    }
    private static Color blend(Color a,Color b,double t){t=Math.max(0,Math.min(1,t));return new Color((int)(a.getRed()+(b.getRed()-a.getRed())*t),(int)(a.getGreen()+(b.getGreen()-a.getGreen())*t),(int)(a.getBlue()+(b.getBlue()-a.getBlue())*t));}
    static String sampleJson(Request r) {
        var s=r.world.sample(r.x,r.z);var boundary=r.world.boundary(r.x,r.z);var edge=boundary.edge();
        Root root=null;try{root=r.hydro.root(r.hydro.group(canonicalSample(r,r.x,r.z,s)));}catch(IllegalArgumentException unsupported){/* complete support unavailable near numeric guard */}
        int node=root==null?-1:root.index(r.x,r.z);int status=root==null?0:root.status(node);
        StringBuilder out=new StringBuilder("{\"model\":").append(Server.quote(MODEL)).append(",\"version\":").append(Server.quote(TectonicTerrain.VERSION))
            .append(",\"hydrologyVersion\":").append(Server.quote(ContinentalHydrology.VERSION)).append(",\"fluvialVersion\":").append(Server.quote(FluvialNetwork.VERSION))
            .append(",\"climateVersion\":").append(Server.quote(ClimateField.VERSION)).append(",\"windVersion\":").append(Server.quote(WindField.VERSION))
            .append(",\"substrateVersion\":").append(Server.quote(TerrainSubstrate.VERSION))
            .append(",\"seed\":").append(Server.quote(Long.toString(r.world.seed))).append(",\"x\":").append(Server.quote(Long.toString(r.x))).append(",\"z\":").append(Server.quote(Long.toString(r.z)))
            .append(",\"worldHeight\":").append(r.world.worldHeight()).append(",\"seaLevel\":").append(r.world.seaLevel()).append(",\"size\":").append(r.world.settings.size()).append(",\"fields\":{");
        for(var layer:Layer.values()){if(out.charAt(out.length()-1)!='{')out.append(',');out.append(Server.quote(layer.name())).append(':');
            if(layer==Layer.plateId)out.append(Server.quote(Long.toString(s.owner().id())));else if(layer==Layer.continentId)out.append(Server.quote(Long.toString(s.continentId())));
            else if(layer==Layer.regime)out.append(Server.quote(edge.regime().name()));
            else if(layer==Layer.windDirection||layer==Layer.windSpeed)out.append(windValue(r.hydro.climate.wind(r.x,r.z),layer));
            else if(layer==Layer.rockType){if(root==null||!root.active(node))out.append("null");else out.append(Server.quote(root.rock(node).name()));}
            else if(layer==Layer.drainage){if(root==null||!root.active(node))out.append("null");else out.append(Server.quote(root.drainage(node).name()));}
            else if(layer==Layer.erodedTerrain){if(status==0)out.append("null");else out.append(root.elevation(r.x,r.z,s.elevation(),r.world.seaLevel()));}
            else if(hydroLayer(layer)){if(status==0)out.append("null");else out.append(hydroValue(root,node,layer));}
            else out.append(value(r.world,s,layer,r.x,r.z));}
        out.append("},\"hydrology\":{\"status\":").append(status).append(",\"step\":").append(r.hydro.step);
        if(root!=null&&root.active(node)) {
            int q=root.downstream(node);var wind=root.wind(r.x,r.z);out.append(",\"familyId\":").append(Server.quote(Long.toString(root.group.id())))
                .append(",\"nodeX\":").append(Server.quote(Long.toString(root.x(node)))).append(",\"nodeZ\":").append(Server.quote(Long.toString(root.z(node))))
                .append(",\"source\":").append(Server.quote(Long.toString(root.source(node)))).append(",\"flux\":").append(Server.quote(Long.toString(root.flux(node))))
                .append(",\"contributingCells\":").append(Server.quote(Long.toString(root.fluvial().contributingCells(node))))
                .append(",\"contributingArea\":").append(Server.quote(Long.toString(root.fluvial().contributingArea(node))))
                .append(",\"strahlerOrder\":").append(root.fluvial().strahlerOrder(node))
                .append(",\"supplied\":").append(Server.quote(Long.toString(root.supplied()))).append(",\"discharged\":").append(Server.quote(Long.toString(root.discharged())))
                .append(",\"unresolved\":").append(Server.quote(Long.toString(root.unresolved()))).append(",\"activeCells\":").append(root.activeCells)
                .append(",\"exportedSedimentMmBlock2\":").append(Server.quote(root.exportedSediment.toString()))
                .append(",\"climate\":{\"rainfall\":").append(root.rainfall(node)).append(",\"humidity\":").append(root.humidity(node))
                .append(",\"wind\":{\"x\":").append(wind.x()).append(",\"z\":").append(wind.z()).append(",\"speed\":").append(wind.speed())
                .append(",\"directionRadians\":").append(wind.directionRadians()).append(",\"directionDegrees\":").append(StrictMath.toDegrees(wind.directionRadians())).append("}}")
                .append(",\"ground\":{\"rock\":").append(Server.quote(root.rock(node).name())).append(",\"hardness\":").append(root.hardness(node))
                .append(",\"soilDepth\":").append(root.soilDepth(node)).append(",\"bedrockElevation\":").append(root.bedrockElevation(node))
                .append(",\"bedrockDepth\":").append(root.soilDepth(node)).append(",\"infiltrationPermille\":").append(root.infiltrationPermille(node))
                .append(",\"runoffPermille\":").append(root.runoffPermille(node)).append(",\"drainage\":").append(Server.quote(root.drainage(node).name())).append('}')
                .append(",\"downstream\":");
            if(q<0)out.append("null");else out.append("{\"x\":").append(Server.quote(Long.toString(root.x(q)))).append(",\"z\":").append(Server.quote(Long.toString(root.z(q)))).append('}');
            appendChannel(out,root,root.channelAt(r.x,r.z));
            appendLake(out,root,root.lakeAt(r.x,r.z,root.elevation(r.x,r.z,s.elevation(),r.world.seaLevel())));
        }
        return out.append("},\"closestBoundary\":{\"first\":").append(Server.quote(Long.toString(edge.first().id()))).append(",\"second\":").append(Server.quote(Long.toString(edge.second().id())))
            .append(",\"descendingSide\":").append(edge.descendingSide()).append("},\"waterStatus\":").append(Server.quote(WATER_STATUS)).append('}').toString();
    }
    private static void appendChannel(StringBuilder out,Root root,FluvialNetwork.Channel channel) {
        out.append(",\"channel\":");if(channel==null){out.append("null");return;}var profile=channel.profile();
        out.append("{\"source\":");appendCell(out,root,channel.sourceSegment());out.append(",\"downstream\":");appendCell(out,root,channel.downstream());
        out.append(",\"t\":").append(channel.t()).append(",\"thread\":").append(channel.thread()).append(",\"threadCount\":").append(channel.threadCount())
            .append(",\"flux\":").append(Server.quote(Long.toString(profile.flux())))
            .append(",\"meanDischarge\":").append(profile.meanDischarge()).append(",\"bankfullDischarge\":").append(profile.bankfullDischarge())
            .append(",\"currentWidth\":").append(profile.currentWidth()).append(",\"currentDepth\":").append(profile.currentDepth()).append(",\"currentVelocity\":").append(profile.currentVelocity())
            .append(",\"bankfullWidth\":").append(profile.bankfullWidth()).append(",\"bankfullDepth\":").append(profile.bankfullDepth()).append(",\"bankfullVelocity\":").append(profile.bankfullVelocity())
            .append(",\"slope\":").append(profile.slope()).append(",\"roughness\":").append(profile.roughness()).append(",\"strahlerOrder\":").append(profile.order())
            .append(",\"planform\":").append(Server.quote(profile.planform().name())).append(",\"tangent\":{\"x\":").append(channel.tangentX()).append(",\"z\":").append(channel.tangentZ()).append('}')
            .append(",\"centerDistance\":").append(channel.centerDistance()).append(",\"threadDistance\":").append(channel.threadDistance())
            .append(",\"insideCurrent\":").append(channel.insideCurrent()).append(",\"insideBankfull\":").append(channel.insideBankfull())
            .append(",\"waterSurface\":").append(channel.waterSurface()).append(",\"bedElevation\":").append(channel.bedElevation()).append(",\"bankfullWaterSurface\":").append(channel.bankfullWaterSurface()).append('}');
    }
    private static void appendLake(StringBuilder out,Root root,FluvialNetwork.LakeSample sample) {
        out.append(",\"lake\":");if(sample==null){out.append("null");return;}var lake=sample.lake();
        out.append("{\"id\":").append(Server.quote(Long.toString(lake.id()))).append(",\"component\":").append(lake.component())
            .append(",\"surface\":").append(sample.surface()).append(",\"surfaceMillimetres\":").append(lake.surfaceMillimetres())
            .append(",\"depth\":").append(sample.depth()).append(",\"maxDepth\":").append(lake.maxDepth()).append(",\"cellCount\":").append(lake.cellCount())
            .append(",\"flux\":").append(Server.quote(Long.toString(lake.flux()))).append(",\"shorelineSignal\":").append(sample.shorelineSignal())
            .append(",\"bedElevation\":").append(sample.bedElevation()).append(",\"spill\":");appendCell(out,root,lake.spillCell());
        out.append(",\"outlet\":");appendCell(out,root,lake.outletCell());out.append('}');
    }
    private static void appendCell(StringBuilder out,Root root,int cell) {
        if(cell<0||cell>=root.size()){out.append("null");return;}
        out.append("{\"cell\":").append(cell).append(",\"x\":").append(Server.quote(Long.toString(root.x(cell))))
            .append(",\"z\":").append(Server.quote(Long.toString(root.z(cell)))).append('}');
    }
    static String metadata() {
        StringBuilder out=new StringBuilder("{\"model\":").append(Server.quote(MODEL)).append(",\"version\":").append(Server.quote(TectonicTerrain.VERSION))
            .append(",\"hydrologyVersion\":").append(Server.quote(ContinentalHydrology.VERSION)).append(",\"fluvialVersion\":").append(Server.quote(FluvialNetwork.VERSION))
            .append(",\"climateVersion\":").append(Server.quote(ClimateField.VERSION)).append(",\"windVersion\":").append(Server.quote(WindField.VERSION))
            .append(",\"substrateVersion\":").append(Server.quote(TerrainSubstrate.VERSION))
            .append(",\"waterStatus\":").append(Server.quote(WATER_STATUS))
            .append(",\"native\":{\"baseHeight\":256,\"baseSeaLevel\":63,\"maxSize\":4},\"params\":[");
        for(var s:SPECS){if(out.charAt(out.length()-1)!='[')out.append(',');out.append("{\"id\":").append(Server.quote(s.id)).append(",\"label\":").append(Server.quote(s.label)).append(",\"default\":").append(s.value)
            .append(",\"min\":").append(s.min).append(",\"max\":").append(s.max).append(",\"step\":").append(s.step).append('}');}
        out.append("],\"fields\":[");for(var layer:Layer.values()){if(out.charAt(out.length()-1)!='[')out.append(',');out.append("{\"id\":").append(Server.quote(layer.name())).append(",\"label\":").append(Server.quote(layer.label))
            .append(",\"description\":").append(Server.quote(layer.description)).append(",\"min\":").append(layer.min).append(",\"max\":").append(layer.max).append('}');}
        return out.append("]}").toString();
    }
    private static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
}
