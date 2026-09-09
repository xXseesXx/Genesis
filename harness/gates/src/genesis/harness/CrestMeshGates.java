package genesis.harness;

import genesis.oracle.ActiveHydrology;
import genesis.oracle.CrestMesh;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import javax.imageio.ImageIO;

/** Spill preservation is checked against an independent D8 relaxation, not merely the shared heap. */
final class CrestMeshGates {
    private static void check(boolean ok,String message){ActiveHydrologyGates.check(ok,message);}
    public static void main(String[] args)throws Exception{run();}
    static void run()throws Exception {
        Random random=new Random(229381);
        for(int trial=0;trial<150;trial++) {
            int w=1+random.nextInt(6),h=1+random.nextInt(6),n=w*h;int[] bed=new int[n],east=ActiveHydrologyGates.emptyCrests(n),south=ActiveHydrologyGates.emptyCrests(n);
            boolean[] active=new boolean[n],term=new boolean[n];long[] rain=new long[n];
            for(int p=0;p<n;p++){bed[p]=random.nextInt(61)-30;active[p]=random.nextInt(5)!=0;term[p]=active[p]&&random.nextInt(7)==0;rain[p]=active[p]?random.nextInt(9):0;
                if(random.nextBoolean())east[p]=random.nextInt(121)-30;if(random.nextBoolean())south[p]=random.nextInt(121)-30;}
            var coarse=ActiveHydrologyGates.verify(w,h,bed,active,term,rain,east,south);
            var mesh=new CrestMesh(w,h,bed,active,term,rain,east,south);var fine=mesh.solve();verify(mesh,fine);
            for(int p=0;p<n;p++) {
                int q=mesh.original(p);check(coarse.resolved(p)==fine.resolved(q),"Refinement changes terminal reachability");
                if(coarse.resolved(p))check(coarse.filled(p)==fine.filled(q),"D8 refinement lowered or raised a coarse spill");
            }
            check(coarse.supplied==fine.supplied&&coarse.discharged==fine.discharged&&coarse.unresolved==fine.unresolved,"Refinement changed water ledger");
            geometry(mesh);
        }
        // Extreme height arithmetic and no source duplication at added vertices.
        var extreme=new CrestMesh(2,1,new int[]{Integer.MIN_VALUE,Integer.MAX_VALUE},new boolean[]{true,true},new boolean[]{false,true},
            new long[]{Long.MAX_VALUE,0},ActiveHydrologyGates.emptyCrests(2),ActiveHydrologyGates.emptyCrests(2));
        check(extreme.solve().discharged==Long.MAX_VALUE&&extreme.runoff(1)==0,"Extreme refinement budget");
        try{new CrestMesh(1024,1024,new int[0],new boolean[0],new boolean[0],new long[0],new int[0],new int[0]);throw new AssertionError("Oversized refinement accepted");}catch(IllegalArgumentException expected){}
        diagnostic();
        Files.writeString(Path.of("build/crest-mesh.json"),"""
            {
              "experiment":"crest-mesh-v1",
              "randomIndependentCases":150,
              "surface":"piecewise-linear triangles through original nodes, explicit crest midpoints and max-perimeter centers",
              "routing":"D8 on refined vertices; independent relaxation and source walks",
              "preserved":"coarse D4 minimax level/reachability at original vertices and total source ledger",
              "notPreserved":"receiver identity, individual outlet shares or flux at every intermediate coarse node",
              "diagnostic":{"intactSpill":80,"loweredPassSpill":30,"intactCrest":120,"loweredCrest":30},
              "scope":"one finite refinement, not arbitrary recursive or continuous-gradient hydrology"
            }
            """);
        System.out.println("PASS CREST MESH: 150 independent D8/source-walk cases, triangulated diagonal paths, coarse spill preservation, exact source ledger and lowered-pass detection; one finite refinement");
    }
    private static void verify(CrestMesh m,ActiveHydrology.Result r) {
        int n=m.size(),w=m.width;long[] level=new long[n];Arrays.fill(level,Long.MAX_VALUE);
        for(int p=0;p<n;p++)if(m.terminal(p))level[p]=m.bed(p);
        for(int iteration=0;iteration<n;iteration++){boolean change=false;
            for(int p=0;p<n;p++)if(m.active(p)&&!m.terminal(p))for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){
                if(dx==0&&dz==0)continue;int x=p%w+dx,z=p/w+dz;if(x<0||z<0||x>=w||z>=m.height)continue;
                int q=z*w+x;if(!m.active(q)||level[q]==Long.MAX_VALUE)continue;long candidate=Math.max(m.bed(p),level[q]);
                if(candidate<level[p]){level[p]=candidate;change=true;}}
            if(!change)break;
        }
        long[] flux=new long[n];
        for(int p=0;p<n;p++) {
            check(r.resolved(p)==(level[p]!=Long.MAX_VALUE),"D8 reachability differs from reference");
            if(r.resolved(p))check(r.filled(p)==level[p],"D8 spill differs from reference");
            int q=p,steps=0;while(true){flux[q]+=m.runoff(p);int next=r.downstream(q);if(next<0)break;
                check(Math.abs(q%w-next%w)<=1&&Math.abs(q/w-next/w)<=1&&q!=next&&m.active(next),"Nonlocal fine receiver");
                check(r.order(next)<r.order(q)&&r.filled(next)<=r.filled(q),"Fine receiver climbs/cycles");q=next;check(++steps<=n,"Fine cycle");}
            if(r.resolved(p))check(m.terminal(q),"Fine route has invented terminal");
        }
        for(int p=0;p<n;p++)check(r.flux(p)==flux[p],"Fine source walk mismatch");
    }
    private static void geometry(CrestMesh m) {
        for(int z=0;z<m.height-1;z++)for(int x=0;x<m.width-1;x++) {
            int p=z*m.width+x;
            for(int direction=0;direction<2;direction++) {
                int a=direction==0?p:p+m.width,b=direction==0?p+m.width+1:p+1;
                if(!m.active(a)||!m.active(b))continue;
                double prior=m.bed(a);
                for(int k=0;k<=16;k++) {
                    double value=m.surface(x,z,k*64,direction==0?k*64:1024-k*64);
                    check(value<=Math.max(m.bed(a),m.bed(b))&&value>=Math.min(m.bed(a),m.bed(b)),"D8 straight path hides a higher/lower surface obstacle");
                    check(m.bed(b)>=m.bed(a)?value>=prior:value<=prior,"Triangulated D8 path not monotone");prior=value;
                }
            }
        }
    }
    private static void diagnostic()throws Exception {
        int w=9,h=5,n=w*h;int[] bed=new int[n],east=ActiveHydrologyGates.emptyCrests(n),south=ActiveHydrologyGates.emptyCrests(n);
        boolean[] mask=new boolean[n],term=new boolean[n];Arrays.fill(mask,true);long[] rain=new long[n];
        for(int p=0;p<n;p++){int x=p%w;term[p]=x==0||x==8;bed[p]=term[p]?0:x==1?80:x<4?20:10;rain[p]=x==2||x==3?1:0;if(x==3)east[p]=120;}
        var intact=new CrestMesh(w,h,bed,mask,term,rain,east,south);var a=intact.solve();
        east[21]=30;var breached=new CrestMesh(w,h,bed,mask,term,rain,east,south);var b=breached.solve();
        check(a.filled(intact.original(21))==80&&b.filled(breached.original(21))==30,"Fine pass counterexample hidden by diagonal model");
        check(intact.bed(intact.original(21)+1)==120,"Mesh aliases caller's mutated crest array");
        BufferedImage image=new BufferedImage(1080,410,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        g.setColor(new Color(245,247,249));g.fillRect(0,0,1080,410);g.setColor(new Color(25,34,46));g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,20));
        g.drawString("From an edge contract to a sampled ridge surface",20,30);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));
        g.drawString("Cross-section through an actual refined row. D8 solve covers the whole 2D mesh, not just this section.",20,56);
        for(int panel=0;panel<2;panel++) {
            CrestMesh mesh=panel==0?intact:breached;var result=panel==0?a:b;int ox=25+panel*535;
            g.setColor(new Color(25,34,46));g.drawString(panel==0?"Intact crest 120: left basin spills west at 80":"Lowered pass 30: basin spills across the divide",ox,94);
            for(int series=0;series<2;series++) {
                g.setColor(series==0?new Color(151,103,62):new Color(38,116,181));int lastX=ox,lastY=330;
                for(int x=0;x<mesh.width;x++){int p=4*mesh.width+x,px=ox+x*29,py=330-(series==0?mesh.bed(p):result.filled(p))*17/10;
                    if(x>0)g.drawLine(lastX,lastY,px,py);g.fillOval(px-2,py-2,5,5);lastX=px;lastY=py;}
            }
        }
        g.setColor(new Color(25,34,46));g.drawString("Brown: explicit bed / crest vertices. Blue: eventual-overflow level. Added vertices receive zero additional runoff.",20,365);
        g.drawString("Coarse spill levels survive finer diagonal routing; individual receiver paths may change. Not finished terrain morphology.",20,391);
        g.dispose();Files.createDirectories(Path.of("build/gallery"));ImageIO.write(image,"png",Path.of("build/gallery/crest-mesh.png").toFile());
    }
}
