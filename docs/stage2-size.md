# Stage 2 size investigation — Purpur 26.2

The target is exactly 1.5 blocks wide and 3 blocks tall, for both rendered appearance and collision/hitbox behavior.
**This build does not achieve it and does not change player size.** This limitation does not block supported Stage 2 skin/movement.

Checked the versioned Paper 26.2 sources and Purpur's `ver/26.2` API patches, rather than the moving latest Javadoc:

- [Attribute.java](https://github.com/PaperMC/Paper/blob/ver/26.2/paper-api/src/main/java/org/bukkit/attribute/Attribute.java):
  `SCALE` is relative entity scale. No independent width/height attributes. `STEP_HEIGHT` controls stepping, not body height.
- [Entity.java](https://github.com/PaperMC/Paper/blob/ver/26.2/paper-api/src/main/java/org/bukkit/entity/Entity.java):
  public `getWidth`, `getHeight`, `getBoundingBox` report dimensions; there are no corresponding independent body size setters.
  `wouldCollideUsing` is a collision query, not a dimension mutation.
- [Purpur Entity patch](https://github.com/PurpurMC/Purpur/blob/ver/26.2/purpur-api/paper-patches/files/src/main/java/org/bukkit/entity/Entity.java.patch)
  and [Player patch](https://github.com/PurpurMC/Purpur/blob/ver/26.2/purpur-api/paper-patches/files/src/main/java/org/bukkit/entity/Player.java.patch)
  do not add independent width/height setters.

For a nominal standing player (0.6 wide × 1.8 tall):

| Uniform scale | Width | Height |
| --- | --- | --- |
| 1.0 | 0.6 | 1.8 |
| 1.5 | 0.9 | 2.7 |
| 5/3 | 1.0 | 3.0 |
| 2.5 | 1.5 | 4.5 |

The requested width needs 2.5× while height needs 5/3×; one scalar cannot satisfy both. Pose, crouching, swimming
and foreign scale modifiers further change the actual box. `/infect status` therefore prints the server's current
bounding box and pose and explicitly marks exact Stage 2 size UNIMPLEMENTED. Other plugins' scale changes remain intact.

Mutating a returned bounding-box object does not safely resize both a networked vanilla player and its collision model.
NMS dimension overrides would be version-specific and risk desynchronizing movement, reach, eye height, poses and observers.
Visual-only fake entities would not prove collision equivalence. Neither approach is included. A future supported solution
must verify both rendered dimensions and collisions/hits from real Java and Bedrock clients; models alone would not prove that.
