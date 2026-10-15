package pl.infectedmorph.domain;

/** A server-profile confirmation is required before movement; client rendering is never inferred. */
public final class ActivationTransaction {
    public interface Step { void run(); }
    public enum Outcome { ACTIVE, ROLLED_BACK, RECOVERY_REQUIRED }
    public record Result(Outcome outcome, RuntimeException failure) {}

    private ActivationTransaction() {}

    public static Result activate(Step applyAppearance, Step verifyAppearance, Step activateMovement,
                                  Step deactivateMovement, Step restoreAppearance) {
        try {
            applyAppearance.run();
            verifyAppearance.run();
            activateMovement.run();
            return new Result(Outcome.ACTIVE, null);
        } catch (RuntimeException failure) {
            boolean recovered = true;
            try { deactivateMovement.run(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); recovered = false; }
            try { restoreAppearance.run(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); recovered = false; }
            return new Result(recovered ? Outcome.ROLLED_BACK : Outcome.RECOVERY_REQUIRED, failure);
        }
    }
}
