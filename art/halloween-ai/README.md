# Halloween AI artwork

The existing Wisp ghost, Ember pumpkin and Echo bat companion sheets and their animation frames are preserved. Their SHA-256 hashes are recorded in `validation.json`.

All new decorative artwork was generated with the built-in image generation tool. `generation-prompts.json` contains the exact prompts and original generation paths; `raw/` contains project-local copies of the five original generations.

## Packaged assets

- Three 96 × 96 icon atlases: 4 × 4 square cells of 24 × 24 native pixels. The first 46 cells follow `HalloweenGlyph` enum order; the final two are a sparkle and distant bat.
- A 256 × 256 environment atlas: 4 × 4 square cells of 64 × 64 native pixels. Moon/clouds, four distant bat wing poses, four lantern flicker frames, and four autumn leaf poses.
- A 288 × 96 haunted village backdrop.
- A 108 × 108 adaptive launcher foreground derived from the generated lantern.

The production PNGs are in `app/src/main/res/drawable-nodpi/`. All use binary transparency; icons are single-ink masks tinted for the current theme, while scenery uses a fixed 15-color palette. Rendering uses nearest-neighbor sampling and integer physical-pixel positions/scales.

## Rebuild preparation

Run `python tools/halloween/prepare-ai-assets.py` with Pillow, NumPy and SciPy installed. It isolates the generated objects before re-registering the square atlas cells, removes nearly transparent generation residue, samples the native pixel grid and normalizes the palette. It never draws replacement artwork and never writes the character sheets. Larger inspection images are written to `output/halloween-verification/ai-assets/`.

Native animation moves the generated clouds, moon, bats and falling leaves and steps through the generated lantern frames. It respects both the in-app reduced-motion preference and Android's animation setting, and stops when the app or tab is inactive.

The Themes/Appearance crash was caused by a shape drawable being passed to Compose `painterResource` in the app-icon chooser. The Halloween background is now a supported vector, and the foreground is a supported PNG. `LauncherArtTest` guards against unsupported XML layer formats.
