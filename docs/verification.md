# Verification — October 15, 2026 — 0.4.1-SNAPSHOT

This update adds the approved native Stage 2 5/3 scale workaround. Expected standard standing size is approximately
1.0 × 3.0 blocks, not the unsupported exact 1.5 × 3.0. Stage 1 size and 4/no-sprint movement, Stage 2 6/9 movement,
MineSkin behavior and both approved skin designs remain unchanged. No skin generator, UV, eye or intensity source was changed.

## Automated checks

Local Java 25 core suite: **66 passed**, including six new scale lifecycle cases (before the real Bukkit/Mockito adapter suite).
A local full Maven build was attempted with Java 25/Maven 3.9.11. The managed proxy blocks the official Purpur repository
with HTTP 403; no TLS checks were disabled or substitute API stubs used. The full build runs in GitHub Actions against
real pinned `purpur-api:26.2.build.2621-stable`.

**Full Maven build: BUILD SUCCESS — 81 tests passed, 0 failures, 0 errors, 0 skipped.**
[Java 25 Actions run 38754698108](https://github.com/antonibielanski5650/InfectedMorph/actions/runs/38754698108)
compiled 24 main and 16 test source files. Code/artifact commit: `b2b3e75a19e64addbb29ba09d6e56cc087f5ab44`.
Subsequent documentation-only updates record these links without changing the packaged code.

- [Download 0.4.1-SNAPSHOT JAR ZIP](https://github.com/antonibielanski5650/InfectedMorph/actions/runs/38754698108/artifacts/11982089259)
- [Unchanged Stage 1 preview ZIP](https://github.com/antonibielanski5650/InfectedMorph/actions/runs/38754698108/artifacts/11982328925)
- [Unchanged Stage 2 preview ZIP](https://github.com/antonibielanski5650/InfectedMorph/actions/runs/38754698108/artifacts/11982039260)

Extract `InfectedMorph-0.4.1-SNAPSHOT.jar` from the JAR ZIP. GitHub sign-in/repository access may be required.
JAR artifact ZIP SHA-256: `a74263d93415f591d7111d2c2c6903d518f68e13d7fb924e83e845f85a0fc8e8`.

New tests cover:

- Native transient SCALE multiplier amount 2/3 (factor 5/3), idempotence and readback.
- Foreign additive, base-scalar and final-scalar modifiers retained; mid-infection base/foreign changes survive restoration.
- Owned stale modifier repair and reapplication after external removal.
- Missing/invalid attributes, rejected/clamped scale, failed removal and retry behavior.
- Actual Bukkit player port delegates to the scale adapter without touching movement or player abilities.
- NORMAL → Stage 1 → Stage 2 → Stage 1 → NORMAL, repeated without scale stacking.
- Size persists outside ground walking while only speed calibration is suspended.
- Death/logout, disable and respawn/join stale cleanup remove only owned scale.
- Partial scale or movement activation failure rolls back appearance, scale and speed.
- Both cleanup directions: speed failure cannot skip scale removal; scale failure cannot skip speed removal; retries remain inactive.

Existing tests still cover speed/sprint, transactions, skins, UV/alpha, HTTP limits and Java profile application/restoration.
The exact pixel regression now checks **all twelve approved Stage 1 and Stage 2 classic/slim/intensity texture previews**.
Skin preview generation and fixtures are unchanged and are republished by the build.

## Runtime boundaries

No actual Minecraft server/client was available. Native SCALE uses Minecraft's supported visual/collision behavior, but actual
F5/observer model size, standing/pose bounding boxes, collisions/hits/clearance and lifecycle event ordering need local testing.
Measured 4/6/9 speed targets, MineSkin with a real key and live texture refresh/restoration also remain runtime checks.
Bedrock transformations are blocked; cross-edition appearance/size visibility is unverified. A server attribute readback is not
a rendering acknowledgement. See [local test steps](local-testing.md) and [size implementation](stage2-size.md).

No production deployment, ChunkVote change, custom model, Stage 3–5 implementation or merge into main is performed.
