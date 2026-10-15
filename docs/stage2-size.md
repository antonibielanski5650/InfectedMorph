# Stage 2 native scaling — Purpur 26.2 — 0.4.1

The approved workaround uses uniform **5/3 (approximately 1.666667)** entity scale. A standard standing player
at baseline scale 1 is expected to be approximately **1.0 block wide and 3.0 blocks tall**. Minecraft's native SCALE
attribute handles model and collision dimensions together. The plugin never edits a returned bounding box, creates
fake entities, sends custom resizing packets or uses NMS, Blockbench or custom models. Rendering/collisions still require
real Minecraft client testing; automated attribute readback does not prove a rendered model.

## API and ownership

- [Paper 26.2 Attribute.SCALE](https://github.com/PaperMC/Paper/blob/ver/26.2/paper-api/src/main/java/org/bukkit/attribute/Attribute.java)
  exposes relative entity scale. [AttributeInstance](https://jd.papermc.io/paper/26.2/org/bukkit/attribute/AttributeInstance.html)
  supports transient modifiers and keyed lookup/removal.
- The plugin owns only `infectedmorph:stage2_scale`: operation `MULTIPLY_SCALAR_1`, amount `5.0 / 3.0 - 1.0`.
  Native effective scale is multiplied by 5/3. Base values, foreign additive/multiplicative modifiers and their later updates
  remain intact. Repeated enforcement is idempotent; restoring removes only this key instead of overwriting a saved base.
- Scale setup and signed texture/movement activation form one transaction. Partial failure rolls back all three.
  Stage 2 is not reported active if scale application/readback fails. Extreme initial scales that clamp the expected product
  are rejected and rolled back. Subsequent foreign changes remain subject to Minecraft's native scale limits.
- Stage 1 adds no scale effect and restores the baseline by removing the owned Stage 2 modifier. NORMAL does the same.
  Death, respawn, quit, plugin disable, join/startup stale cleanup and interrupted transformations share this lifecycle.
  Scale and speed removal are attempted independently; either failure retains an inactive entry for cleanup retries.
  Both modifiers are transient and not saved in player NBT.

## Expected dimensions and remaining width limitation

| Baseline effective scale without InfectedMorph | Stage 2 effective scale | Nominal standing width | Nominal standing height |
| --- | --- | --- | --- |
| 1.0 | 5/3 | 1.0 | 3.0 |
| 1.2 | 2.0 | 1.2 | 3.6 |

These assume a standard standing 0.6 × 1.8 player and no native scale clamping. Different poses and other plugins'
scale changes produce different dimensions. Scale remains active in air/water/flight; the ground movement modifier
is suspended there separately. No teleport or automatic clearance correction is performed when growing near obstacles.
Choose open space and test native collision, eye height, low ceilings, doorways, crouching and hit detection locally.

**Exactly 1.5-block width is still NOT implemented.** A uniform factor 5/3 produces width 1.0, whereas width 1.5 needs
factor 2.5 and would produce height 4.5. The supported API has no independent player width/height setters.
[Entity.java](https://github.com/PaperMC/Paper/blob/ver/26.2/paper-api/src/main/java/org/bukkit/entity/Entity.java),
[Purpur Entity patch](https://github.com/PurpurMC/Purpur/blob/ver/26.2/purpur-api/paper-patches/files/src/main/java/org/bukkit/entity/Entity.java.patch)
and [Player patch](https://github.com/PurpurMC/Purpur/blob/ver/26.2/purpur-api/paper-patches/files/src/main/java/org/bukkit/entity/Player.java.patch)
were checked for this boundary. No non-uniform or fragile NMS override is attempted.

`/infect status` prints the actual effective SCALE, current server bounding box and pose, plus the explicit width limitation.
A typical default player is 0.6 × 1.8 again in Stage 1/NORMAL; foreign scale settings are restored instead when present.
Bedrock transformations remain blocked and native Java-to-Bedrock size/skin visibility is unverified.
