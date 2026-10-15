# Verification - October 15, 2026

## Scope

Review branch: `codex/stage1-foundation`, existing draft PR #1. The update adds visible Stage 1 intensity presets,
UV-bounded marks, conservative/configurable eye treatment and English-only project text. It preserves the existing
preview-only default, disabled experimental Java display, lifecycle protections and explicit Bedrock limitation.

## Automated and visual checks

Full Java 25 build: **BUILD SUCCESS**, [Actions run 38688114241](https://github.com/antonibielanski5650/InfectedMorph/actions/runs/38688114241),
source commit `4aa6e0346a42abce84b8e43d7edd7ce28f5f58dc`, Temurin 25.0.4+1.
All 15 main source files compiled against the real Purpur 26.2 API. **27 tests passed; 0 failures, 0 errors, 0 skipped.**

- [Download the new 0.2.0-SNAPSHOT test JAR (ZIP)](https://github.com/antonibielanski5650/InfectedMorph/actions/runs/38688114241/artifacts/11950574431)
- [Download all intensity previews (ZIP)](https://github.com/antonibielanski5650/InfectedMorph/actions/runs/38688114241/artifacts/11950374762)

The local `mvn package` attempt hit the cloud proxy's HTTP 403 for the official Purpur dependency repository.
The successful complete GitHub build resolves the compilation check; it is not a Minecraft runtime test.

The expanded tests cover:

- Every intensity on classic and slim: original ARGB data, alpha and fully transparent hidden RGB remain intact.
- Independently specified Minecraft UV strips protect caps, unused slim columns and unrelated texture regions.
- Upper head/hairline, central nose/mouth features and most source pixels stay unchanged.
- Coverage and total color change increase from low to medium to high across both models and multiple UUIDs;
  existing marks persist, while at least 70% of all original pixels remain unchanged even on an entirely opaque texture.
- Medium produces visible marks on front face, torso, both arms and both legs.
- Real UV sample fixtures retain more than 55% of visible source pixels even at high intensity;
  unrelated opaque face overlay/hair pixels are protected.
- Results are deterministic and respond to both UUID and original skin data.
- Eye recognition handles different rows and eyes close to the hairline, skips ambiguous/uniform faces,
  accepts custom/asymmetric coordinates and rejects invalid ones. Custom eye brightness decreases with intensity.
- Existing skin URL restrictions, HTTPS upgrade, HTTP response size limits and disabled redirects remain tested.

Samples are generated from synthetic original skins for both models and all three intensities.
The comparison sheets are inspected as images, not described as Minecraft screenshots.

| Synthetic sample | Low changed pixels | Medium changed pixels | High changed pixels |
| --- | --- | --- | --- |
| Classic | 174 | 290 | 566 |
| Slim | 170 | 305 | 549 |

Both samples detect four eye pixels on different rows; those eye pixels darken with intensity.
Counts describe only these fixtures, not a promised percentage for every player skin.
See [sample files](previews/README.md) and the [Actions workflow](https://github.com/antonibielanski5650/InfectedMorph/actions)
for build output and downloadable JAR/preview artifacts.

The local environment provides Java 25.0.4.1 and Maven 3.9.11. The local Maven attempt used proxy/CA configuration;
GitHub Actions performed the complete Java 25 build because the official Purpur repository is blocked by the cloud proxy.
The workflow compiles against the real pinned Purpur API, runs JUnit and packages the relocated Gson dependency.
Historical baseline builds on October 14 passed eight tests; those old artifacts are not the updated release.

## Still requires actual Minecraft testing

- Plugin startup and behavior on a local Purpur 26.2 server with the installed Geyser-Spigot/floodgate versions.
- Visual quality on real player skins, outer layers, custom faces and classic/slim models at each intensity.
- MineSkin publishing, the client's acceptance of the signed profile, F5/observer visibility, tab list, capes,
  death/respawn, dimension changes, restart and restoration after interrupted work.
- Permissions and cancellation races against real server events, plus other skin/nickname plugin interactions.
- Actual Bedrock session detection and observation of changed Java skins. Bedrock transformations remain blocked.

No Stages 2-5, animated models, movement/attribute changes, production deployment or ChunkVote work is included.
