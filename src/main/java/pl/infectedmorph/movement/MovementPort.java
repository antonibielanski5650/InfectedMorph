package pl.infectedmorph.movement;

/** Adapter boundary for main-thread player operations. The controller never replaces foreign modifiers. */
public interface MovementPort {
    boolean aliveAndOnline();
    boolean ordinaryGroundWalking();
    double unrestrictedGroundSpeed();
    void stopSprinting();
    void putOwnedModifier(double amount);
    void removeOwnedModifier();
}
