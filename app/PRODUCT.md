# BopDop Android

BopDop is an Android music app with Home, Explore and Library tabs, search, a queue and a full-screen player. The application uses Kotlin and Jetpack Compose. Existing music content, navigation, account connections and playback behavior remain the product's source of truth.

## Halloween extension

The user approved a Halloween festive world with dark plum and orange, a light parchment variant, custom icons and animated decorations. The latest explicit instruction preserves the existing animated Wisp ghost, Ember pumpkin and Echo bat exactly, while using AI-generated artwork for backdrops, icons and other decorations. The user requires crisp 16-bit pixel characters and properly divided square animation grids.

This is a seasonal extension of the existing Android app. Keep its existing typeface, native screen structure, real music content and core controls. The generated concepts in `../output/halloween-concepts/v1/` are visual references for palette and seasonal atmosphere; they do not authorize replacing product behavior or inventing content. The latest preservation instruction takes precedence over earlier character concepts.

Festive settings control automatic festival selection, intensity and an explicit Halloween preview. Off takes precedence over preview. Seasonal mascot selection follows the festival; explicitly selected characters remain selected all year. Respect Android and app reduced-motion settings and suspend decorative animation while inactive.

## Validation

The user reported a crash entering Themes/Appearance. The launcher-layer preview must support every selected resource through Compose `painterResource`. Verify this route in the emulator whenever launcher artwork changes.
