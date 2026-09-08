package genesis.harness;

import genesis.core.Params;
import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;
import genesis.core.hydro.BoundaryPorts;
import genesis.core.hydro.DrainageRefinement;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/** Explicit two-parent fixture. Budgets and synthetic resistance are NOT world hydrology. */
public final class RefinementDemo {
    public static final List<String> LAYERS=List.of("network","rain","inflow","flux","cost","distance");
    public record Demo(long seed,long x,long z,long spacing,int level,int baseSpacing,
                       long rain,long inflow,DrainageRefinement.Result a,DrainageRefinement.Result b) {}
    private RefinementDemo() {}
    public static Demo create(Map<String,String> query) {
        for(String key:query.keySet())if(!List.of("seed","x","z","level","rain","inflow","coarseSpacing","layer").contains(key))
            throw new IllegalArgumentException("Not a refinement fixture parameter: "+key);
        if(!LAYERS.contains(query.getOrDefault("layer","network")))throw new IllegalArgumentException("Unknown refinement layer");
        long seed=number(query,"seed",42),x=number(query,"x",-8192),z=number(query,"z",-8192);
        Lattice.check(x);Lattice.check(z);
        int level=Math.toIntExact(number(query,"level",1));
        if(level<1||level>20)throw new IllegalArgumentException("Refinement parent level must be 1..20");
        long rain=number(query,"rain",11),inflow=number(query,"inflow",100);
        if(rain<0||inflow<0)throw new IllegalArgumentException("Runoff and inflow must be nonnegative");
        var params=new Params(Map.of("coarseSpacing",(double)number(query,"coarseSpacing",4096)));
        var ports=new BoundaryPorts(seed,params);var kernel=new DrainageRefinement(seed,params);
        long s=ports.spacing(level),i=Math.floorDiv(x,s),j=Math.floorDiv(z,s);
        x=Math.multiplyExact(i,s);z=Math.multiplyExact(j,s);
        Lattice.check(x);Lattice.check(z);Lattice.check(Math.addExact(x,Math.multiplyExact(2,s)));Lattice.check(Math.addExact(z,s));
        var west=ports.port(ports.face(level,i,j,4));var shared=ports.port(ports.face(level,i,j,2));var east=ports.port(ports.face(level,i+1,j,2));
        long aBudget=Math.addExact(rain,inflow),bBudget=Math.addExact(rain,aBudget);
        // B is evaluated first from the immutable parent ledger, not by generating A first.
        var b=kernel.refine(level,i+1,j,east,new DrainageRefinement.Inflow[]{new DrainageRefinement.Inflow(shared,aBudget)},rain,bBudget,costs(seed,level,i+1,j));
        var a=kernel.refine(level,i,j,shared,new DrainageRefinement.Inflow[]{new DrainageRefinement.Inflow(west,inflow)},rain,aBudget,costs(seed,level,i,j));
        return new Demo(seed,x,z,s,level,params.integer("coarseSpacing"),rain,inflow,a,b);
    }
    private static long number(Map<String,String> query,String key,long fallback){return Long.parseLong(query.getOrDefault(key,Long.toString(fallback)));}
    private static int[] costs(long seed,int level,long i,long j){
        int[] result=new int[4];for(int c=0;c<4;c++)result[c]=(int)Math.floorMod(Hash64.hash(Hash64.stream(seed,0x44454d4fL),level-1,i*2+c%2,j*2+c/2),1000);
        return result;
    }
    public static String json(Demo d) {
        StringBuilder out=new StringBuilder("{\"version\":\"").append(DrainageRefinement.VERSION)
            .append("\",\"fixture\":\"two-eastward-parents\",\"seed\":\"").append(d.seed).append("\",\"x\":\"").append(d.x)
            .append("\",\"z\":\"").append(d.z).append("\",\"spacing\":\"").append(d.spacing).append("\",\"level\":").append(d.level)
            .append(",\"coarseSpacing\":").append(d.baseSpacing).append(",\"rainPerParent\":\"").append(d.rain).append("\",\"externalInflow\":\"").append(d.inflow)
            .append("\",\"finalOutflow\":\"").append(d.b.outflow).append("\",\"parents\":[");
        boolean comma=false;
        for(var r:List.of(d.a,d.b)) {
            if(comma)out.append(',');comma=true;
            out.append("{\"i\":\"").append(r.i).append("\",\"j\":\"").append(r.j).append("\",\"root\":").append(r.root)
                .append(",\"rain\":\"").append(r.localRain).append("\",\"inflow\":\"").append(r.externalInflow).append("\",\"outflow\":\"").append(r.outflow).append("\",\"children\":[");
            for(int c=0;c<4;c++) {
                if(c!=0)out.append(',');var node=r.child(c);
                out.append("{\"index\":").append(c).append(",\"i\":\"").append(node.i).append("\",\"j\":\"").append(node.j)
                    .append("\",\"downstream\":").append(node.downstream).append(",\"resistance\":").append(node.resistance)
                    .append(",\"distance\":\"").append(node.distance).append("\",\"rain\":\"").append(node.localRain)
                    .append("\",\"externalInflow\":\"").append(node.externalInflow).append("\",\"outflow\":\"").append(node.outflow)
                    .append("\",\"exit\":").append(portJson(node.exit)).append('}');
            }
            out.append("],\"entries\":[");var entries=r.entries();
            for(int e=0;e<entries.length;e++){if(e!=0)out.append(',');out.append("{\"units\":\"").append(entries[e].units).append("\",\"port\":").append(portJson(entries[e].port)).append('}');}
            out.append("],\"exit\":").append(portJson(r.exit)).append('}');
        }
        return out.append("]}").toString();
    }
    private static String portJson(BoundaryPorts.Port p){return "{\"x\":\""+p.x+"\",\"z\":\""+p.z+"\",\"level\":"+p.owner.level+",\"axis\":"+p.owner.axis+",\"i\":\""+p.owner.i+"\",\"j\":\""+p.owner.j+"\"}";}
    private static long value(DrainageRefinement.Child c,String layer){return switch(layer){case "rain"->c.localRain;case "inflow"->c.externalInflow;case "flux"->c.outflow;case "cost"->c.resistance;case "distance"->c.distance;default->0;};}
    public static BufferedImage render(Demo d,String layer) {
        if(!LAYERS.contains(layer))throw new IllegalArgumentException("Unknown refinement layer");
        var image=new BufferedImage(1008,624,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x10181b));g.fillRect(0,0,image.getWidth(),image.getHeight());
        g.setFont(new Font("SansSerif",Font.BOLD,18));g.setColor(new Color(0xe3e9e5));g.drawString("CONDITIONED REFINEMENT / "+layer.toUpperCase(java.util.Locale.ROOT),28,32);
        g.setFont(new Font("SansSerif",Font.PLAIN,13));g.drawString("Seed "+d.seed+" | parent level "+d.level+" | synthetic resistance | explicit two-parent fixture",28,57);
        long max=1;for(var r:List.of(d.a,d.b))for(var c:r.children())max=Math.max(max,value(c,layer));
        for(int parent=0;parent<2;parent++) {
            var r=parent==0?d.a:d.b;
            g.setColor(new Color(0xc8d8cb));g.drawString((parent==0?"PARENT A":"PARENT B")+"  ("+r.i+", "+r.j+")",100+parent*384,88);
            for(int c=0;c<4;c++) {
                var child=r.child(c);int cx=96+parent*384+(c%2)*192,cz=108+c/2*192;
                double t=Math.log1p(value(child,layer))/Math.log1p(max);
                g.setColor(new Color((int)(23+30*t),(int)(42+62*t),(int)(47+65*t)));g.fillRect(cx,cz,192,192);
                g.setColor(new Color(0x405457));g.setStroke(new BasicStroke(1));g.drawRect(cx,cz,192,192);
                g.setFont(new Font("Monospaced",Font.PLAIN,12));g.setColor(new Color(0xe0e8de));
                g.drawString((parent==0?"A":"B")+c+"  "+(c==r.root?"EXIT CHILD":"-> "+child.downstream),cx+12,cz+24);
                g.drawString(layer.equals("network")?"rain "+child.localRain:layer+" "+value(child,layer),cx+12,cz+44);
                g.drawString("out "+child.outflow,cx+12,cz+170);
            }
        }
        for(var r:List.of(d.a,d.b))for(var child:r.children()) {
            long half=d.spacing/2;long hx=child.i*half+half/2,hz=child.j*half+half/2;
            int ax=px(d,hx),az=pz(d,hz),bx=px(d,child.exit.x),bz=pz(d,child.exit.z);
            g.setStroke(new BasicStroke(child.outflow==0?1:3));g.setColor(new Color(child.outflow==0?0x516669:0x68dce7));
            arrow(g,ax,az,bx,bz);
            if(child.downstream>=0) {
                var target=r.child(child.downstream);arrow(g,bx,bz,px(d,target.i*half+half/2),pz(d,target.j*half+half/2));
            }
            g.setColor(new Color(0xdceee8));g.fillOval(ax-5,az-5,10,10);
            g.setColor(new Color(child.downstream<0?0xffd17b:0x78aca7));g.fillOval(bx-4,bz-4,8,8);
        }
        for(var r:List.of(d.a,d.b))for(var entry:r.entries()) {
            long half=d.spacing/2;
            for(var child:r.children()) {
                long x=child.i*half,z=child.j*half;
                if(entry.port.x>=x&&entry.port.x<=x+half&&entry.port.z>z&&entry.port.z<z+half) {
                    g.setColor(new Color(0xffd17b));g.setStroke(new BasicStroke(3));
                    arrow(g,px(d,entry.port.x),pz(d,entry.port.z),px(d,x+half/2),pz(d,z+half/2));break;
                }
            }
        }
        g.setColor(new Color(0xffd17b));g.setStroke(new BasicStroke(2));
        int enter=pz(d,d.a.entries()[0].port.z),leave=pz(d,d.b.exit.z);
        arrow(g,40,enter,96,enter);arrow(g,864,leave,940,leave);
        g.setFont(new Font("Monospaced",Font.PLAIN,12));g.drawString("IN",40,enter-12);g.drawString("OUT",903,leave-12);
        g.setColor(new Color(0xffd17b));g.drawOval(px(d,d.a.exit.x)-8,pz(d,d.a.exit.z)-8,16,16);
        g.setColor(new Color(0xa1b5b5));g.setFont(new Font("SansSerif",Font.PLAIN,13));
        g.drawString("Gold = inherited ports/transfers. Cyan = child routing. All child routes stay inside their parent.",28,527);
        g.drawString("A: "+d.inflow+" incoming + "+d.rain+" local = "+d.a.outflow+" transferred to B",28,553);
        g.drawString("B: "+d.a.outflow+" incoming + "+d.rain+" local = "+d.b.outflow+" outgoing",28,577);
        g.drawString("Boundary commitments are supplied, not inferred from this picture. Not a global river/continent generator.",28,607);
        g.dispose();return image;
    }
    private static int px(Demo d,long x){return 96+(int)Math.round((x-d.x)*(384.0/d.spacing));}
    private static int pz(Demo d,long z){return 108+(int)Math.round((z-d.z)*(384.0/d.spacing));}
    private static void arrow(java.awt.Graphics2D g,int ax,int az,int bx,int bz) {
        g.drawLine(ax,az,bx,bz);double angle=Math.atan2(bz-az,bx-ax),at=.7;
        int x=(int)Math.round(ax+(bx-ax)*at),z=(int)Math.round(az+(bz-az)*at);
        g.drawLine(x,z,x-(int)Math.round(8*Math.cos(angle-.5)),z-(int)Math.round(8*Math.sin(angle-.5)));
        g.drawLine(x,z,x-(int)Math.round(8*Math.cos(angle+.5)),z-(int)Math.round(8*Math.sin(angle+.5)));
    }
    public static void writeDiagnostics() throws java.io.IOException {
        Files.createDirectories(Path.of("build/gallery"));var d=create(Map.of());
        for(String layer:LAYERS)ImageIO.write(render(d,layer),"png",Path.of("build/gallery/refinement-"+layer+".png").toFile());
    }
}
