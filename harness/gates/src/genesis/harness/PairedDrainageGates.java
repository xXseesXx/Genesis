package genesis.harness;

import genesis.core.hash.Hash64;
import genesis.oracle.ActiveHydrology;
import genesis.oracle.MaritimeEnvelope.Key;
import genesis.oracle.MixedBoundaryEnvelope;
import genesis.oracle.MixedBoundaryLandmass;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Actual paired land masks with finite shared saddles; full-union solve has no owner restrictions. */
final class PairedDrainageGates {
    private static void check(boolean ok,String message){ActiveHydrologyGates.check(ok,message);}
    public static void main(String[] args)throws Exception{ActiveHydrologyGates.run();run();}
    static void run()throws Exception {
        notchCounterexample();StringBuilder rows=new StringBuilder();
        for(long seed:new long[]{42,-1,137}) {
            int spacing=524288,step=spacing/64;var e=new MixedBoundaryEnvelope(seed,spacing,spacing/32);
            Key first=null,second=null;
            for(int z=-1;z<=1&&first==null;z++)for(int x=-1;x<=1&&first==null;x++){
                Key key=new Key(x,z),other=e.partner(key);if(other!=null){first=key;second=other;}}
            check(first!=null,"No test ridge pair");
            var a=new MixedBoundaryLandmass(seed,spacing,spacing/32,first,53);
            var b=new MixedBoundaryLandmass(seed,spacing,spacing/32,second,53);
            long x0=Math.min(a.x0,b.x0),z0=Math.min(a.z0,b.z0);
            int w=(int)((Math.max(a.x0,b.x0)-x0)/step)+193,h=(int)((Math.max(a.z0,b.z0)-z0)/step)+193,n=w*h;
            int[] bed=new int[n],owner=new int[n],east=ActiveHydrologyGates.emptyCrests(n),south=ActiveHydrologyGates.emptyCrests(n);
            Arrays.fill(owner,-1);boolean[] active=new boolean[n],term=new boolean[n],land=new boolean[n];long[] source=new long[n];
            for(int p=0;p<n;p++) {
                long x=x0+p%w*(long)step,z=z0+p/w*(long)step;var sample=e.sample(x,z);
                MixedBoundaryLandmass cell=sample.owner().equals(first)?a:sample.owner().equals(second)?b:null;
                if(cell==null)continue;
                check(p%w>0&&p%w<w-1&&p/w>0&&p/w<h-1,"Active domain hits solve-box edge");
                owner[p]=cell==a?0:1;active[p]=true;int q=cell.index(x,z);land[p]=cell.land(q);term[p]=cell.water(q)==1;
                bed[p]=term[p]?0:land[p]?40+(int)Math.floorMod(Hash64.hash(seed,0,x,z),61)+cell.ridge(q)/8:-12;
                source[p]=land[p]?1+Math.floorMod(Hash64.hash(seed,1,x,z),9):0;
            }
            int ridgeEdges=0;
            for(int p=0;p<n;p++)if(active[p])for(int q:new int[]{p%w+1<w?p+1:-1,p/w+1<h?p+w:-1})
                if(q>=0&&active[q]&&owner[p]!=owner[q]&&!term[p]&&!term[q]) {
                    if(q==p+1)east[p]=600;else south[p]=600;ridgeEdges++;
                }
            check(ridgeEdges>0,"No active inter-cell ridge edges");
            long start=System.nanoTime();var full=ActiveHydrology.solve(w,h,bed,active,term,source,east,south);
            double ms=(System.nanoTime()-start)/1e6;
            long combinedDischarge=0;long[] combinedFlux=new long[n];
            for(int which=0;which<2;which++) {
                boolean[] ownActive=new boolean[n];long[] ownSource=new long[n];
                for(int p=0;p<n;p++){ownActive[p]=term[p]||owner[p]==which;ownSource[p]=owner[p]==which?source[p]:0;}
                var local=ActiveHydrology.solve(w,h,bed,ownActive,term,ownSource,east,south);combinedDischarge+=local.discharged;
                check(local.unresolved==0,"Local paired cell lacks complete terminal routing");
                for(int p=0;p<n;p++) {
                    combinedFlux[p]+=local.flux(p);
                    if(owner[p]==which&&!term[p])check(full.filled(p)==local.filled(p)&&full.downstream(p)==local.downstream(p),"Union terrain changed local spill/receiver");
                }
            }
            for(int p=0;p<n;p++) {
                check(full.flux(p)==combinedFlux[p],"Union flux differs from independent cells");
                int q=full.downstream(p);
                if(q>=0&&!term[p]&&!term[q])check(owner[p]==owner[q],"Unrestricted union flow crosses the physical ridge");
                if(active[p]&&!term[p])check(full.filled(p)<600,"Lake overtops assumed divide");
            }
            check(full.unresolved==0&&full.discharged==combinedDischarge&&full.discharged==full.supplied,"Paired water ledger");
            var cold=ActiveHydrology.solve(w,h,bed,active,term,source,east,south);
            for(int p=n-1;p>=0;p-=7)check(full.downstream(p)==cold.downstream(p)&&full.flux(p)==cold.flux(p),"Cold/reverse-stride paired reads differ");
            if(!rows.isEmpty())rows.append(",\n");
            rows.append(String.format(Locale.ROOT,"    {\"seed\":%d,\"first\":[%d,%d],\"second\":[%d,%d],\"vertices\":%d,\"ridgeEdges\":%d,\"supplied\":%d,\"discharged\":%d,\"unresolved\":%d,\"fillDepthSum\":%d,\"unionSolveMs\":%.3f}",
                seed,first.i(),first.j(),second.i(),second.j(),n,ridgeEdges,full.supplied,full.discharged,full.unresolved,full.fillDepthSum,ms));
            if(seed==42)render(w,h,bed,owner,land,term,east,south,full);
        }
        Files.createDirectories(Path.of("build/gallery"));Files.writeString(Path.of("build/paired-drainage.json"),"""
            {
              "experiment":"paired-drainage-v1",
              "model":"finite D4 node beds and explicit shared edge crests; synthetic forcing, not a block terrain",
              "ridgeCrest":600,
              "rawBedRange":[-12,228],
              "independentLocalUnionAgreement":true,
              "loweredPassCounterexampleDetected":true,
              "audits":[
            %s
              ]
            }
            """.formatted(rows));
        System.out.println("PASS PAIRED DRAINAGE: 3 actual ridge pairs, unrestricted union equals local solves for spill/receivers/exact flux; lowered-pass leak detected; finite D4 saddle model only");
    }
    private static void notchCounterexample() {
        int w=9,h=5,n=w*h;int[] bed=new int[n],east=ActiveHydrologyGates.emptyCrests(n),south=ActiveHydrologyGates.emptyCrests(n);
        boolean[] active=new boolean[n],term=new boolean[n];Arrays.fill(active,true);long[] rain=new long[n];
        for(int p=0;p<n;p++){int x=p%w;term[p]=x==0||x==8;bed[p]=term[p]?0:x==1?80:x<4?20:10;
            rain[p]=x==2||x==3?1:0;if(x==3)east[p]=120;}
        var intact=ActiveHydrologyGates.verify(w,h,bed,active,term,rain,east,south);
        check(intact.filled(2*w+3)==80,"Analytic left basin spill should be 80");
        east[2*w+3]=30;
        var breached=ActiveHydrologyGates.verify(w,h,bed,active,term,rain,east,south);
        check(breached.filled(2*w+3)==30&&breached.downstream(2*w+3)==2*w+4&&breached.flux(2*w+3)>0,
            "Lowering a shared pass must reveal cross-divide spill");
        boolean[] left=active.clone();for(int p=0;p<n;p++)if(p%w>=4&&!term[p])left[p]=false;
        var falselyIndependent=ActiveHydrology.solve(w,h,bed,left,term,rain,east,south);
        check(falselyIndependent.filled(2*w+3)!=breached.filled(2*w+3),"Owner mask concealed a physical leak without detection");
    }
    private static void render(int w,int h,int[] bed,int[] owner,boolean[] land,boolean[] term,int[] east,int[] south,ActiveHydrology.Result r)throws Exception {
        BufferedImage image=new BufferedImage(1040,585,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        g.setColor(new Color(245,247,249));g.fillRect(0,0,1040,585);g.setColor(new Color(25,34,46));g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,19));
        g.drawString("Shared ridge: independent cells agree with an unrestricted combined solve",20,28);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));
        g.drawString("Seed 42 | synthetic beds / runoff | D4 graph with finite 600-unit crest saddles, not Minecraft terrain",20,53);
        int minX=w,minZ=h,maxX=0,maxZ=0;
        for(int p=0;p<owner.length;p++)if(owner[p]>=0){minX=Math.min(minX,p%w);maxX=Math.max(maxX,p%w);minZ=Math.min(minZ,p/w);maxZ=Math.max(maxZ,p/w);}
        int cw=maxX-minX+1,ch=maxZ-minZ+1;
        for(int panel=0;panel<2;panel++) {
            BufferedImage map=new BufferedImage(cw,ch,BufferedImage.TYPE_INT_RGB);
            for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++){int p=z*w+x;Color c=new Color(222,226,231);
                if(owner[p]>=0){if(term[p])c=new Color(35,77,123);else if(panel==0){int v=Math.min(100,Math.max(0,bed[p])/2);c=new Color(85+v,118+v/2,72+v/3);}
                    else c=owner[p]==0?new Color(174,189,132):new Color(149,184,161);
                    if(panel==1&&!term[p]&&r.flux(p)>=30){int v=(int)Math.min(120,Math.log1p(r.flux(p))*13);c=new Color(25,80+v/2,120+v);}
                    if(east[p]==600||south[p]==600)c=new Color(180,104,56);}
                map.setRGB(x-minX,z-minZ,c.getRGB());}
            double scale=Math.min(480.0/cw,425.0/ch);int ox=20+panel*510;
            g.setColor(new Color(25,34,46));g.drawString(panel==0?"Synthetic bed samples + shared crest edges":"Accumulated net runoff + cell ownership",ox,87);
            g.drawImage(map,ox,100,(int)(cw*scale),(int)(ch*scale),null);
        }
        g.setColor(new Color(25,34,46));g.drawString("Orange marks crest edges (schematic). Blue lines show flux >=30; they are not width-calibrated river channels.",20,548);
        g.drawString("Lake filling models eventual overflow. Lowering a test pass exposes a real cross-cell leak; owner masks cannot hide it.",20,573);
        g.dispose();Files.createDirectories(Path.of("build/gallery"));ImageIO.write(image,"png",Path.of("build/gallery/paired-drainage.png").toFile());
    }
}
