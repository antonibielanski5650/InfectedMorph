package pl.infectedmorph.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StageTransitionTest {
    @Test void capturesOriginalBeforeRestorationAndNeverUsesTheInfectedLiveTexture() {
        String[] stored = {"original"}, live = {"stage1"};
        String result = StageTransition.restoreBeforeTransition(() -> stored[0], () -> {
            live[0] = stored[0]; stored[0] = null;
        }, () -> live[0].equals("original"));
        assertEquals("original", result);
        live[0] = "stage2";
        assertEquals("original", StageTransition.restoreBeforeTransition(() -> result, () -> live[0] = result,
                () -> live[0].equals("original")));
    }

    @Test void incompleteAppearanceOrMovementRecoveryPreventsNextStage() {
        assertThrows(IllegalStateException.class, () -> StageTransition.restoreBeforeTransition(() -> "original", () -> {}, () -> false));
        assertThrows(IllegalStateException.class, () -> StageTransition.restoreBeforeTransition(() -> "original",
                () -> { throw new IllegalStateException("Restore failed"); }, () -> true));
    }
}
