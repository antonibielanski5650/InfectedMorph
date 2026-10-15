package pl.infectedmorph.movement;

/** Adapter boundary for main-thread player operations. The controller never replaces foreign modifiers. */
public interface MovementPort {
    boolean aliveAndOnline();
    boolean ordinaryGroundWalking();
    double unrestrictedGroundSpeed();
    default boolean isSprinting() { return false; }
    void stopSprinting();
    void putOwnedModifier(double amount);
    default void putOwnedModifier(double amount, double targetAttribute) { putOwnedModifier(amount); }
    void removeOwnedModifier();
}
