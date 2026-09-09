package genesis.oracle;

/** One finite refinement: original nodes, explicit crest midpoints, high cell centers.
 * Triangles join the two edge midpoints in each refined quad. This realizes graph saddles as
 * piecewise-linear surface ridges without lowering coarse D4 minimax barriers under D8 sampling.
 * This is conservative, angular geometry, not finished hillslopes or recursive world refinement.
 */
public final class CrestMesh {
    public static final String VERSION="crest-mesh-v1";
    public final int width,height;
    private final int coarseWidth,coarseSize;
    private final int[] bed;
    private final boolean[] active,terminals;
    private final long[] runoff;
    public CrestMesh(int w,int h,int[] terrain,boolean[] mask,boolean[] ends,long[] rain,int[] east,int[] south) {
        long fw=2L*w-1,fh=2L*h-1;
        if(w<1||h<1||fw>1048576||fh>1048576||fw*fh>1048576)throw new IllegalArgumentException("Refined mesh exceeds finite budget");
        int n=w*h;
        if(terrain==null||mask==null||ends==null||rain==null||east==null||south==null||terrain.length!=n||mask.length!=n||ends.length!=n||rain.length!=n||east.length!=n||south.length!=n)
            throw new IllegalArgumentException("Mismatched coarse inputs");
        width=(int)fw;height=(int)fh;coarseWidth=w;coarseSize=n;
        bed=new int[width*height];active=new boolean[bed.length];terminals=new boolean[bed.length];runoff=new long[bed.length];
        long total=0;
        for(int p=0;p<n;p++) {
            if(rain[p]<0||!mask[p]&&(ends[p]||rain[p]!=0))throw new IllegalArgumentException("Invalid coarse source/terminal");
            total=Math.addExact(total,rain[p]);int q=original(p);bed[q]=terrain[p];active[q]=mask[p];terminals[q]=ends[p];runoff[q]=rain[p];
            if(p%w+1<w&&mask[p]&&mask[p+1]){bed[q+1]=Math.max(east[p],Math.max(terrain[p],terrain[p+1]));active[q+1]=true;}
            if(p/w+1<h&&mask[p]&&mask[p+w]){bed[q+width]=Math.max(south[p],Math.max(terrain[p],terrain[p+w]));active[q+width]=true;}
        }
        for(int z=0;z<h-1;z++)for(int x=0;x<w-1;x++) {
            int p=z*w+x,q=original(p)+width+1;
            if(mask[p]&&mask[p+1]&&mask[p+w]&&mask[p+w+1]) {
                active[q]=true;bed[q]=Math.max(Math.max(bed[q-1],bed[q+1]),Math.max(bed[q-width],bed[q+width]));
            }
        }
    }
    public int size(){return bed.length;}
    public int original(int p){if(p<0||p>=coarseSize)throw new IllegalArgumentException("Invalid coarse index");return 2*(p/coarseWidth)*width+2*(p%coarseWidth);}
    public int bed(int p){return bed[p];}
    public boolean active(int p){return active[p];}
    public boolean terminal(int p){return terminals[p];}
    public long runoff(int p){return runoff[p];}
    public ActiveHydrology.Result solve(){return ActiveHydrology.solveD8Surface(width,height,bed,active,terminals,runoff);}
    /** Exact integer barycentric numerator/Q interpolation inside a refined quad, Q=1024.
     * Triangles with a missing positively weighted vertex are outside the modeled surface.
     */
    public double surface(int x,int z,int u,int v) {
        if(x<0||z<0||x+1>=width||z+1>=height||u<0||v<0||u>1024||v>1024)throw new IllegalArgumentException("Invalid triangle sample");
        int p=z*width+x;int[] vertices;int[] weights;
        if(((x+z)&1)==0) {
            if(u+v<=1024){vertices=new int[]{p,p+1,p+width};weights=new int[]{1024-u-v,u,v};}
            else{vertices=new int[]{p+width+1,p+1,p+width};weights=new int[]{u+v-1024,1024-v,1024-u};}
        }else {
            if(v<=u){vertices=new int[]{p,p+1,p+width+1};weights=new int[]{1024-u,u-v,v};}
            else{vertices=new int[]{p,p+width,p+width+1};weights=new int[]{1024-v,v-u,u};}
        }
        long value=0;for(int k=0;k<3;k++){if(weights[k]>0&&!active[vertices[k]])throw new IllegalArgumentException("Outside active triangles");value+=(long)bed[vertices[k]]*weights[k];}
        return value/1024.0;
    }
}
