# Stage 1 visual inspection samples

These original synthetic skins exercise clothing, hair, face details and transparent outer layers.
Classic eyes are on local row 4; slim eyes are on row 3, close to the hairline.

| Model | Original | Low | Medium | High | Comparison sheet |
| --- | --- | --- | --- | --- | --- |
| Classic/wide | [PNG](classic-original.png) | [PNG](classic-low.png) | [PNG](classic-medium.png) | [PNG](classic-high.png) | [Sheet](classic-comparison.png) |
| Slim | [PNG](slim-original.png) | [PNG](slim-low.png) | [PNG](slim-medium.png) | [PNG](slim-high.png) | [Sheet](slim-comparison.png) |

Every skin PNG is exactly 64x64, preserves alpha and uses its actual arm UV width.
Comparison sheets show a flat front projection with nearest-neighbor scaling, plus the UV map.
They are not Minecraft screenshots; they cannot establish in-game rendering, geometry, lighting or Bedrock compatibility.

To reproduce after building:

```sh
mvn package
java -Djava.awt.headless=true -cp target/test-classes:target/classes pl.infectedmorph.skin.Stage1PreviewSamples docs/previews
```

On Windows use `;` instead of `:` between classpath entries. The source is `src/test/java/pl/infectedmorph/skin/Stage1PreviewSamples.java`.
The CI workflow generates the same files under `target/stage1-previews` and uploads a separate preview artifact.
No player textures or API keys are needed.
