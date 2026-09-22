package genesis.oracle;

/**
 * Immutable experimental controls for the coupled climate, ground, erosion and river pipeline.
 * Integer units keep viewer URLs exact and reproducible; production callers use {@link #defaults()}.
 */
public record HydrologyTuning(
    int samplesPerPlate,
    int climatePasses,
    int traceCellsPermille,
    int initialHumidityPermille,
    int baseCondensationPermille,
    int orographicCondensationPermille,
    int recyclingPermille,
    int rainfallBasePermille,
    int rainfallHumidityPermille,
    int rainfallLiftPermille,
    int soilDepthGainPermille,
    int infiltrationBasePermille,
    int infiltrationSoilPermille,
    int infiltrationHumidityLossPermille,
    int wetEvapotranspirationPermille,
    int dryEvapotranspirationBonusPermille,
    int slopeRunoffPermille,
    int fullSlopeRunoffPermille,
    int erosionPasses,
    boolean massWasting,
    int massWastingPasses,
    int softStableAngle,
    int hardStableAngle,
    int bankfullMultiplierPercent,
    int channelWidthPercent,
    int minimumChannelCells,
    int meanderLimitPermille,
    boolean braidedChannels,
    boolean dendriticRelief,
    int dendriticStrengthPercent) {

    public HydrologyTuning {
        range("samplesPerPlate",samplesPerPlate,16,128);range("climatePasses",climatePasses,1,64);
        range("traceCellsPermille",traceCellsPermille,250,4000);range("initialHumidityPermille",initialHumidityPermille,0,1000);
        range("baseCondensationPermille",baseCondensationPermille,0,200);range("orographicCondensationPermille",orographicCondensationPermille,0,8000);
        range("recyclingPermille",recyclingPermille,0,100);range("rainfallBasePermille",rainfallBasePermille,0,1000);
        range("rainfallHumidityPermille",rainfallHumidityPermille,0,2000);range("rainfallLiftPermille",rainfallLiftPermille,0,40000);
        range("soilDepthGainPermille",soilDepthGainPermille,0,12000);range("infiltrationBasePermille",infiltrationBasePermille,0,1000);
        range("infiltrationSoilPermille",infiltrationSoilPermille,0,250);range("infiltrationHumidityLossPermille",infiltrationHumidityLossPermille,0,1000);
        range("wetEvapotranspirationPermille",wetEvapotranspirationPermille,0,800);range("dryEvapotranspirationBonusPermille",dryEvapotranspirationBonusPermille,0,800);
        range("slopeRunoffPermille",slopeRunoffPermille,0,1000);range("fullSlopeRunoffPermille",fullSlopeRunoffPermille,10,1000);
        range("erosionPasses",erosionPasses,1,16);range("massWastingPasses",massWastingPasses,1,16);
        range("softStableAngle",softStableAngle,5,80);range("hardStableAngle",hardStableAngle,softStableAngle,89);
        range("bankfullMultiplierPercent",bankfullMultiplierPercent,100,5000);range("channelWidthPercent",channelWidthPercent,10,3000);
        range("minimumChannelCells",minimumChannelCells,1,1_000_000);range("meanderLimitPermille",meanderLimitPermille,0,500);
        range("dendriticStrengthPercent",dendriticStrengthPercent,0,300);
    }

    public static HydrologyTuning defaults() {
        return new HydrologyTuning(64,24,2000,480,18,2700,8,140,720,18000,
            6400,450,75,520,160,200,180,120,6,true,4,28,62,
            1200,800,16_000,220,true,true,100);
    }

    public double traceCells(){return traceCellsPermille/1000.0;}
    public double bankfullMultiplier(){return bankfullMultiplierPercent/100.0;}
    public double channelWidthMultiplier(){return channelWidthPercent/100.0;}
    public double meanderLimit(){return meanderLimitPermille/1000.0;}
    public double dendriticStrength(){return dendriticStrengthPercent/100.0;}

    private static void range(String name,int value,int low,int high) {
        if(value<low||value>high)throw new IllegalArgumentException(name+" must be "+low+".."+high);
    }
}
