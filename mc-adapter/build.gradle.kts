
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Compile the existing generator in place; no algorithm copies or lab runtime.
val terrain by sourceSets.creating {
    java.srcDir("../core/src/main/java")
    java.srcDir("../oracle/src")
    java.include("genesis/core/**")
    listOf("TectonicTerrain", "IrregularPlates", "ContinentalGroups", "PlateResponse",
        "BoundaryForcing", "JunctionForcing", "ContinentalHydrology", "ActiveHydrology",
        "ClimateField", "FluvialNetwork", "HydraulicErosion", "MassWasting", "RainfallField",
        "TerrainHardness", "TerrainSubstrate", "WindField").forEach {
        java.include("genesis/oracle/$it.java")
    }
}
sourceSets.main {
    compileClasspath += terrain.output
    runtimeClasspath += terrain.output
}
sourceSets.test {
    compileClasspath += terrain.output
    runtimeClasspath += terrain.output
}
tasks.jar { from(terrain.output) }
tasks.named<Jar>("sourcesJar") { from(terrain.allSource) }
dependencies { testImplementation("junit:junit:4.13.2") }
tasks.test { useJUnit() }

// The legacy launcher uses ASM 5, which silently rejects Java 21 mod classes.
// Keep the familiar entry points, but run with GTNH's modern bootstrap.
tasks.named("runClient") {
    dependsOn("runClient21")
    enabled = false
}
tasks.named("runServer") {
    dependsOn("runServer21")
    enabled = false
}
