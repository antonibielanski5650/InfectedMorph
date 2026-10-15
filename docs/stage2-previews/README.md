# Stage 2 visual samples

Synthetic fixtures only; no real client rendering claim. The approved Stage 1 samples in `../previews/` are unchanged.
Each classic/slim and low/medium/high combination includes the original, freshly generated Stage 1 and separate Stage 2 PNG,
plus a nearest-neighbor comparison with front/back views and UV maps. Raw textures are 64×64 and retain alpha.

| Model / intensity | Comparison | Stage 2 texture | Stage 1 / Stage 2 scratch pixels |
| --- | --- | --- | --- |
| Classic low | [Sheet](classic-low-comparison.png) | [PNG](classic-low-stage2.png) | 34 / 196 |
| Classic medium | [Sheet](classic-medium-comparison.png) | [PNG](classic-medium-stage2.png) | 80 / 297 |
| Classic high | [Sheet](classic-high-comparison.png) | [PNG](classic-high-stage2.png) | 165 / 379 |
| Slim low | [Sheet](slim-low-comparison.png) | [PNG](slim-low-stage2.png) | 28 / 196 |
| Slim medium | [Sheet](slim-medium-comparison.png) | [PNG](slim-medium-stage2.png) | 82 / 297 |
| Slim high | [Sheet](slim-high-comparison.png) | [PNG](slim-high-stage2.png) | 177 / 371 |

Scratch counts use a fixed red-dominance threshold on these neutral-color fixtures. They measure visible cut coverage,
not simply any pixel change or total bruising. Arbitrary original red clothing cannot be semantically classified by that metric.
Automated comparison repeats across 24 UUIDs, both models and all three intensities (144 comparisons).

Reproduce after `mvn package` (Linux/macOS):

```sh
java -Djava.awt.headless=true -cp target/test-classes:target/classes pl.infectedmorph.skin.Stage2PreviewSamples docs/stage2-previews
```

On Windows CMD, use `target\test-classes;target\classes` as the classpath. GitHub Actions also publishes these images
as [InfectedMorph-stage2-previews ZIP](https://github.com/antonibielanski5650/InfectedMorph/actions/runs/38751357909/artifacts/11981276966). Real skins, armor, lighting, actual F5/observer visibility and client UV rendering need local testing.
