# Local Stage 1 testing - Java 25 / Purpur 26.2

Use a separate local server and test accounts. This plugin has not been tested with a real Minecraft client in this workspace.
Do not replace your production plugin or world while evaluating it. The approved skin generator is unchanged in 0.3.0.

## 1. Install and configure on Windows CMD

1. Download the **new 0.3.0-SNAPSHOT artifact** from draft PR #1 / its successful Actions run. Extract the ZIP.
   Remove the older InfectedMorph JAR from the local server's `plugins` folder and put the new JAR there.
2. Start the local Purpur 26.2 server once to create the config, then stop it. Accept the Minecraft EULA yourself.
   Make sure `java -version` reports Java 25. No client mod or resource pack is needed for Stage 1.
3. Edit `plugins\InfectedMorph\config.yml`:

   ```yaml
   java-display:
     enabled: true
     mineskin-api-key-env: INFECTEDMORPH_MINESKIN_API_KEY
   ```

   Keep the rest of the generated configuration. Older configs retain `enabled: false`; change it explicitly.
   `stage1.infection-intensity` and eye settings keep the already approved appearance design.
4. Obtain your own MineSkin V2 API key from the MineSkin account manager. Do not send it in chat or commit it.
   Open **Windows Command Prompt (CMD)** and enter:

   ```bat
   cd /d C:\Minecraft\InfectedMorph-test
   java -version
   set "INFECTEDMORPH_MINESKIN_API_KEY=PASTE_YOUR_KEY_HERE"
   java -Xms1G -Xmx2G -jar purpur.jar nogui
   ```

   Replace only the placeholder with your actual key. Use the same CMD window for `set` and starting Java.
   This is process-local configuration: it is inherited by this server process, not committed to config or Git.
   The plugin never logs the value; do not share CMD history/screenshots containing the command you typed.
   After stopping the server, clear it with:

   ```bat
   set "INFECTEDMORPH_MINESKIN_API_KEY="
   ```

   Do not use `setx` unless you deliberately want persistent environment configuration. PowerShell has different syntax.
   GUI launchers, services and panels do not inherit variables from an unrelated CMD window.
5. Ensure outbound HTTPS to `api.mineskin.org` and `textures.minecraft.net`. Do not disable TLS validation.
   MineSkin uploads the generated texture as `unlisted`, which is not private. No key is shipped with the JAR.
6. In the server console, grant the Java test account permission:

   ```text
   lp user <name> permission set infectedmorph.stage1 true
   ```

   Alternatively use a local operator test account. `normal` and `status` are allowed by default.

## 2. Verify actual Java appearance

1. Join with a standard 64x64 classic or slim skin. Leave sprinting and any special movement modes before testing.
2. Run `/infect status`. It must show `JAVA_PROFILE` when enabled and the key is configured.
   `PREVIEW_ONLY` means no transformation or movement restriction will activate; read its reason first.
3. Run `/infect stage1`. During generation/publication, the stage stays `NORMAL` and movement is unrestricted.
4. After publication succeeds, expect `JAVA_PROFILE_APPLIED`, Stage `STAGE1` and active movement restrictions.
   The server has verified the signed profile property and called Paper's native self/observer refresh.
   Status deliberately says rendering is `UNVERIFIED`: a server API cannot acknowledge a rendered client texture.
5. Press F5 and inspect front/back/sides; ask a second Java client to inspect the same player and tab list.
   Repeat with slim and classic skins and outer layers enabled/disabled. Check that original clothes, hair and the
   approved infection marks are retained. No reconnect should be required by the implementation.
6. Run `/infect normal`: original skin/model/cape should return for both clients and movement should be normal.
   If profile API recovery fails, status must say `RECOVERY_REQUIRED`, Stage `NORMAL`, and explain cleanup.
   Reconnect is a recovery option, not the routine refresh mechanism.

## 3. Measure walking and reject sprint attempts

1. Build a straight, flat, unobstructed stone lane with 10 blocks of run-in followed by a measured 40-block segment.
   Use stable 20 TPS; avoid ice, water, soul sand, stairs, jumping, sneaking, collisions, armor speed bonuses and analog input.
2. First record a normal baseline. Activate Stage 1 successfully, then hold straight forward through the run-in.
   Time crossing the start and finish of the 40-block segment from the same player position reference.
   Nominal target: approximately **10 seconds for 40 blocks**, with roughly +/-0.15 blocks/s tolerance over repeated trials.
   The tolerance is a practical test threshold, not an in-game measurement already performed by the developer.
3. While active, try the sprint key, double-tap forward, hold sprint during activation and repeatedly toggle sprint.
   The server must cancel sprint-start events and clear an already-set sprint flag every tick.
   Neither method should produce sustained sprint speed. Check from a second observer as well as FOV.
4. Stop Stage 1. Confirm sprinting works again and your original base speed and other plugins' modifiers remain present.
   The plugin does not automatically resume an old sprint; it releases the restriction so you can start sprinting normally.
5. With a separate test speed modifier/effect, inspect attributes before, during and after. Its entry must remain unchanged.
   During ground walking the Stage 1 multiplier compensates the effective speed toward the fixed target; on exit that
   multiplier alone disappears. Changes made by the other plugin during infection must survive restoration.
6. Inspect swimming, flying, gliding, riding, knockback and jumping. The ground-speed modifier must be absent outside
   normal ground walking. The plugin never writes velocity, fly speed, swim-speed attributes or other modifiers.
   Sprint-based swimming/flight boosts are unavailable because the same sprint flag is prohibited during active Stage 1.

## 4. Lifecycle and failure checklist

| Scenario | Expected result |
| --- | --- |
| Display disabled or key absent | PNG preview only; Stage `NORMAL`; no movement restriction. |
| Invalid key / HTTP error / timeout / 429 | Stage `NORMAL`; no movement restriction; English error/cooldown message. |
| `/infect normal` during generation | Callback invalidated; later completion cannot apply a skin or movement. |
| Death during generation | Job cancelled; no delayed activation after respawn. |
| Death while active | Movement removed immediately; Stage `NORMAL`; original skin restored one server task after respawn. |
| Quit while active or generating | Work cancelled; restoration attempted; reconnect starts `NORMAL` with no owned speed modifier. |
| Plugin disable | All owned movement modifiers removed independently of appearance restoration; no automatic reactivation. |
| Appearance API succeeds but movement fails | Appearance rollback attempted; no active stage; sprint policy released. |
| Appearance rollback fails | Stage `NORMAL`, `RECOVERY_REQUIRED`; cleanup retries and manual recovery instructions. |
| Another plugin replaces the skin | Stage ends, movement released; the external skin is not overwritten. |
| Another plugin modifies movement attributes | Its modifiers/base remain; Stage 1 recalibrates its own ground multiplier only. |
| Respawn / join with a stale owned modifier | Owned modifier removed; no restriction without a new successful activation. |

A crashed server cannot run disable handlers; transient modifiers are not saved in player NBT and are also cleaned on join.
If an attribute API refuses removal, status reports pending cleanup and the tick loop retries while the plugin is enabled.

## 5. Bedrock tests and current limitation

Bedrock transformations remain blocked. With local Geyser-Spigot/floodgate, status should report `BEDROCK` and Stage 1
must state the limitation without changing movement. Availability of `sendSkin` is diagnosed separately; it is not an adapter.
See [the API investigation](bedrock-investigation.md). Observe a changed Java player's skin from Bedrock as a separate,
unverified experiment. Record exact Geyser/floodgate/client/platform versions and results; do not assume Java refresh works there.
