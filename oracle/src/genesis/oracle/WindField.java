package genesis.oracle;

import genesis.core.hash.Hash64;
import genesis.core.hash.Lattice;

/**
 * Continuous, non-stagnating two-dimensional wind made from an analytic stream function.
 *
 * <p>The velocity is {@code (d psi / dz, -d psi / dx)}.  Its divergence therefore cancels
 * analytically, so streamlines have no procedural sources or sinks.  A prevailing component
 * is deliberately stronger than all vortical modes combined, which also keeps the velocity
 * away from zero and makes backwards moisture tracing bounded and unambiguous.</p>
 */
public final class WindField {
    public static final String VERSION="wind-field-v1";
    private static final long DOMAIN=0x57494e444649454cL;
    private static final double TWO_PI=StrictMath.PI*2;
    private static final int[] WAVE_X={1,2,1};
    private static final int[] WAVE_Z={2,-1,-3};
    private static final int[] PERIOD_MULTIPLIER={16,10,6};
    private static final double[] AMPLITUDE={.18,.13,.09};

    public record Wind(double x,double z,double speed,double directionRadians) {
        public Wind {
            if(!Double.isFinite(x)||!Double.isFinite(z)||!Double.isFinite(speed)||speed<=0
                ||!Double.isFinite(directionRadians))throw new IllegalArgumentException("Invalid wind sample");
        }
        public double unitX(){return x/speed;}
        public double unitZ(){return z/speed;}
    }

    private final long seed;
    private final int scale;
    private final double prevailingX,prevailingZ;
    private final double[] phase=new double[AMPLITUDE.length];

    public WindField(long worldSeed,int scale) {
        if(scale<16||scale>1_048_576)throw new IllegalArgumentException("Wind scale outside supported range");
        this.seed=Hash64.stream(worldSeed,DOMAIN);this.scale=scale;
        double orientation=unit(Hash64.mix(seed))*TWO_PI;
        prevailingX=StrictMath.cos(orientation);prevailingZ=StrictMath.sin(orientation);
        for(int k=0;k<phase.length;k++)phase[k]=unit(Hash64.mix(seed+(k+1L)*0x9e3779b97f4a7c15L))*TWO_PI;
    }

    public int scale(){return scale;}

    public Wind sample(long x,long z) {
        Lattice.check(x);Lattice.check(z);double vx=prevailingX,vz=prevailingZ;
        for(int k=0;k<AMPLITUDE.length;k++) {
            long period=Math.multiplyExact((long)scale,PERIOD_MULTIPLIER[k]);
            double fx=Math.floorMod(x,period)/(double)period,fz=Math.floorMod(z,period)/(double)period;
            double angle=TWO_PI*(WAVE_X[k]*fx+WAVE_Z[k]*fz)+phase[k];
            double length=StrictMath.hypot(WAVE_X[k],WAVE_Z[k]);
            double oscillation=AMPLITUDE[k]*StrictMath.cos(angle);
            vx+=oscillation*WAVE_Z[k]/length;
            vz-=oscillation*WAVE_X[k]/length;
        }
        double speed=StrictMath.hypot(vx,vz);
        return new Wind(vx,vz,speed,StrictMath.atan2(vz,vx));
    }

    /** Sum of modal amplitudes is .40, so the unit prevailing flow leaves this strict floor. */
    public static double minimumSpeed(){return 1-ArraysSum.AMPLITUDE_SUM;}

    private static double unit(long hash){return (hash>>>11)*0x1.0p-53;}

    /** Holder avoids a mutable public array while retaining a compile-time-independent check. */
    private static final class ArraysSum {
        static final double AMPLITUDE_SUM=sum();
        private static double sum(){double result=0;for(double value:AMPLITUDE)result+=value;return result;}
    }
}
