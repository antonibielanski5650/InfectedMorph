package pl.infectedmorph.skin;

public enum SkinModel {
    WIDE("classic", 4), SLIM("slim", 3);

    private final String variant;
    private final int armWidth;
    SkinModel(String variant, int armWidth) { this.variant = variant; this.armWidth = armWidth; }
    public String variant() { return variant; }
    public int armWidth() { return armWidth; }
}
