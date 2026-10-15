# Verification - October 15, 2026 - 0.3.0-SNAPSHOT

## Implemented scope

Existing draft PR #1 / `codex/stage1-foundation` now includes transactional appearance/movement activation,
4 blocks/s nominal ground walking, sprint prevention, lifecycle cleanup and verified signed-profile readback.
Java display is key-gated and enabled in new configs; missing keys or explicit disable still produce preview-only.
The approved skin generator and committed appearance samples are unchanged from 0.2.0.

## Automated checks

**Full build: BUILD SUCCESS** on Temurin Java 25.0.4+1, [Actions run 38742938086](https://github.com/antonibielanski5650/InfectedMorph/actions/runs/38742938086).
Source commit: `bbb3c81f9857a7f386f2a1df6a4cc99aaef201d0`. All 21 main source files compiled against the real Purpur API.
**51 tests passed, 0 failures, 0 errors, 0 skipped**, including Paper/Mockito adapter tests.

[Download 0.3.0-SNAPSHOT JAR (ZIP)](https://github.com/antonibielanski5650/InfectedMorph/actions/runs/38742938086/artifacts/11977610061).
Artifact ZIP SHA-256: `f3315a201b96f6396b9b3ef411cb087aade3bc56ab488162b9bea98f33907104`.
The initial remote build found a stale test method reference after renaming the calibration method; it was corrected
and the full build/test/package workflow passed. No tests were removed or skipped to obtain the passing result.

The 27 previous skin/HTTP cases remain. New tests exercise:

- Calibration at different unrestricted speeds with foreign speed modifiers retained unchanged.
- Repeated sprint starts and tick correction; rejected ground starts trigger an attribute resync.
- Ground/non-ground transitions and recalibration after external speed changes.
- Death, logout, disable, inactive/preview state, stale-key removal and fresh respawn/rejoin controller state.
- Partial modifier application, removal failures, released sprint policy and retained cleanup retries.
- Actual Bukkit sprint events (mocked player), including permission to sprint again after normal and preserving another plugin's cancellation.
- The actual Bukkit attribute adapter: transient owned modifier, idempotence, preserving foreign modifiers,
  never changing base/walk speed/fly speed/velocity, and ignoring special movement contexts.
- Activation ordering and rollback after apply, verify and movement failures, including failed rollback paths.
- Java profile application on a detached copy, signed readback, original property restoration and retention of unrelated properties.

The local Java 25 core check passes 43 cases, excluding the Paper/Mockito adapter tests that need the actual API dependency.
A full local `mvn package` was attempted with Maven 3.9.11 / Java 25.0.4.1 and trusted proxy CA; the cloud proxy still blocks
`repo.purpurmc.org` with HTTP 403. GitHub Actions runs the complete Java 25 build against the real pinned Purpur API,
including the Bukkit/Mockito cases. See the current PR checks and artifact links for the final complete result.
The successful remote build resolves the local compilation limitation. No invented/stub server API is used to claim compatibility.

## API/source verification

- Paper 26.2 `AttributeInstance` exposes transient modifiers and keyed removal; the plugin owns only
  `infectedmorph:stage1_walking` and never resets the attribute's base or foreign modifier collection.
- Paper 26.2 `PlayerToggleSprintEvent` is cancellable. The plugin cancels starts at HIGHEST and clears sprint state on ticks.
- Paper 26.2 `CraftPlayer.setPlayerProfile` unregisters/re-registers visible observers and invokes its native self-refresh.
  Detached-copy application/readback avoids changing the live profile before invoking that API.
- `setWalkSpeed` would also change the movement attribute base; it is intentionally not used.
- Geyser exposes `SessionSkinApplyEvent` and public `sendSkin` since 2.8.3; the latter can silently ignore unknown targets.
  Full Bedrock transformation remains blocked rather than treating an API call as proof of a rendered transformation.

Links and scope are in [local testing](local-testing.md), [Bedrock investigation](bedrock-investigation.md), and README.
The ground-speed formula is a nominal vanilla mechanics calculation, not an actual in-game benchmark.

## Not runtime-verified

- Purpur plugin startup and actual server event ordering under death/respawn/disconnect and other plugins.
- Measured 4 blocks/s on a real client; double-tap/sprint-key prediction and observer agreement.
- MineSkin publishing with the user's key, F5 skin rendering, observer visibility, tab list, capes and restoration.
- Actual Java/Bedrock client controls and Geyser/floodgate visibility, refresh ordering or persona geometry support.

The complete Windows CMD/local-server checklist documents how to verify these. No production deployment, ChunkVote
changes, merge into main or Stages 2-5 work has been performed.
