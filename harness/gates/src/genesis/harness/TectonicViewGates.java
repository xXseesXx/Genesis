package genesis.harness;

import genesis.core.hash.Lattice;
import genesis.oracle.TectonicTerrain;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;

/** Real HTTP integration against a temporary loopback server, not browser-layout verification. */
final class TectonicViewGates {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static HttpResponse<byte[]> get(HttpClient client,String base,String path)throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base+path)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    private static String text(HttpResponse<byte[]> response){return new String(response.body(),java.nio.charset.StandardCharsets.UTF_8);}
    public static void main(String[] args)throws Exception{run();}
    static void run()throws Exception {
        try(var running=Server.start(0);var client=HttpClient.newHttpClient()) {
            String base="http://127.0.0.1:"+running.port();
            for(String asset:new String[]{"/tectonics.html","/tectonics.js","/tectonics.css"})check(get(client,base,asset).statusCode()==200,"Missing tectonic asset "+asset);
            var meta=get(client,base,"/api/tectonic/meta");check(meta.statusCode()==200&&text(meta).equals(TectonicView.metadata()),"Metadata does not describe candidate");
            check(text(get(client,base,"/")).contains("/tectonics.html")&&text(get(client,base,"/continents.html")).contains("/tectonics.html"),"New viewer not discoverable");
            String config="seed=-9223372036854775808&x=-123456&z=76543&step=1024&width=7&height=6&plateSpacing=32768&continentalPercent=61&forcingPermille=1250&seaLevel=150";
            for(var layer:TectonicView.Layer.values()) {
                var request=TectonicView.request(TectonicView.query(config+"&layer="+layer));
                var direct=TectonicView.render(request);var response=get(client,base,"/api/tectonic/render?"+config+"&layer="+layer);
                check(response.statusCode()==200,"Render failed for "+layer);check(response.headers().firstValue("X-World-Model").orElse("").equals(TectonicView.MODEL),"Wrong render model");
                check(response.headers().firstValue("X-World-Version").orElse("").equals(TectonicTerrain.VERSION),"Wrong render version");
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
                var a=ImageIO.read(new ByteArrayInputStream(get(client,base,"/api/tectonic/render?x=-8192&z=-8192&step=1024&width=9&height=9"+suffix).body()));
                var b=ImageIO.read(new ByteArrayInputStream(get(client,base,"/api/tectonic/render?x=-6144&z=-6144&step=2048&width=3&height=3"+suffix).body()));
                for(int z=0;z<3;z++)for(int x=0;x<3;x++)check(a.getRGB(x*2+2,z*2+2)==b.getRGB(x,z),"Field depends on viewport/zoom: "+layer);
            }
            String contour="&seed=42&layer=heightMap&contourInterval=25&step=1024";
            var wide=ImageIO.read(new ByteArrayInputStream(get(client,base,"/api/tectonic/render?x=400000&z=100000&width=12&height=12"+contour).body()));
            var crop=ImageIO.read(new ByteArrayInputStream(get(client,base,"/api/tectonic/render?x=402048&z=103072&width=6&height=6"+contour).body()));
            for(int z=0;z<6;z++)for(int x=0;x<6;x++)check(wide.getRGB(x+2,z+3)==crop.getRGB(x,z),"Contour depends on crop edges");
            var fine=TectonicView.request(TectonicView.query("layer=heightMap&contourInterval=5&step=1&width=1&height=1"));check(TectonicView.contourInterval(fine)==5,"Five metre contours not available at fine zoom");
            var baseColor=new java.awt.Color(120,180,100);
            check(!TectonicView.contourColor(baseColor,100,5,25).equals(baseColor),"Contour missing at exact level on a slope");
            check(TectonicView.contourColor(baseColor,112.5,5,25).equals(baseColor),"Contour drawn between levels");
            check(TectonicView.contourColor(baseColor,100,0,25).equals(baseColor),"Flat plateau blackened at a contour level");
            check(get(client,base,"/api/tectonic/render?layer=heightMap&width=1&height=1&x="+Lattice.MAX_COORDINATE+"&z="+Lattice.MAX_COORDINATE).statusCode()==200,"Contour halo escaped numeric support");
            for(String bad:new String[]{"contourInterval=4","contourInterval=-1","contourInterval=1001","elevationOffset=1","elevationOffset=-4001"})check(get(client,base,"/api/tectonic/render?"+bad).statusCode()==400,"Invalid display/offset accepted: "+bad);
            for(String bad:new String[]{"seed=1&seed=2","seed=9223372036854775808","noise=1","model=continental","layer=noise","crustProvinceScale=2","plateWarpPermille=301","plateRoughnessPermille=201","seaLevel=NaN","width=513","height=0","step=0","x="+Lattice.MAX_COORDINATE+"&width=2","plateSpeed=129"})
                check(get(client,base,"/api/tectonic/render?"+bad).statusCode()==400,"Invalid tectonic query accepted: "+bad);
            check(get(client,base,"/api/tectonic/unknown").statusCode()==404,"Unknown tectonic route accepted");
            check(get(client,base,"/api/tectonic/sample?width=0").statusCode()==400,"Point endpoint ignored invalid dimensions");
            var post=client.send(HttpRequest.newBuilder(URI.create(base+"/api/tectonic/render")).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.discarding());check(post.statusCode()==405,"Tectonic route accepted mutation method");
            check(get(client,base,"/api/tectonic/render?plateSpacing=8193&width=2&height=2").statusCode()==200,"Valid off-step integer spacing rejected");
            // Inspect a representative API-produced image independently of browser availability.
            var png=get(client,base,"/api/tectonic/render?width=384&height=384");check(png.statusCode()==200,"Default viewer raster failed");
            Files.createDirectories(Path.of("build/gallery"));Files.write(Path.of("build/gallery/tectonic-viewer-terrain.png"),png.body());
            Files.write(Path.of("build/gallery/tectonic-viewer-height.png"),get(client,base,"/api/tectonic/render?layer=heightMap&width=384&height=384").body());
            Files.write(Path.of("build/gallery/tectonic-viewer-mountains.png"),get(client,base,"/api/tectonic/render?layer=heightMap&x=4233969&z=3211581&step=1024&width=384&height=384&contourInterval=25").body());
            var rivers=get(client,base,"/api/tectonic/render?layer=riverMap&width=384&height=384");check(rivers.statusCode()==200,"Default continent river map failed");Files.write(Path.of("build/gallery/tectonic-rivers.png"),rivers.body());
            Files.write(Path.of("build/gallery/tectonic-rivers-detail.png"),get(client,base,"/api/tectonic/render?layer=riverMap&x=-786432&z=-786432&step=2048&width=384&height=384").body());
            check(text(get(client,base,"/api/tectonic/sample?x=-524288&z=-524288")).contains("\"hydrologyVersion\":\"continental-hydrology-v1\""),"Inspector missing drainage version");
            check(get(client,base,"/api/tectonic/render?layer=riverMap&width=50&height=50&step=1048576").statusCode()==400,"Oversized hydrology solve accepted");
            check(get(client,base,"/api/tectonic/render?rainfallMm=10001&width=1&height=1").statusCode()==400,"Invalid rainfall accepted");
            var riverRequest=TectonicView.request(Map.of());var riverRoot=riverRequest.hydro().root(riverRequest.world().continent(-1,-1));int mouth=-1;
            for(int p=0;p<riverRoot.size();p++)if(riverRoot.status(p)==1&&(mouth<0||riverRoot.flux(p)>riverRoot.flux(mouth)))mouth=p;
            check(mouth>=0&&riverRoot.flux(mouth)>0,"Missing nonzero river fixture");long mx=riverRoot.x(mouth),mz=riverRoot.z(mouth),hs=riverRoot.step;
            String location="x="+(mx-2*hs)+"&z="+(mz-2*hs)+"&step="+(hs/2)+"&width=9&height=9";
            for(String field:new String[]{"riverMap","runoff","lakeDepth","waterSurface","drainageStatus"}) {
                var large=ImageIO.read(new ByteArrayInputStream(get(client,base,"/api/tectonic/render?"+location+"&layer="+field).body()));
                var sub=ImageIO.read(new ByteArrayInputStream(get(client,base,"/api/tectonic/render?x="+(mx-hs)+"&z="+(mz-hs)+"&step="+hs+"&width=3&height=3&layer="+field).body()));
                for(int z=0;z<3;z++)for(int x=0;x<3;x++)check(large.getRGB(2+x*2,2+z*2)==sub.getRGB(x,z),"Actual nonzero river changes with crop/zoom: "+field);
                if(field.equals("riverMap")) {
                    var dry=ImageIO.read(new ByteArrayInputStream(get(client,base,"/api/tectonic/render?"+location+"&layer=riverMap&rainfallMm=0").body()));
                    check(dry.getRGB(4,4)!=large.getRGB(4,4),"Rainfall zero did not remove the river overlay");
                }
            }
            var wetPoint=text(get(client,base,"/api/tectonic/sample?x="+mx+"&z="+mz));check(wetPoint.contains("\"flux\":\""+riverRoot.flux(mouth)+"\""),"Exact large river flux lost in JSON");
            var unsupported=text(get(client,base,"/api/tectonic/sample?x="+Lattice.MAX_COORDINATE+"&z="+Lattice.MAX_COORDINATE));
            check(unsupported.contains("\"waterSurface\":null")&&unsupported.contains("\"hydrology\":{\"status\":0"),"Numeric guard became a drainage outlet");
            Files.writeString(Path.of("build/tectonic-viewer.json"),"{\"model\":\"tectonic-experimental\",\"layers\":33,\"httpDirectAgreement\":true,\"unadornedCropZoomAgreement\":true,\"contourCropAgreement\":true,\"continentalRivers\":true,\"browserVisualCheck\":\"not provided by this gate\"}\n");
        }
        System.out.println("PASS TECTONIC VIEW: 33 candidate layers HTTP/direct/crop/zoom; continent river support/limits/rainfall; contour halo/LOD; exact inspector/configuration; code-rendered views; NOT browser layout QA");
    }
}
