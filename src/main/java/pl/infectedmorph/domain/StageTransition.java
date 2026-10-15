package pl.infectedmorph.domain;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Restore NORMAL before preparing another stage; never capture the infected live appearance. */
public final class StageTransition {
    private StageTransition() {}

    public static <T> T restoreBeforeTransition(Supplier<T> originalSnapshot, Runnable restoreNormal,
                                               BooleanSupplier recoveryComplete) {
        T original = java.util.Objects.requireNonNull(originalSnapshot.get(), "Original appearance is missing.");
        restoreNormal.run();
        if (!recoveryComplete.getAsBoolean()) throw new IllegalStateException("Appearance or movement recovery is pending.");
        return original;
    }
}
