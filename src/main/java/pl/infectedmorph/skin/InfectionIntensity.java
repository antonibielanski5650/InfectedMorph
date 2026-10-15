package pl.infectedmorph.skin;

import java.util.Locale;

public enum InfectionIntensity {
    LOW(1, 1.25, .32, .48, .76),
    MEDIUM(1, 2.0, .48, .70, .55),
    HIGH(2, 2.6, .60, .86, .38);

    final int lesions;
    final double radius, bruiseOpacity, cutOpacity, eyeBrightness;
    InfectionIntensity(int lesions, double radius, double bruiseOpacity, double cutOpacity, double eyeBrightness) {
        this.lesions = lesions; this.radius = radius; this.bruiseOpacity = bruiseOpacity;
        this.cutOpacity = cutOpacity; this.eyeBrightness = eyeBrightness;
    }

    public static InfectionIntensity parse(String value) {
        if (value == null) return MEDIUM;
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public String configValue() { return name().toLowerCase(Locale.ROOT); }
}
