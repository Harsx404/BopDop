# BopDop

A native Android music player built on the DopDop music engine — Liquid Glass UI: clear, refractive glass panels with bright specular rims floating over a living aurora backdrop that takes its colours from whatever is playing.

## Features

- **Two catalogues, one library** — YouTube Music (search, home feed, moods & genres, albums, artists, radio) merged with JioSaavn (320 kbps AAC, Indian charts and stations).
- **Hi-Res match** — YouTube Music tracks play from JioSaavn's 320 kbps copy when the same recording exists there.
- **Resilient streaming** — URLs resolve lazily at play time, are probed mid-file before playback, and fall back across InnerTube clients → NewPipeExtractor with a WebView BotGuard PoToken provider. Expired URLs re-resolve and resume in place.
- **Liquid Glass** — real backdrop blur with vibrancy (the colour behind glows through), a refractive light band inside every edge and a corner-lit specular rim; Clear / Regular / Frosted levels in Settings; system-blue active states.
- **Now Playing** — full-bleed artwork melting into its own colour wash, glass pill sliders (progress + system volume), fused liquid-glass transport, double-tap to like, swipe to skip.
- **Lyrics** — time-synced (LRCLIB) with word-by-word fill, ribbon-style spring scrolling and depth blur; YouTube Music / JioSaavn text fallback for Bollywood and regional songs.
- **Library** — liked songs, playlists, saved albums, followed artists, history, offline audio cache.
- Autoplay radio, queue with drag-to-reorder, sleep timer, media notification & lock-screen controls.

## Stack

Kotlin · Jetpack Compose · Media3 (ExoPlayer + MediaSession) · OkHttp · Coil 3 · Haze (backdrop blur) · AGSL shaders (aurora backdrop) · NewPipeExtractor

## Build

Requires JDK 17+ and Android SDK platform 37.

```bash
./gradlew :app:assembleRelease
```

The release APK (signed with the debug key for sideloading) lands in `app/build/outputs/apk/release/`.

## Roadmap

- Listen Together
- Offline downloads
- YouTube Music account sign-in
