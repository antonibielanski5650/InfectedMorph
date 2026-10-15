package pl.infectedmorph.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ActivationTransactionTest {
    @Test void activatesMovementOnlyAfterAppearanceVerification() {
        List<String> order = new ArrayList<>();
        var result = ActivationTransaction.activate(()->order.add("apply"),()->order.add("verify"),
                ()->order.add("movement"),()->order.add("cleanup"),()->order.add("restore"));
        assertEquals(ActivationTransaction.Outcome.ACTIVE,result.outcome());
        assertEquals(List.of("apply","verify","movement"),order);
    }
    @ParameterizedTest @ValueSource(ints = {0,1,2})
    void everyPartialFailureRestoresAppearanceAndReleasesMovement(int failAt) {
        boolean[] state = {false,false};
        var result = ActivationTransaction.activate(()->{
            state[0] = true;
            if(failAt==0)throw new IllegalStateException("Apply failed");
        },()->{
            if(failAt==1)throw new IllegalStateException("Verification failed");
        },()->{
            state[1] = true;
            if(failAt==2)throw new IllegalStateException("Movement failed after a partial change");
        },()->state[1]=false,()->state[0]=false);
        assertEquals(ActivationTransaction.Outcome.ROLLED_BACK,result.outcome());
        assertFalse(state[0]);assertFalse(state[1]);
    }
    @Test void failedAppearanceRollbackNeverReportsActiveOrKeepsMovement() {
        boolean[] restricted = {false};
        var result = ActivationTransaction.activate(()->{},()->{},()->{
            restricted[0]=true;throw new IllegalStateException("Movement failed");
        },()->restricted[0]=false,()->{throw new IllegalStateException("Restore failed");});
        assertEquals(ActivationTransaction.Outcome.RECOVERY_REQUIRED,result.outcome());
        assertFalse(restricted[0]);
        assertEquals(1,result.failure().getSuppressed().length);
    }
    @Test void failedMovementCleanupStillAttemptsAppearanceRecovery() {
        boolean[] restored = {false};
        var result = ActivationTransaction.activate(()->{throw new IllegalStateException("Apply failed");},()->{},()->{},
                ()->{throw new IllegalStateException("Cleanup failed");},()->restored[0]=true);
        assertEquals(ActivationTransaction.Outcome.RECOVERY_REQUIRED,result.outcome());
        assertTrue(restored[0]);
    }
}
