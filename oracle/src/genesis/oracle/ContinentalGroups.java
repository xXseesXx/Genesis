package genesis.oracle;

import genesis.core.hash.Hash64;
import genesis.core.tectonics.PlateTopology;
import java.util.ArrayList;
import java.util.List;

/**
 * Exact finite plate families for continental-crust bodies. Every plate belongs
 * to one group contained in a 2x2 lattice tile; group size is therefore 1..4.
 * Singles dominate the seeded pattern distribution.
 */
public final class ContinentalGroups {
    public static final String VERSION="continental-groups-v1";
    private static final long DOMAIN=0x434f4e5447524f55L;
    private final long seed;
    public record Cell(long i,long j) {}
    public record Group(long tileI,long tileJ,int part,long id,List<Cell> members) {
        public Group {members=List.copyOf(members);if(members.isEmpty()||members.size()>4)throw new IllegalArgumentException("Invalid continent group");}
        public int size(){return members.size();}
    }
    public ContinentalGroups(long worldSeed){seed=Hash64.stream(worldSeed,DOMAIN);}

    public Group group(long i,long j) {
        long ti=Math.floorDiv(i,2),tj=Math.floorDiv(j,2);int ax=Math.floorMod(i,2),az=Math.floorMod(j,2);
        long h=Hash64.hash(seed,0,ti,tj);int roll=(int)Math.floorMod(h,100),rotation=(int)Math.floorMod(Hash64.mix(h),4);
        int canonical=inverseRotate(ax,az,rotation),pattern=roll<60?0:roll<75?1:roll<90?2:roll<96?3:4;
        int part=switch(pattern){case 0->canonical;case 1->canonical/2;case 2->canonical%2;case 3->canonical==3?1:0;default->0;};
        var members=new ArrayList<Cell>();
        for(int c=0;c<4;c++)if(partOf(pattern,c)==part) {
            int[] actual=rotate(c%2,c/2,rotation);members.add(new Cell(2*ti+actual[0],2*tj+actual[1]));
        }
        // An exact representative plate key identifies the disjoint member set.
        // Hashes are used for appearance only, never for identity equality.
        long id=PlateTopology.key(members.get(0).i(),members.get(0).j());
        for(var cell:members){long key=PlateTopology.key(cell.i(),cell.j());if(Long.compareUnsigned(key,id)<0)id=key;}
        return new Group(ti,tj,part,id,members);
    }
    private static int partOf(int pattern,int c){return switch(pattern){case 0->c;case 1->c/2;case 2->c%2;case 3->c==3?1:0;default->0;};}
    private static int inverseRotate(int x,int z,int rotation) {
        for(int k=0;k<rotation;k++){int n=x;x=z;z=1-n;}return z*2+x;
    }
    private static int[] rotate(int x,int z,int rotation) {
        for(int k=0;k<rotation;k++){int n=x;x=1-z;z=n;}return new int[]{x,z};
    }
}
