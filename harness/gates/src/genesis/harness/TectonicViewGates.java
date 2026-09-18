package genesis.harness;

import genesis.core.hash.Lattice;
import genesis.oracle.ClimateField;
import genesis.oracle.ContinentalHydrology;
import genesis.oracle.FluvialNetwork;
import genesis.oracle.TectonicTerrain;
import genesis.oracle.TerrainSubstrate;
import genesis.oracle.WindField;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Real HTTP integration against a temporary loopback server, not browser-layout verification. */
final class TectonicViewGates {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private record ChannelCandidate(int segment,long x,long z,double curvature) {}
    private record ChannelFixture(long x,long z,FluvialNetwork.Channel channel,double curvature) {}
    private record LakeFixture(long x,long z,FluvialNetwork.LakeSample sample) {}
    private record BudgetFixture(String name,TectonicView.HydroBudget budget) {}
    private static HttpResponse<byte[]> get(HttpClient client,String base,String path)throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base+path)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    private static String text(HttpResponse<byte[]> response){return new String(response.body(),java.nio.charset.StandardCharsets.UTF_8);}
    public static void main(String[] args)throws Exception{run();}
    static void run()throws Exception {
        var budgetFixtures=List.of(
            budget("default 384",Map.of("layer","riverMap","width","384","height","384")),
            budget("detail 96",Map.of("layer","riverMap","x","-6144","z","-6144","step","64","width","96","height","96")),
            budget("tile 128",Map.of("layer","riverMap","x","-3075","z","-3076","step","16","width","128","height","128")),
            budget("tile reference 256",Map.of("layer","riverMap","x","-3075","z","-3076","step","16","width","256","height","256")),
            budget("multi-family 3",Map.of("layer","riverMap","width","3","height","3","step","2048")));
        int maximumFamilies=0;long maximumVertices=0;
        for(var fixture:budgetFixtures) {
            var budget=fixture.budget();maximumFamilies=Math.max(maximumFamilies,budget.families());maximumVertices=Math.max(maximumVertices,budget.vertices());
            check(budget.families()<=TectonicView.MAX_HYDRO_FAMILIES&&budget.vertices()<=TectonicView.MAX_HYDRO_VERTICES,"Viewer work/memory guard rejects required fixture: "+fixture.name());
            System.out.println("Viewer hydrology budget "+fixture.name()+": families="+budget.families()+", vertices="+budget.vertices());
        }
        check(TectonicView.MAX_HYDRO_FAMILIES==20&&TectonicView.MAX_HYDRO_VERTICES==4_000_000&&TectonicView.MAX_HYDRO_ESTIMATED_BYTES==322_000_000&&TectonicView.estimatedHydrologyBytes(TectonicView.MAX_HYDRO_VERTICES)==TectonicView.MAX_HYDRO_ESTIMATED_BYTES,"Viewer hydrology guard changed without updating its retained-memory contract");
        check(maximumFamilies==16&&maximumVertices==3_287_184,"Required viewer fixtures changed their complete-continent budget; review the hard request guard");
        try(var running=Server.start(0);var client=HttpClient.newHttpClient()) {
            String base="http://localhost:"+running.port();
            for(String asset:new String[]{"/tectonics.html","/tectonics.js","/tectonics.css"})check(get(client,base,asset).statusCode()==200,"Missing tectonic asset "+asset);
            var meta=get(client,base,"/api/tectonic/meta");check(meta.statusCode()==200&&text(meta).equals(TectonicView.metadata()),"Metadata does not describe candidate");
            check(text(get(client,base,"/")).contains("/tectonics.html")&&text(get(client,base,"/continents.html")).contains("/tectonics.html"),"New viewer not discoverable");
            String config="seed=-9223372036854775808&x=-123456&z=76543&step=1024&width=7&height=6&size=2&plateSpacing=32768&continentalPercent=61&forcingPermille=1250&seaLevel=63";
            for(var layer:TectonicView.Layer.values()) {
                var request=TectonicView.request(TectonicView.query(config+"&layer="+layer));
                var direct=TectonicView.render(request);var response=get(client,base,"/api/tectonic/render?"+config+"&layer="+layer);
                check(response.statusCode()==200,"Render failed for "+layer);check(response.headers().firstValue("X-World-Model").orElse("").equals(TectonicView.MODEL),"Wrong render model");
                check(response.headers().firstValue("X-World-Version").orElse("").equals(TectonicTerrain.VERSION),"Wrong render version");
                check(response.headers().firstValue("X-Hydrology-Version").orElse("").equals(ContinentalHydrology.VERSION)&&response.headers().firstValue("X-Fluvial-Version").orElse("").equals(FluvialNetwork.VERSION),"Wrong water-model version");
                check(response.headers().firstValue("X-Climate-Version").orElse("").equals(ClimateField.VERSION)&&response.headers().firstValue("X-Wind-Version").orElse("").equals(WindField.VERSION)
                    &&response.headers().firstValue("X-Substrate-Version").orElse("").equals(TerrainSubstrate.VERSION),"Wrong climate/ground version");
                check(response.headers().firstValue("X-World-Height").orElse("").equals("512")&&response.headers().firstValue("X-Sea-Level").orElse("").equals("126")&&response.headers().firstValue("X-Native-Scale").orElse("").equals("2"),"Wrong native build dimensions");
                var image=ImageIO.read(new ByteArrayInputStream(response.body()));check(image.getWidth()==7&&image.getHeight()==6,"Render ignored dimensions");
                for(int z=0;z<6;z++)for(int x=0;x<7;x++)check(image.getRGB(x,z)==direct.image().getRGB(x,z),"HTTP differs from direct candidate raster");
                check(Double.parseDouble(response.headers().firstValue("X-Land-Fraction").orElseThrow())==direct.landFraction(),"Displayed land metric differs from height mask");
            }
            // Absolute samples, exact 64-bit seed/IDs, candidate-only regime labels and inspector limits.
            String point="seed=-9223372036854775808&x="+Lattice.MAX_COORDINATE+"&z="+(-Lattice.MAX_COORDINATE)+"&width=1&height=1";
            var pointRequest=TectonicView.request(TectonicView.query(point));var response=get(client,base,"/api/tectonic/sample?"+point);
            check(response.statusCode()==200&&text(response).equals(TectonicView.sampleJson(pointRequest)),"Extreme inspector differs from direct model");
            check(text(response).contains("\"seed\":\"-9223372036854775808\"")&&text(response).contains("\"plateId\":\"")&&text(response).contains("\"continentId\":\""),"64-bit values converted to JSON doubles");
            // Same world points reached through different windows and common-point zoom.
            for(var layer:TectonicView.Layer.values()) {
                // Contour strokes have an explicit zoom LOD; unadorned field colors do not.
                String suffix="&seed=42&contourInterval=0&layer="+layer;
                var a=ImageIO.read(new ByteArrayInputStream(get(client,base,"/api/tectonic/render?x=-2048&z=-2048&step=256&width=9&height=9"+suffix).body()));
                if(layer==TectonicView.Layer.riverMap) {
                    // The river layer alone has an explicit pixel-footprint LOD; crop identity remains exact.
                    var b=ImageIO.read(new ByteArrayInputStream(get(client,base,"/api/tectonic/render?x=-1536&z=-1536&step=256&width=5&height=5"+suffix).body()));
                    for(int z=0;z<5;z++)for(int x=0;x<5;x++)check(a.getRGB(x+2,z+2)==b.getRGB(x,z),"River display cue depends on crop edges");
                } else {
                    var b=ImageIO.read(new ByteArrayInputStream(get(client,base,"/api/tectonic/render?x=-1536&z=-1536&step=512&width=3&height=3"+suffix).body()));
                    for(int z=0;z<3;z++)for(int x=0;x<3;x++)check(a.getRGB(x*2+2,z*2+2)==b.getRGB(x,z),"Field depends on viewport/zoom: "+layer);
                }
            }
            String contour="&seed=42&layer=heightMap&contourInterval=25&step=1024";
            var wide=ImageIO.read(new ByteArrayInputStream(get(client,base,"/api/tectonic/render?x=400000&z=100000&width=12&height=12"+contour).body()));
            var crop=ImageIO.read(new ByteArrayInputStream(get(client,base,"/api/tectonic/render?x=402048&z=103072&width=6&height=6"+contour).body()));
            for(int z=0;z<6;z++)for(int x=0;x<6;x++)check(wide.getRGB(x+2,z+3)==crop.getRGB(x,z),"Contour depends on crop edges");
            var compactFine=TectonicView.request(TectonicView.query("layer=heightMap&contourInterval=1&step=1&width=1&height=1"));
            check(TectonicView.contourInterval(compactFine)==4,"Compact v7 contour LOD did not track the fourfold-smaller plate scale");
            var fine=TectonicView.request(TectonicView.query("layer=heightMap&contourInterval=1&step=1&width=1&height=1&plateSpacing=8192"));
            check(TectonicView.contourInterval(fine)==1,"One-block contours are unavailable when the feature scale can resolve them");
            var baseColor=new java.awt.Color(120,180,100);
            check(!TectonicView.contourColor(baseColor,100,5,25).equals(baseColor),"Contour missing at exact level on a slope");
            check(TectonicView.contourColor(baseColor,112.5,5,25).equals(baseColor),"Contour drawn between levels");
            check(TectonicView.contourColor(baseColor,100,0,25).equals(baseColor),"Flat plateau blackened at a contour level");
            check(get(client,base,"/api/tectonic/render?layer=heightMap&width=1&height=1&x="+Lattice.MAX_COORDINATE+"&z="+Lattice.MAX_COORDINATE).statusCode()==200,"Contour halo escaped numeric support");
            for(String bad:new String[]{"contourInterval=-1","contourInterval=257","elevationOffset=1","elevationOffset=-193","size=0","size=5","plateSpacing=2047"})check(get(client,base,"/api/tectonic/render?"+bad).statusCode()==400,"Invalid native display/terrain setting accepted: "+bad);
            check(TectonicView.request(TectonicView.query("layer=elevation&width=2048&height=2048")).width()==2048,"2048-wide tectonic render was rejected");
            for(String bad:new String[]{"seed=1&seed=2","seed=9223372036854775808","noise=1","model=continental","layer=noise","crustProvinceScale=2","plateWarpPermille=301","plateRoughnessPermille=201","seaLevel=NaN","width=0","height=0","step=0","x="+Lattice.MAX_COORDINATE+"&width=2","plateSpeed=129"})
                check(get(client,base,"/api/tectonic/render?"+bad).statusCode()==400,"Invalid tectonic query accepted: "+bad);
            check(get(client,base,"/api/tectonic/unknown").statusCode()==404,"Unknown tectonic route accepted");
            check(get(client,base,"/api/tectonic/sample?width=0").statusCode()==400,"Point endpoint ignored invalid dimensions");
            var post=client.send(HttpRequest.newBuilder(URI.create(base+"/api/tectonic/render")).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.discarding());check(post.statusCode()==405,"Tectonic route accepted mutation method");
            check(get(client,base,"/api/tectonic/render?plateSpacing=2049&width=2&height=2").statusCode()==200,"Valid off-step integer spacing rejected");
            // Inspect a representative API-produced image independently of browser availability.
            var png=get(client,base,"/api/tectonic/render?width=384&height=384");check(png.statusCode()==200,"Default viewer raster failed");
            Files.createDirectories(Path.of("build/gallery"));Files.write(Path.of("build/gallery/tectonic-viewer-terrain.png"),png.body());
            Files.write(Path.of("build/gallery/tectonic-viewer-height.png"),get(client,base,"/api/tectonic/render?layer=heightMap&width=384&height=384").body());
            Files.write(Path.of("build/gallery/tectonic-viewer-mountains.png"),get(client,base,"/api/tectonic/render?layer=heightMap&x=-32768&z=-49152&step=128&width=384&height=384&contourInterval=5").body());
            var rivers=get(client,base,"/api/tectonic/render?layer=riverMap&width=384&height=384");check(rivers.statusCode()==200,"Default continent river map failed");Files.write(Path.of("build/gallery/tectonic-rivers.png"),rivers.body());
            var riverDetail=get(client,base,"/api/tectonic/render?layer=riverMap&x=-6144&z=-6144&step=64&width=96&height=96");check(riverDetail.statusCode()==200,"Bounded wide-zoom river gallery failed");Files.write(Path.of("build/gallery/tectonic-rivers-detail.png"),riverDetail.body());
            String versioned=text(get(client,base,"/api/tectonic/sample?x=-131072&z=-131072"));
            check(versioned.contains("\"hydrologyVersion\":"+quote(ContinentalHydrology.VERSION))&&versioned.contains("\"fluvialVersion\":"+quote(FluvialNetwork.VERSION))
                &&versioned.contains("\"climateVersion\":"+quote(ClimateField.VERSION))&&versioned.contains("\"windVersion\":"+quote(WindField.VERSION))
                &&versioned.contains("\"substrateVersion\":"+quote(TerrainSubstrate.VERSION)),"Inspector missing water/climate/ground versions");
            check(get(client,base,"/api/tectonic/render?layer=riverMap&width=3&height=3&step=2048").statusCode()==200,"Bound rejected a modest multi-family hydrology view");
            check(get(client,base,"/api/tectonic/render?layer=riverMap&width=50&height=50&step=1048576").statusCode()==400,"Unbounded cold multi-continent solve accepted");
            check(get(client,base,"/api/tectonic/render?rainfallMm=10001&width=1&height=1").statusCode()==400,"Invalid rainfall accepted");
            var riverRequest=TectonicView.request(Map.of());
            check(riverRequest.world().plateParams.integer("plateSpacing")==2048&&riverRequest.x()==-3072&&riverRequest.z()==-3072&&riverRequest.step()==16,"Fourfold-smaller viewer preset is not coherent");
            check(riverRequest.hydro().climate!=null&&riverRequest.hydro().substrate!=null,"Viewer wrapped away terrain-aware climate/substrate providers");
            check(TectonicView.hydrologyBudget(TectonicView.request(Map.of("layer","windDirection","width","384","height","384"))).equals(new TectonicView.HydroBudget(0,0)),"Global wind unnecessarily builds continent roots");
            check(TectonicView.hydrologyBudget(TectonicView.request(Map.of("layer","humidity","width","1","height","1"))).families()>0,"Terrain-aware humidity bypasses its complete root");
            var riverRoot=riverRequest.hydro().root(riverRequest.world().continent(-1,-1));int mouth=-1;
            for(int p=0;p<riverRoot.size();p++)if(riverRoot.status(p)==1&&(mouth<0||riverRoot.flux(p)>riverRoot.flux(mouth)))mouth=p;
            check(mouth>=0&&riverRoot.flux(mouth)>0,"Missing nonzero river fixture");long mx=riverRoot.x(mouth),mz=riverRoot.z(mouth),hs=riverRoot.step;
            String location="x="+(mx-2*hs)+"&z="+(mz-2*hs)+"&step="+(hs/2)+"&width=9&height=9";
            for(String field:new String[]{"erodedTerrain","erosionDepth","hardness","riverMap","humidity","rainfall","soilDepth","bedrockElevation","bedrockDepth","rockType","infiltration","runoffFraction","drainage","runoff","lakeDepth","waterSurface","drainageStatus"}) {
                var large=ImageIO.read(new ByteArrayInputStream(get(client,base,"/api/tectonic/render?"+location+"&layer="+field).body()));
                if(field.equals("riverMap")) {
                    var sub=ImageIO.read(new ByteArrayInputStream(get(client,base,"/api/tectonic/render?x="+(mx-hs)+"&z="+(mz-hs)+"&step="+(hs/2)+"&width=5&height=5&layer="+field).body()));
                    for(int z=0;z<5;z++)for(int x=0;x<5;x++)check(large.getRGB(2+x,2+z)==sub.getRGB(x,z),"Actual river display changes with crop");
                } else {
                    var sub=ImageIO.read(new ByteArrayInputStream(get(client,base,"/api/tectonic/render?x="+(mx-hs)+"&z="+(mz-hs)+"&step="+hs+"&width=3&height=3&layer="+field).body()));
                    for(int z=0;z<3;z++)for(int x=0;x<3;x++)check(large.getRGB(2+x*2,2+z*2)==sub.getRGB(x,z),"Actual nonzero river changes with crop/zoom: "+field);
                }
                if(field.equals("riverMap")) {
                    var dry=ImageIO.read(new ByteArrayInputStream(get(client,base,"/api/tectonic/render?"+location+"&layer=riverMap&rainfallMm=0").body()));
                    check(dry.getRGB(4,4)!=large.getRGB(4,4),"Rainfall zero did not remove the river overlay");
                }
            }
            var wetPoint=text(get(client,base,"/api/tectonic/sample?x="+mx+"&z="+mz));check(wetPoint.contains("\"flux\":\""+riverRoot.flux(mouth)+"\""),"Exact large river flux lost in JSON");
            check(wetPoint.contains("\"rainfall\":"+riverRoot.rainfall(mouth))&&wetPoint.contains("\"humidity\":"+riverRoot.humidity(mouth))
                &&wetPoint.contains("\"rockType\":"+quote(riverRoot.rock(mouth).name()))&&wetPoint.contains("\"drainage\":"+quote(riverRoot.drainage(mouth).name())),"Climate/ground map fields missing from inspector");
            for(String field:new String[]{"windDirection","windSpeed","soilDepth","bedrockElevation","bedrockDepth","infiltration","runoffFraction"})check(jsonNumber(wetPoint,field),"Climate/ground field is absent or not numeric: "+field);
            check(wetPoint.contains("\"climate\":{\"rainfall\":")&&wetPoint.contains("\"wind\":{\"x\":")&&wetPoint.contains("\"ground\":{\"rock\":"),"Structured climate/ground inspector metadata missing");
            var channelFixture=channelFixture(riverRoot);var channel=channelFixture.channel();int channelNode=riverRoot.index(channelFixture.x(),channelFixture.z());
            String channelPoint=text(get(client,base,"/api/tectonic/sample?x="+channelFixture.x()+"&z="+channelFixture.z()));
            check(channelPoint.contains("\"contributingCells\":\""+riverRoot.fluvial().contributingCells(channelNode)+"\"")&&channelPoint.contains("\"contributingArea\":\""+riverRoot.fluvial().contributingArea(channelNode)+"\"")&&channelPoint.contains("\"strahlerOrder\":"+riverRoot.fluvial().strahlerOrder(channelNode)),"Inspector lost exact contributing area/order");
            check(channelPoint.contains("\"channel\":{\"source\":{")&&channelPoint.contains("\"flux\":\""+channel.profile().flux()+"\"")&&channelPoint.contains("\"planform\":"+quote(channel.profile().planform().name())),"Inspector omitted the sampled channel identity/form");
            for(String field:new String[]{"t","meanDischarge","bankfullDischarge","currentWidth","currentDepth","currentVelocity","bankfullWidth","bankfullDepth","bankfullVelocity","slope","roughness"})
                check(jsonNumber(channelPoint,field),"Channel field is absent or not numeric: "+field);
            check(Pattern.compile("\\\"tangent\\\":\\{\\\"x\\\":-?[0-9.Ee+]+,\\\"z\\\":-?[0-9.Ee+]+}").matcher(channelPoint).find(),"Channel tangent is absent or not numeric");
            int channelWater=pixel(client,base,channelFixture.x(),channelFixture.z(),"riverMap"),channelLand=pixel(client,base,channelFixture.x(),channelFixture.z(),"erodedTerrain");
            check(channelFixture.curvature()>1e-6&&channel.insideCurrent()&&channelWater!=channelLand,"River map did not render a curved current-width channel pixel");
            check(TectonicView.currentChannelAlpha(channel,1)>=.78,"Physical current thread lost its strong water mask");
            var profile=channel.profile();double lodDistance=channel.threadWidth()/2+64;
            var lodChannel=new FluvialNetwork.Channel(channel.sourceSegment(),channel.downstream(),channel.t(),channel.thread(),profile.bankfullWidth(),lodDistance,
                channel.centerX(),channel.centerZ(),channel.threadX(),channel.threadZ(),channel.tangentX(),channel.tangentZ(),channel.waterSurface(),channel.bedElevation(),channel.bankfullWaterSurface(),profile);
            check(!lodChannel.insideCurrent()&&!lodChannel.insideBankfull()&&TectonicView.currentChannelAlpha(lodChannel,1)==0&&TectonicView.currentChannelAlpha(lodChannel,256)>=.42,"Display LOD does not distinguish physical water from a coarse pixel-footprint cue");

            var lakeFixture=lakeFixture(riverRequest,riverRoot);var lake=lakeFixture.sample().lake();
            String lakePoint=text(get(client,base,"/api/tectonic/sample?x="+lakeFixture.x()+"&z="+lakeFixture.z()));
            check(lakePoint.contains("\"lake\":{\"id\":\""+lake.id()+"\",\"component\":"+lake.component())&&lakePoint.contains("\"surface\":"+lake.surface())&&lakePoint.contains("\"maxDepth\":"+lake.maxDepth())&&lakePoint.contains("\"cellCount\":"+lake.cellCount()),"Inspector omitted stable flat-lake metadata");
            for(String field:new String[]{"surface","depth","maxDepth","shorelineSignal","bedElevation"})check(jsonNumber(lakePoint,field),"Lake field is absent or not numeric: "+field);
            check(lakePoint.contains("\"spill\":{")&&lakePoint.contains("\"outlet\":"),"Lake spill/outlet metadata missing");
            int lakeWater=pixel(client,base,lakeFixture.x(),lakeFixture.z(),"riverMap"),lakeLand=pixel(client,base,lakeFixture.x(),lakeFixture.z(),"erodedTerrain");
            check(lakeWater!=lakeLand,"River map did not render a fine connected-lake pixel");
            var dryRequest=TectonicView.request(Map.of("rainfallMm","0"));var dryRoot=dryRequest.hydro().root(dryRequest.world().continent(-1,-1));
            var dryLakeFixture=lakeFixture(dryRequest,dryRoot);var dryLake=dryLakeFixture.sample().lake();
            check(dryLake.flux()==0,"Zero-rain topographic lake carries flux");
            int patchStep=Math.max(1,dryRoot.step/32),patchRadius=4,patchSize=patchRadius*2+1;
            long patchX=dryLakeFixture.x()-patchRadius*(long)patchStep,patchZ=dryLakeFixture.z()-patchRadius*(long)patchStep;
            String lakePatch="rainfallMm=0&x="+patchX+"&z="+patchZ+"&step="+patchStep+"&width="+patchSize+"&height="+patchSize;
            var dryLakeMap=ImageIO.read(new ByteArrayInputStream(get(client,base,"/api/tectonic/render?"+lakePatch+"&layer=riverMap").body()));
            var dryLakeTerrain=ImageIO.read(new ByteArrayInputStream(get(client,base,"/api/tectonic/render?"+lakePatch+"&layer=erodedTerrain").body()));
            for(int z=0;z<patchSize;z++)for(int x=0;x<patchSize;x++)check(dryLakeMap.getRGB(x,z)==dryLakeTerrain.getRGB(x,z),"Zero-rain topographic lake painted water at "+x+","+z);
            String dryLakePoint=text(get(client,base,"/api/tectonic/sample?rainfallMm=0&x="+dryLakeFixture.x()+"&z="+dryLakeFixture.z()));
            check(dryLakePoint.contains("\"lake\":{\"id\":\""+dryLake.id()+"\"")&&dryLakePoint.contains("\"cellCount\":"+dryLake.cellCount()+",\"flux\":\"0\""),"Dry inspector lost zero-flux topographic lake metadata");
            check(pixel(client,base,channelFixture.x(),channelFixture.z(),"riverMap")==channelWater,"River raster depends on lake/channel query order");
            check(channelPoint.contains("\"waterStatus\":"+quote(TectonicView.WATER_STATUS))&&TectonicView.WATER_STATUS.contains("downhill/cross-divide")&&TectonicView.WATER_STATUS.contains("transient storage"),"Water-status caveat is stale");
            var unsupported=text(get(client,base,"/api/tectonic/sample?x="+Lattice.MAX_COORDINATE+"&z="+Lattice.MAX_COORDINATE));
            check(unsupported.contains("\"waterSurface\":null")&&unsupported.contains("\"hydrology\":{\"status\":0"),"Numeric guard became a drainage outlet");
            for(String layer:new String[]{"erodedTerrain","erosionDepth","hardness"})Files.write(Path.of("build/gallery/tectonic-"+layer+".png"),get(client,base,"/api/tectonic/render?layer="+layer+"&width=384&height=384").body());
            for(String bad:new String[]{"erosionStrength=-1","erosionStrength=3001"})check(get(client,base,"/api/tectonic/render?"+bad).statusCode()==400,"Invalid erosion accepted");
            Files.writeString(Path.of("build/tectonic-viewer.json"),"{\"model\":\"tectonic-experimental\",\"layers\":"+TectonicView.Layer.values().length+",\"parameters\":"+TectonicView.SPECS.size()+",\"nativeBaseHeight\":256,\"nativeBaseSeaLevel\":63,\"integralUpscale\":true,\"fullscreenLogic\":true,\"httpDirectAgreement\":true,\"continentalRivers\":true,\"climate\":true,\"substrate\":true,\"erosion\":true}\n");
        }
        System.out.println("PASS TECTONIC VIEW: native 256/Y63 headers, fourfold XZ preset, continuous wind/climate/ground maps, size-2 rendering, curved current-width channels, fine flat lakes, hydraulic inspector, block contours, fullscreen logic and deterministic exact configuration");
    }
    private static BudgetFixture budget(String name,Map<String,String> query) {
        return new BudgetFixture(name,TectonicView.hydrologyBudget(TectonicView.request(query)));
    }
    private static ChannelFixture channelFixture(ContinentalHydrology.Root root) {
        var candidates=new ArrayList<ChannelCandidate>();var network=root.fluvial();
        for(int p=0;p<root.size();p++) {
            var profile=network.profile(p);if(profile==null)continue;int threads=profile.planform()==FluvialNetwork.Planform.BRAIDED?2:1;
            if(profile.currentWidth()<=0)continue;
            for(int k=1;k<FluvialNetwork.CURVE_SUBDIVISIONS;k++)for(int thread:threads==1?new int[]{0}:new int[]{-1,1}) {
                double t=k/(double)FluvialNetwork.CURVE_SUBDIVISIONS;var point=network.threadCenterline(p,t,thread);double curvature=network.centerline(p,t).displacement();
                if(curvature>1e-6)candidates.add(new ChannelCandidate(p,Math.round(point.x()),Math.round(point.z()),curvature));
            }
        }
        candidates.sort(Comparator.comparingDouble(ChannelCandidate::curvature).reversed());
        for(var candidate:candidates) {
            var channel=root.channelAt(candidate.x(),candidate.z());
            if(channel!=null&&channel.sourceSegment()==candidate.segment()&&channel.t()>0&&channel.t()<1&&channel.insideCurrent())return new ChannelFixture(candidate.x(),candidate.z(),channel,candidate.curvature());
        }
        throw new AssertionError("No curved integer current-channel fixture");
    }
    private static LakeFixture lakeFixture(TectonicView.Request request,ContinentalHydrology.Root root) {
        int quarter=Math.max(1,root.step/4);int[] offsets={quarter,root.step/2,3*quarter,0};
        for(int p=0;p<root.size();p++)if(root.fluvial().lakeForCell(p)!=null)for(int dz:offsets)for(int dx:offsets) {
            long x=root.x(p)+dx,z=root.z(p)+dz;var raw=request.world().sample(x,z);double bed=root.elevation(x,z,raw.elevation(),request.world().seaLevel());var sample=root.lakeAt(x,z,bed);
            if(sample!=null&&sample.wet())return new LakeFixture(x,z,sample);
        }
        throw new AssertionError("No fine connected-lake fixture");
    }
    private static int pixel(HttpClient client,String base,long x,long z,String layer)throws Exception {
        var response=get(client,base,"/api/tectonic/render?x="+x+"&z="+z+"&step=1&width=1&height=1&layer="+layer);
        check(response.statusCode()==200,"Fixture pixel render failed for "+layer);return ImageIO.read(new ByteArrayInputStream(response.body())).getRGB(0,0);
    }
    private static boolean jsonNumber(String json,String field){return Pattern.compile("\\\""+Pattern.quote(field)+"\\\":-?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)(?:[Ee][+-]?[0-9]+)?").matcher(json).find();}
    private static String quote(String value){return "\""+value+"\"";}
}
