# Verification — October 15, 2026 — 0.4.0-SNAPSHOT

Stage 2 adds stronger deterministic original-skin generation, 6 blocks/s walking, 9 blocks/s sprinting, permitted sprint
inputs, and safe Stage 1 ↔ Stage 2 transitions via NORMAL. Stage 1's approved generator/UV/eye/intensity implementation
and committed Stage 1 preview images are unchanged. Exact 1.5 × 3 dimensions are unsupported; size is unchanged.
See [size investigation](stage2-size.md) for versioned official sources and [local testing](local-testing.md) for runtime checks.

## Build and automated checks

Local Java 25.0.4.1 core compilation/tests: **59 passed**, 0 failures/skips (before Paper/Mockito adapter checks).
The local full `mvn package` was attempted with Java 25 / Maven 3.9.11 and trusted proxy CA, but the managed network
returns HTTP 403 for the official Purpur Maven repository. No TLS validation was disabled and no substitute API stubs were used.
The full Java 25 build against pinned real `purpur-api:26.2.build.2621-stable` is run by GitHub Actions; final evidence
and downloadable artifacts will be linked here after the run completes.

Tests cover the previous Stage 1 behavior plus:

- Stage 2 nominal 6 walking / 9 sprinting calibration, including vanilla's 1.3 sprint attribute modifier.
- Actual Bukkit sprint events allowed in Stage 2, blocked in Stage 1, foreign cancellations retained.
- Shared transient attribute adapter target readback, preserving foreign/sprint modifiers and never writing base/walk/fly speed or velocity.
- Stage 1 → Stage 2 → Stage 1 → NORMAL sprint/speed restoration, ground/special-movement transitions, death/logout and disable.
- Partial Stage 2 activation failure rolls back appearance and owned movement; inactive/preview cannot acquire sprint restrictions.
- Snapshot retained before restoration; incomplete recovery blocks the next stage; repeated transitions retain the original source.
- Stage 2 determinism, UUID variation, original and Stage 1 data immutability, preserved alpha/hidden RGB, exact classic/slim UV bounds.
- More than 10% greater red-cut coverage than Stage 1 at every intensity across 24 UUIDs and both models (144 fixture comparisons).
- Added marks on every base body side, protected custom eyes/central features/hairline, capped added coverage, and most opaque pixels unchanged.

Generated synthetic samples include all low/medium/high classic/slim Stage 2 textures, fresh Stage 1 references and original textures.
The classic medium comparison was visually inspected: longer visible cuts on front/back, with recognizable colors/features retained.
Samples are not real client screenshots. Counts and reproduction are in [sample notes](stage2-previews/README.md).

## Still requires real server/client testing

- Plugin startup/event ordering on Purpur 26.2, measured 4/6/9 blocks/s at stable 20 TPS, sprint-key/double-tap prediction and hunger rules.
- MineSkin with a real user's environment key, self F5 and second Java observer refresh, classic/slim rendering, restoration and capes.
- Stage transitions, cancellation/death/respawn/logout/disable and failure recovery while other skin/movement plugins are installed.
- Actual current bounding boxes/poses and confirmation that Stage 2 leaves size unchanged; no exact size achievement is claimed.
- Geyser/floodgate/Bedrock observer behavior. Bedrock player transformations remain safely blocked, not implemented or runtime-verified.

No real Minecraft client was available in this workspace. Automated profile readback is not rendering confirmation.
No production deployment, ChunkVote changes, custom models, Stages 3–5 implementation or merge into main is performed.
