package pl.infectedmorph.domain;

public enum InfectionStage {
    NORMAL, STAGE1, STAGE2;

    public String command() { return name().toLowerCase(java.util.Locale.ROOT); }
    public String label() { return this == STAGE2 ? "Stage 2" : this == STAGE1 ? "Stage 1" : "NORMAL"; }
}
