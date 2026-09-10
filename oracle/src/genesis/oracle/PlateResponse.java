package genesis.oracle;

import genesis.oracle.IrregularPlates.Site;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Bounded kinematic broad-relief proxy, not an elastic or time-integrated plate solver. */
public final class PlateResponse {
    public record Response(double datum,double tiltX,double tiltZ) {}
    private PlateResponse() {}

    /** Relative motion only. Fixed support and canonical summation make query order irrelevant. */
    public static Response solve(Site owner,List<Site> neighbors,int spacing) {
        var ordered=new ArrayList<>(neighbors);ordered.sort(Comparator.comparing(Site::id,Long::compareUnsigned));
        double datum=0,tx=0,tz=0,total=0;
        for(Site other:ordered) {
            if(other.id()==owner.id())continue;
            double dx=(other.x()-owner.x())/(double)spacing,dz=(other.z()-owner.z())/(double)spacing;
            double distance=StrictMath.hypot(dx,dz);if(distance==0||distance>=2)continue;
            double nx=dx/distance,nz=dz/distance,t=1-distance/2,w=t*t*t;
            double normal=((owner.vx()-other.vx())*nx+(owner.vz()-other.vz())*nz)/64.0;
            // Compression lifts the nearby side; extension lowers it. Opposing
            // compression adds to mean height but cancels the directional tilt.
            datum+=w*normal;tx+=w*normal*nx;tz+=w*normal*nz;total+=w;
        }
        return total==0?new Response(0,0,0):new Response(datum/total,tx/total,tz/total);
    }
}
