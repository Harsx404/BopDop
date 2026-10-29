# Approved Halloween extension

THESIS: Give the existing BopDop Android music app a readable Halloween seasonal world while music remains the main task.

OWN-WORLD: True native pixel artwork, smoked plum, amber lantern light and parchment. The user explicitly preserves the current Wisp ghost, Ember pumpkin and Echo bat and their animation frames. AI-generated backdrops, icons, lanterns and environment props surround these unchanged characters.

STORY: Home shows the haunted village and seasonal greeting; familiar navigation carries the generated icon family; the full player carries quiet scenery, transport accents and the current seekbar companion; Appearance exposes previews, intensity and character selection.

FIRST VIEWPORT: An animated skyline strip sits behind the existing top controls, above the existing Home greeting and real music feed. This is an extension, so the existing font, navigation topology and product content are retained.

FORM: User-pinned Halloween pixel-art direction; no random direction selection is applicable to this requested extension. Three AI mockups were shown and approved for atmosphere and palette before implementation. Current user instructions explicitly supersede their character art. The app remains a native Android Compose interface, so mobile emulator captures are the relevant viewports.

QUALITY BAR: Every packaged sprite has binary transparency, integer native dimensions and nearest-neighbor rendering. Environment and character atlases use 4 × 4 square grids. Icons preserve familiar action meanings. Decoration must not intercept touch or compete with music. Reduced motion and background suspension are mandatory.

Reference concepts: `../../output/halloween-concepts/v1/mockup-home-dark.png`, `mockup-home-light.png`, `mockup-player-dark.png`. Latest visual captures are under `../../output/halloween-verification/ai-*.png`. These concept images guide the seasonal vocabulary, not a replacement of the existing Home layout or font.

Detector: one scoped run over HalloweenScene.kt, HalloweenIcons.kt, SeekBar.kt and SettingsScreen.kt returned an empty finding list. The detector is text-oriented; native runtime captures and resource validation provide the substantive checks.
