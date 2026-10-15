# Bedrock skin API investigation - October 15, 2026

## Verified public API capabilities

The following official Geyser sources were inspected for this update:

- [Geyser skin API overview](https://geysermc.org/wiki/geyser/api/): skin event hooks are distinct from Java profiles.
- [SessionSkinApplyEvent](https://github.com/GeyserMC/Geyser/blob/master/api/src/main/java/org/geysermc/geyser/api/event/bedrock/SessionSkinApplyEvent.java):
  provides target UUID, slim/Bedrock flags and original `SkinData`; exposes skin/cape/geometry setters for skin application events.
- [GeyserConnection](https://github.com/GeyserMC/Geyser/blob/master/api/src/main/java/org/geysermc/geyser/api/connection/GeyserConnection.java):
  public `sendSkin(UUID, SkinData)`, documented since 2.8.3, updates a known player for a specific connection and can update
  that connection's own skin. It silently returns when the target is unknown. There is no rendered-appearance acknowledgement.
- [SkinData](https://github.com/GeyserMC/Geyser/blob/master/api/src/main/java/org/geysermc/geyser/api/skin/SkinData.java) and
  [Skin](https://github.com/GeyserMC/Geyser/blob/master/api/src/main/java/org/geysermc/geyser/api/skin/Skin.java): raw image data,
  cape and geometry have their own representations. A Java textures property is not a replacement for those records.
- [SkinManager](https://github.com/GeyserMC/Geyser/blob/master/core/src/main/java/org/geysermc/geyser/skin/SkinManager.java):
  current implementation distinguishes self/unlisted players from listed observers and delays some player-list updates.
  These internals are evidence for required tests, not an API dependency of InfectedMorph.

`/infect status` now checks the installed public API for `sendSkin` and the event class. Older or missing installations
produce an explicit unavailable capability message. A positive capability result still states `NOT_IMPLEMENTED`/`UNVERIFIED`.

## Decision for 0.4.0 (Stages 1 and 2)

A full Bedrock transformation adapter is **not implemented** in this release. The public APIs make a prototype possible,
but their mere presence cannot establish complete, reliable transformation/restoration for all sessions. In particular,
`sendSkin` returning normally can mean a silent no-op; it cannot be used as proof to activate Stage 1 or Stage 2 movement.
The Java profile backend is not treated as a Bedrock backend. Bedrock players remain blocked without movement changes.

A testable full adapter needs all of the following together before activation can be trusted:

1. Capture each Bedrock player's original raw skin, layout, geometry and cape from the correct own-session event.
   Validate raw format/dimensions and reject unsupported persona/marketplace/custom geometry without losing the original.
2. Generate the approved infection from that original, not from an assumed Java/Floodgate fallback skin.
3. Apply event overrides for newly observing sessions and refresh already observing connections with the public API.
   Track sessions where the target is not yet known; handle late observers, reconnects, world/visibility changes and refresh order.
4. Publish a matching signed Java texture for Java observers, preserve slim/classic geometry, and retain both editions'
   original appearance data for rollback. Any partial failure must avoid active movement restrictions.
5. Restore both representations on normal/death/quit/disable and remove stale event overrides.
6. Test own Bedrock view and Bedrock/Java observers on actual installed versions/platforms, including trusted-skin
   behavior, persona rejection and delayed refreshes. Neither this workspace nor the CI build has those real clients.

This is an explicit safe fallback requested for unreliable full support. It is not a claim that Geyser lacks skin APIs,
or that Java profile changes automatically synchronize Bedrock appearance. No unsupported internal methods are invoked.
