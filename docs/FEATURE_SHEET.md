# BopDop feature sheet

What BopDop is adding, mostly from studying BitChord (BC) and LastWave (LW). Built five at a time,
in batch order. Update the **Status** column as items land.

- **Priority:** P1 do soon · P2 worth it · P3 nice to have
- **Size:** S hours · M a day or two · L several days · XL needs new infrastructure
- **Status:** `todo` · `doing` · `done` · `blocked` (with the reason in Notes)

## Batch plan

| Batch | Items |
|---|---|
| 1 | F01 Offline downloads · F02 Word-by-word lyrics · F03 Playback speed · F04 Lyrics offset · F05 Wi-Fi vs mobile audio quality |
| 2 | F06 ListenBrainz scrobbling · F08 Remember shuffle & repeat · F09 Settings search · F10 Swipe to queue · F11 Playlist tools |
| 3 | F12 Smart playlists · F13 Stats for nerds · F14 Live search results · F15 Queue history · F16 Dislike |
| 4 | F17 Song info panel · F18 Output device pill · F19 Lyrics translation under lines · F20 Backup & restore · F21 Grid / list Library |
| 5 | F22 Cache controls · F23 Lyrics motion background · F24 Selectable app icon · F25 One-tap update install · F26 Per-ABI APKs |
| later | F07 Last.fm · F27–F40 (bigger or need outside services) |

## Features

| ID | Area | Feature | From | Pri | Size | Batch | Status | Notes |
|---|---|---|---|---|---|---|---|---|
| F01 | Features | Offline downloads: songs, albums and playlists saved with tags, cover art and `.lrc`; plays with no network; Downloads page | BC, LW | P1 | L | 1 | done | Media3 downloads into a never-trimmed cache; plays in airplane mode; lyrics + art saved; Download / Download all / Downloads page. Export to Music/ folder not done |
| F02 | Player | Word-by-word lyrics highlighting (karaoke sweep) from word-timed LRC | BC, LW | P1 | M | 1 | done | lrc.red word times drive the sweep; other sources fall back to the estimated sweep |
| F03 | Player | Playback speed 0.5×–2× (pitch kept) | BC | P1 | S | 1 | done | Player ⋯ menu and Settings; shown under the seek bar when not 1× |
| F04 | Player | Lyrics timing offset, per song | BC | P1 | S | 1 | done | Timer button in the lyrics toolbar; also moves the live line and lock-screen lyrics |
| F05 | QoL | Separate audio quality for Wi-Fi and mobile data | BC | P1 | M | 1 | done | Settings › Sound; High keeps the JioSaavn 320 kbps match; downloads always High |
| F06 | Features | Scrobbling to ListenBrainz (user token) | BC, LW | P1 | M | 2 | todo | |
| F07 | Features | Scrobbling to Last.fm | BC, LW | P2 | M | later | blocked | needs a Last.fm API key + secret registered for BopDop |
| F08 | QoL | Remember shuffle and repeat across restarts | BC | P2 | S | 2 | todo | |
| F09 | QoL | Search inside Settings | BC, LW | P2 | M | 2 | todo | |
| F10 | QoL | Swipe a song to add it to the queue (search, recents, lists) | BC | P2 | M | 2 | todo | |
| F11 | QoL | Playlist tools: reorder, add to several playlists, total length, sort, pin | BC | P2 | M | 2 | todo | |
| F12 | Features | Smart playlists from history, likes, seed artists and genres; weekly recap | LW | P1 | L | 3 | todo | |
| F13 | Player | Stats for nerds: codec, bitrate, sample rate, bit depth, channels, source | BC, LW | P2 | S | 3 | todo | |
| F14 | QoL | Live search results while typing | BC | P2 | M | 3 | todo | today only word suggestions |
| F15 | QoL | Queue history (what played before) | BC | P3 | S | 3 | todo | |
| F16 | Features | Dislike: skips the song and keeps it out of radio | BC | P2 | S | 3 | todo | |
| F17 | Player | Song info panel: release date, album, credits, your plays | BC | P2 | M | 4 | todo | |
| F18 | Player | Output device pill: phone speaker, Bluetooth name, USB | BC | P3 | S | 4 | todo | |
| F19 | Player | Translation shown under each lyric line; right-to-left scripts | BC, LW | P2 | M | 4 | todo | |
| F20 | QoL | Backup and restore library and settings to a file | BC | P1 | M | 4 | todo | |
| F21 | UI | Grid or list toggle in Library | BC | P3 | S | 4 | todo | |
| F22 | QoL | Cache controls: unlimited option, keep cached songs across updates, remove one | BC | P3 | S | 5 | todo | |
| F23 | UI | Motion background behind lyrics from the cover | LW | P3 | M | 5 | todo | |
| F24 | UI | Selectable app icon (light / dark glass) | BC | P3 | S | 5 | todo | |
| F25 | QoL | One-tap update: download and install in the app | BC | P2 | M | 5 | todo | needs install-packages permission |
| F26 | QoL | Per-ABI APKs alongside the universal one | BC, LW | P3 | S | 5 | todo | |
| F27 | Features | Spotify account link: your playlists and Liked Songs | BC | P2 | L | later | todo | needs Spotify login |
| F28 | UI | Animated cover art (Spotify Canvas, Apple motion art) | BC, LW | P2 | L | later | todo | |
| F29 | Features | Lossless sources (FLAC/ALAC add-ons) | BC, LW | P3 | XL | later | todo | |
| F30 | Features | Dolby Atmos when a surround version exists | BC, LW | P3 | L | later | todo | |
| F31 | Features | Bit-perfect USB DAC output | BC, LW | P3 | XL | later | todo | |
| F32 | Features | Beat-matched Automix (tempo, beat grid, phrase align) | BC | P3 | XL | later | todo | |
| F33 | Features | Discord Rich Presence | BC, LW | P3 | M | later | todo | |
| F34 | Features | Multiple accounts / profiles with PIN; guest mode | BC, LW | P3 | L | later | todo | |
| F35 | Features | Friends feed (recent plays) | LW | P3 | L | later | todo | needs scrobbling first |
| F36 | Features | SMB / WebDAV network shares as a library | BC | P3 | L | later | todo | |
| F37 | UI | Apple-style hold menu with frosted popup | BC | P3 | M | later | todo | |
| F38 | UI | Tablet and landscape player layouts | BC | P3 | M | later | todo | |
| F39 | UI | Bounce overscroll throughout | BC | P3 | S | later | todo | |
| F40 | QoL | In-app language picker and translations | BC | P2 | L | later | todo | Hindi first |
| F41 | Features | Desktop and TV apps | BC | P3 | XL | later | todo | |

Sources: [BitChord](https://github.com/kushagrasinghx/BitChord) ([releases](https://github.com/kushagrasinghx/BitChord/releases)),
[LastWave](https://github.com/Clash-Projects/LastWave-Native) ([releases](https://github.com/Clash-Projects/LastWave-Native/releases)).
