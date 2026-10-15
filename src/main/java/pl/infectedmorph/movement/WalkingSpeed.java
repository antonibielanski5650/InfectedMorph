package pl.infectedmorph.movement;

/** Vanilla steady-state ground model at 20 TPS, full forward input, ordinary friction 0.6. */
public final class WalkingSpeed {
    public static final double TARGET_BLOCKS_PER_SECOND = 4.0;
    public static final double TARGET_ATTRIBUTE = TARGET_BLOCKS_PER_SECOND * (1.0 - .6 * .91) / (20.0 * .98);

    private WalkingSpeed() {}

    public static double modifierAmount(double unrestricted) {
        if (!Double.isFinite(unrestricted) || unrestricted <= 0) {
            throw new IllegalArgumentException("Stage 1 requires a positive, finite unrestricted movement speed.");
        }
        double amount = TARGET_ATTRIBUTE / unrestricted - 1.0;
        if (!Double.isFinite(amount)) throw new IllegalArgumentException("Movement-speed calibration is outside the supported range.");
        return amount;
    }

    public static double estimateBlocksPerSecond(double effectiveAttribute) {
        return effectiveAttribute * .98 / (1.0 - .6 * .91) * 20.0;
    }
}
