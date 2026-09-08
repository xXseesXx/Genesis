package genesis.harness;

import genesis.core.Generator;
import genesis.core.Params;
import genesis.core.fields.FieldRegistry;
import genesis.core.fields.Fields;
import genesis.core.fields.RockColumn;
import genesis.core.fields.RockColumn.Rock;
import genesis.core.geology.Stratigraphy;
import genesis.core.hash.Lattice;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;

public final class GeologyGates {
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    private static FieldRegistry fixture(double surface){return new FieldRegistry.Builder().add(Fields.BASE_ELEVATION,(x,z)->surface).add(Fields.CRUST_TYPE,(x,z)->1).add(Fields.CRUST_AGE,(x,z)->400).build();}
    public static void run() throws Exception {
        Params flat=new Params(Map.of("geologyFoldAmplitude",0.0));
        long[] elevations={Long.MIN_VALUE,299,300,1099,1100,1499,1500,Long.MAX_VALUE};
        int[] expected={1,1,2,2,3,3,4,4};
        var analytic=new Stratigraphy(42,flat,fixture(1100)).column(-17,32);
        for(int i=0;i<elevations.length;i++)check(analytic.at(elevations[i]).rock.id==expected[i],"Half-open rock contact ownership");
        check(analytic.at(1100).formationAge==200,"Synthetic age order");
        var copy=analytic.layers();copy[0]=null;check(analytic.layers()[0]!=null,"Column leaks mutable array");
        for(int k=0;k<4;k++)check(analytic.layers()[k].formationAge==400*(4-k)/4,"Formation age must become younger upward");
        for(Rock rock:Rock.values()) {
            long y=rock==Rock.SHALE?500:rock==Rock.LIMESTONE?1200:rock==Rock.SANDSTONE?1800:0;
            var inputs=new FieldRegistry.Builder().add(Fields.BASE_ELEVATION,(x,z)->(double)y).add(Fields.CRUST_TYPE,(x,z)->rock==Rock.BASALT?0:1).add(Fields.CRUST_AGE,(x,z)->400).build();
            var geology=new Stratigraphy(42,flat,inputs);
            check(geology.rock(0,0)==rock.id,"Analytic material selection");
            var changed=new Params(Map.of("geologyFoldAmplitude",0.0,rock.parameter+"Hardness",17.0,rock.parameter+"Weatherability",31.0));
            var altered=new Stratigraphy(42,changed,inputs);
            check(altered.hardness(0,0)==17&&altered.weatherability(0,0)==31,"Material coefficient propagation");
            check(altered.rock(0,0)==geology.rock(0,0),"Strength changes contact geometry");
        }
        Random random=new Random(2941);
        for(int scale:new int[]{1024,1025,16384,1048576}) {
            Params p=new Params(Map.of("geologyFoldScale",(double)scale,"geologyFoldAmplitude",4000.0));
            var strata=new Stratigraphy(Long.MIN_VALUE,p,fixture(1000));int bound=4+8*4000/Math.min(scale,4096);
            for(int k=0;k<1500;k++) {
                long x=(k<500?k%17L*scale:random.nextLong(-1000000,1000000)),z=random.nextLong(-1000000,1000000);
                int a=strata.displacement(x,z),b=strata.displacement(x-1,z),c=strata.displacement(x,z+1);
                check(Math.abs(a)<=4000&&Math.abs(a-b)<=bound&&Math.abs(a-c)<=bound,"Fold join/quantization bound");
                var col=strata.column(x,z);var layers=col.layers();
                check(layers[1].upper-layers[1].lower==800&&layers[2].upper-layers[2].lower==400,"Deformation inverted or stretched layers");
                check(col.at(col.surfaceY).rock.id==strata.rock(x,z),"Column/raster material mismatch");
            }
            for(long x:new long[]{-Lattice.MAX_COORDINATE,Lattice.MAX_COORDINATE})check(Math.abs(strata.displacement(x,x))<=4000,"Extreme fold overflow");
        }
        worldContracts();golden(false);diagnostics();
        System.out.println("PASS GEOLOGY: contact ties/continuity/thickness, synthetic chronology, coefficient counterfactuals, structured cold/order/concurrency, old-field independence, code-rendered map/section");
    }
    private static void worldContracts() throws Exception {
        var geological=List.of(Fields.ROCK_TYPE,Fields.HARDNESS,Fields.WEATHERABILITY,Fields.FORMATION_AGE,Fields.STRATA_DISPLACEMENT);
        for(var model:Generator.Model.values()) {
            var world=new Generator(-1,Params.defaults(),model);
            try{world.columns.tile(Fields.ROCK_COLUMN,0,0,1,2,2);throw new AssertionError("Structured value cast to number");}catch(IllegalArgumentException expected){}
            Object[] tile=world.columns.values(Fields.ROCK_COLUMN,-12345,-8123,17,16,16);
            var cold=new Generator(-1,Params.defaults(),model);
            for(int z=0;z<8;z++)for(int x=0;x<8;x++)check(GeologyJson.column((RockColumn)tile[z*2*16+x*2]).equals(GeologyJson.column(cold.columns.get(Fields.ROCK_COLUMN,-12345+x*34,-8123+z*34))),"Structured bulk/cold/common-point zoom mismatch");
            var altered=new Generator(-1,new Params(Map.of("geologyDatum",-4000.0,"geologyFoldAmplitude",0.0,"sandstoneHardness",10.0)),model);
            List<String> expected=new ArrayList<>();List<Integer> order=new ArrayList<>();int differences=0;
            for(int k=0;k<600;k++) {
                long x=k%30*4097L-80000,z=k/30*4097L-50000;var col=world.columns.get(Fields.ROCK_COLUMN,x,z);
                expected.add(GeologyJson.column(col));order.add(k);
                check(col.at(col.surfaceY).rock.id==world.fields.get(Fields.ROCK_TYPE,x,z),"Exposed field disagrees with structured column");
                check(col.at(col.surfaceY).formationAge==world.fields.get(Fields.FORMATION_AGE,x,z),"Column age mismatch");
                for(var field:world.fields.ids())if(!geological.contains(field))
                    check(world.fields.get(field,x,z).equals(altered.fields.get(field,x,z)),"Geology unexpectedly changed terrain/water: "+field.name);
                if(!world.fields.get(Fields.ROCK_TYPE,x,z).equals(altered.fields.get(Fields.ROCK_TYPE,x,z)))differences++;
            }
            check(differences>0,"Contact counterfactual never changes exposure");
            Collections.shuffle(order,new Random(471));var fresh=new Generator(-1,Params.defaults(),model);
            try(var pool=Executors.newFixedThreadPool(4)) {
                List<Callable<Boolean>> jobs=new ArrayList<>();for(int k:order)jobs.add(()->expected.get(k).equals(GeologyJson.column(fresh.columns.get(Fields.ROCK_COLUMN,k%30*4097L-80000,k/30*4097L-50000))));
                for(var result:pool.invokeAll(jobs))check(result.get(),"Structured field order/concurrency mismatch");
            }
        }
    }
    public static void diagnostics() throws Exception {
        Files.createDirectories(Path.of("build/gallery"));var g=new Generator(-1,Params.defaults(),Generator.Model.CONTINENTAL);
        var image=new BufferedImage(1568,920,BufferedImage.TYPE_INT_RGB);var pen=image.createGraphics();
        pen.setColor(new Color(0x10181b));pen.fillRect(0,0,1568,920);pen.setColor(new Color(0xe3e9e5));pen.setFont(new Font("SansSerif",Font.PLAIN,16));
        var fields=List.of(Fields.BASE_ELEVATION,Fields.ROCK_TYPE,Fields.HARDNESS);
        long ox=-131072,oz=-122880;
        for(int k=0;k<3;k++){pen.drawString(fields.get(k).label,8+k*520,23);pen.drawImage(Renderer.render(g,new Renderer.Layer[]{new Renderer.Layer(fields.get(k),1)},ox,oz,256,512,512).image(),8+k*520,36,null);}
        pen.drawString("COLUMN SECTION: current terrain clips a folded material volume; no erosion or water feedback",8,580);
        int sx=8,sy=604,width=1552,height=260;
        for(int x=0;x<width;x++) {
            long wx=ox+x*128;var column=g.columns.get(Fields.ROCK_COLUMN,wx,oz+65536);
            for(int y=0;y<height;y++) {
                long elevation=4000-y*24;int color=elevation>column.surfaceY?0x183746:Renderer.color(Fields.ROCK_TYPE,column.at(elevation).rock.id);
                image.setRGB(sx+x,sy+y,color);
            }
        }
        pen.drawString("Grey basalt / pink granite / olive shale / pale green limestone / ochre sandstone. Section: z="+(oz+65536)+", x step=128; top=4000 m, vertical step=24 m.",8,886);
        pen.drawString("Seed -1 / continental. Map panels share coordinates. Outer material units are unbounded; this is M5a stratigraphy, not a geological history simulation.",8,910);
        pen.dispose();ImageIO.write(image,"png",Path.of("build/gallery/geology-strata.png").toFile());
    }
    public static void golden(boolean candidates) throws Exception {
        Properties baseline=new Properties();
        if(!candidates){try(var in=Files.newInputStream(Path.of("harness/golden/geology.properties"))){baseline.load(in);}check(Stratigraphy.VERSION.equals(baseline.getProperty("version")),"Geology baseline version");}
        for(long seed:new long[]{0,1,-1,42,137,8675309,Long.MIN_VALUE,Long.MAX_VALUE}) {
            var world=new Generator(seed,Params.defaults(),Generator.Model.CONTINENTAL);var data=new StringBuilder();
            for(int j=0;j<32;j++)for(int i=0;i<32;i++) {
                long x=-131072+i*4096L,z=-122880+j*4096L;data.append(GeologyJson.column(world.columns.get(Fields.ROCK_COLUMN,x,z)));
                for(var field:List.of(Fields.ROCK_TYPE,Fields.HARDNESS,Fields.WEATHERABILITY,Fields.FORMATION_AGE,Fields.STRATA_DISPLACEMENT))data.append('|').append(world.fields.get(field,x,z));
                data.append('\n');
            }
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.toString().getBytes(StandardCharsets.UTF_8)));
            if(candidates)System.out.println("seed."+seed+"="+hash);else check(hash.equals(baseline.getProperty("seed."+seed)),"Geology snapshot changed seed "+seed);
        }
    }
    public static void main(String[] args) throws Exception {if(args.length>0){golden(true);diagnostics();}else run();}
}
