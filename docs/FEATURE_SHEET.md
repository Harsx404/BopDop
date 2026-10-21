# BopDop feature sheet

What BopDop is adding, mostly from studying BitChord (BC) and LastWave (LW). Built five at a time,
in batch order. Update the **Status** column as items land.

- **Priority:** P1 do soon · P2 worth it · P3 nice to have
- **Size:** S hours · M a day or two · L several days · XL needs new infrastructure
- **Status:** `todo` · `doing` · `done` · `blocked` / `skipped` (with the reason in Notes)

## Batch plan

| Batch | Items |
|---|---|
| 1 | F01 Offline downloads · F02 Word-by-word lyrics · F03 Playback speed · F04 Lyrics offset · F05 Wi-Fi vs mobile audio quality |
| 2 | F06 ListenBrainz scrobbling · F08 Remember shuffle & repeat · F09 Settings search · F10 Swipe to queue · F11 Playlist tools |
| 3 | F12 Smart playlists · F13 Stats for nerds · F14 Live search results · F15 Queue history · F16 Dislike |
| 4 | F17 Song info panel · F18 Output device pill · F19 Lyrics translation under lines · F20 Backup & restore · F21 Grid / list Library |
| 5 | F22 Cache controls · F23 Lyrics motion background · F24 Selectable app icon · F25 One-tap update install · F26 Per-ABI APKs |
| 6 | F42 Resume exactly · F43 Notification buttons · F44 Lyrics search · F45 Save queue · F46 Release signing |
| 7 | F47 Smart downloads · F48 Private session · F49 Launcher shortcuts · F50 Radio from playlist · F51 Bluetooth resume |
| 8 | F52 Video PiP · F53 Battery saver · F54 Accessibility · F55 Continue listening · F56 Report a problem |
| later | F57–F60, F40 (languages), F41 |

## Features

| ID | Area | Feature | From | Pri | Size | Batch | Status | Notes |
|---|---|---|---|---|---|---|---|---|
| F01 | Features | Offline downloads: songs, albums and playlists saved with tags, cover art and `.lrc`; plays with no network; Downloads page | BC, LW | P1 | L | 1 | done | Media3 downloads into a never-trimmed cache; plays in airplane mode; lyrics + art saved; Download / Download all / Downloads page. Export to Music/ folder not done |
| F02 | Player | Word-by-word lyrics highlighting (karaoke sweep) from word-timed LRC | BC, LW | P1 | M | 1 | done | lrc.red word times drive the sweep; other sources fall back to the estimated sweep |
| F03 | Player | Playback speed 0.5×–2× (pitch kept) | BC | P1 | S | 1 | done | Player ⋯ menu and Settings; shown under the seek bar when not 1× |
| F04 | Player | Lyrics timing offset, per song | BC | P1 | S | 1 | done | Timer button in the lyrics toolbar; also moves the live line and lock-screen lyrics |
| F05 | QoL | Separate audio quality for Wi-Fi and mobile data | BC | P1 | M | 1 | done | Settings › Sound; High keeps the JioSaavn 320 kbps match; downloads always High |
| F06 | Features | Scrobbling to ListenBrainz (user token) | BC, LW | P1 | M | 2 | done | Settings › Scrobbling; token checked with ListenBrainz; playing-now + a listen at half the song or 4 min; offline listens wait in a file |
| F07 | Features | Scrobbling to Last.fm | BC, LW | P2 | M | later | done | Settings › Scrobbling › Last.fm: web approval (no password in the app), Libre.fm too; offline scrobbles queue. Needs an API key: local.properties lastfm.key/lastfm.secret, CI LASTFM_API_KEY/LASTFM_SECRET, or pasted in the sheet |
| F08 | QoL | Remember shuffle and repeat across restarts | BC | P2 | S | 2 | done | Saved in settings; Play keeps the mode, Shuffle buttons turn it on |
| F09 | QoL | Search inside Settings | BC, LW | P2 | M | 2 | done | Matches titles, descriptions, group names and a few extra words |
| F10 | QoL | Swipe a song to add it to the queue (search, recents, lists) | BC | P2 | M | 2 | done | Right = play next, left = add to queue, with haptic at the trigger; off in sideways shelves |
| F11 | QoL | Playlist tools: reorder, add to several playlists, total length, sort, pin | BC | P2 | M | 2 | done | Edit mode with drag handles and remove; multi-select add sheet; sort sheet; pin in ⋯. Drag does not auto-scroll long lists yet |
| F12 | Features | Smart playlists from history, likes, seed artists and genres; weekly recap | LW | P1 | L | 3 | done | Library › Made for you: Your week (minutes + top artist), On repeat, Forgotten favourites, Night owl, and Discover (related to your top songs, minus anything heard). No genres: the catalogues don't give us any |
| F13 | Player | Stats for nerds: codec, bitrate, sample rate, bit depth, channels, source | BC, LW | P2 | S | 3 | done | Player ⋯ and Settings › Sound; adds bit depth and how far ahead it's loaded to the old Audio pipeline sheet |
| F14 | QoL | Live search results while typing | BC | P2 | M | 3 | done | Top artist, four songs and two albums under the suggestions once typing pauses |
| F15 | QoL | Queue history (what played before) | BC | P3 | S | 3 | done | History chip on the queue page: earlier songs in this queue, latest first, tap to go back |
| F16 | Features | Dislike: skips the song and keeps it out of radio | BC | P2 | S | 3 | done | “Not for me” in the song menu; matches other copies of the same song; list in Settings › Sound |
| F17 | Player | Song info panel: release date, album, credits, your plays | BC | P2 | M | 4 | done | Song menu › Song info. Release date, album, label, language, credits from JioSaavn or YouTube's auto-generated description (topped up from the JioSaavn match); your plays, first and latest |
| F18 | Player | Output device pill: phone speaker, Bluetooth name, USB | BC | P3 | S | 4 | done | Fifth icon in the player's bottom row: phone / headphones / wired / USB, lit when not the speaker; live as devices come and go; tap opens the system output picker (Android 14+), Bluetooth settings before that |
| F19 | Player | Translation shown under each lyric line; right-to-left scripts | BC, LW | P2 | M | 4 | done | Translate is its own toggle in the lyrics toolbar, under Original or Romanized; Urdu/Arabic/Hebrew lines right-aligned (lyrics page, art-page line, dots). Fixed translations never showing when lyrics had blank lines |
| F20 | QoL | Backup and restore library and settings to a file | BC | P1 | M | 4 | done | Settings › Backup: library, settings, plays and lyric timings to a JSON file; restore shows what's in it and asks first. YouTube Music sign-in and ListenBrainz token stay on the phone |
| F21 | UI | Grid or list toggle in Library | BC | P3 | S | 4 | done | Grid / list icon by “Your playlists”; covers playlists and saved albums; remembered |
| F22 | QoL | Cache controls: unlimited option, keep cached songs across updates, remove one | BC | P3 | S | 5 | done | Unlimited size, “Keep cached songs” (moves the cache into app storage), “Remove from cache” in the song menu |
| F23 | UI | Motion background behind lyrics from the cover | LW | P3 | M | 5 | done | Cover copies swirl, blurred, behind the lyrics while playing; Settings › Lyrics › Moving background |
| F24 | UI | Selectable app icon (light / dark glass) | BC | P3 | S | 5 | done | Classic, Dark glass, Light glass via launcher aliases; Settings › Look & feel › App icon |
| F25 | QoL | One-tap update: download and install in the app | BC | P2 | M | 5 | done | Update downloads the APK for this phone and opens Android's installer; asks for “Install unknown apps” once |
| F26 | QoL | Per-ABI APKs alongside the universal one | BC, LW | P3 | S | 5 | done | arm64-v8a, armeabi-v7a, x86_64 + universal; release workflow uploads all; updater picks the phone's own |
| F27 | Features | Spotify account link: your playlists and Liked Songs | BC | P2 | L | later | done | Spotify's own login page in-app (sp_dc), tokens minted by the web player in a hidden WebView, Pathfinder hashes read live; playlists + Liked Songs matched to YouTube Music; Save to library |
| F28 | UI | Animated cover art (Spotify Canvas, Apple motion art) | BC, LW | P2 | L | later | done | Spotify Canvas over the cover when connected (Wi-Fi by default); Settings › Look & feel |
| F29 | Features | Lossless sources (FLAC/ALAC add-ons) | BC, LW | P3 | XL | later | done | Add-on protocol (/manifest.json, /search, /stream): user adds URLs, none bundled; same-recording match; FLAC 24/96 shown in the badge |
| F30 | Features | Dolby Atmos when a surround version exists | BC, LW | P3 | L | later | done | Asks add-ons for Atmos when the phone has an E-AC-3 JOC/AC-4 decoder; Atmos files and streams labelled “Dolby Atmos” |
| F31 | Features | Bit-perfect USB DAC output | BC, LW | P3 | XL | later | done | Android 14+ bit-perfect mixer for USB DACs at the song's rate/depth; EQ and normalization step aside; shown in Stats for nerds |
| F32 | Features | Beat-matched Automix (tempo, beat grid, phrase align) | BC | P3 | XL | later | skipped | Needs tempo and beat analysis of the audio; crossfade Automix already covers transitions |
| F33 | Features | Discord Rich Presence | BC, LW | P3 | M | later | skipped | Only possible by running the user's Discord token as a bot, which breaks Discord's terms and risks the account |
| F34 | Features | Multiple accounts / profiles with PIN; guest mode | BC, LW | P3 | L | later | skipped | A phone usually has one listener; not worth a PIN and profiles yet |
| F35 | Features | Friends feed (recent plays) | LW | P3 | L | later | skipped | Only works if friends also scrobble to ListenBrainz |
| F36 | Features | SMB / WebDAV network shares as a library | BC | P3 | L | later | skipped | Niche; on-device files and add-ons already cover home servers |
| F37 | UI | Apple-style hold menu with frosted popup | BC | P3 | M | later | done | Long-press a song, album, playlist or artist: the screen frosts, the item lifts out with quick actions; More… opens the full sheet; Back closes |
| F38 | UI | Tablet and landscape player layouts | BC | P3 | M | later | skipped | The app is portrait-only and used on phones |
| F39 | UI | Bounce overscroll throughout | BC | P3 | S | later | done | Rubber-band ends on every list and scroller, springing back; reduced motion keeps Android's stretch |
| F40 | QoL | In-app language picker and translations | BC | P2 | L | later | todo | Hindi first |
| F41 | Features | Desktop and TV apps | BC | P3 | XL | later | todo | |
| F42 | QoL | Pick up exactly where you left off after the app is closed: same queue, song and position | BopDop | P1 | M | 6 | todo | Today "resume" rebuilds a queue from history and starts at 0:00 |
| F43 | Player | Like, shuffle and repeat buttons in the notification and on the lock screen | BopDop | P1 | M | 6 | todo | Media3 custom layout; only play/skip there now |
| F44 | Player | Wrong or missing lyrics: search and pick another lyrics result | BopDop | P1 | M | 6 | todo | "No lyrics for this one" is a dead end now |
| F45 | QoL | Save the current queue as a playlist | BopDop | P1 | S | 6 | todo | |
| F46 | QoL | Stable release signing key (and Last.fm key in CI) | BopDop | P1 | S | 6 | todo | One-tap updates and Last.fm sign-in depend on it |
| F47 | Features | Auto-download Liked songs, Wi-Fi only option for downloads | BopDop | P2 | M | 7 | todo | |
| F48 | QoL | Private session: no history, stats or scrobbles for a while | BopDop | P2 | S | 7 | todo | |
| F49 | QoL | Launcher shortcuts on the app icon: Search, Liked songs, Shuffle all, Continue | BopDop | P2 | S | 7 | todo | |
| F50 | Player | Start radio from a playlist or album (not just one song) | BopDop | P2 | S | 7 | todo | |
| F51 | QoL | Resume playing when Bluetooth headphones or the car reconnect | BopDop | P2 | S | 7 | todo | Pausing on unplug already works |
| F52 | Player | Music video picture-in-picture when leaving the app | BopDop | P2 | M | 8 | todo | |
| F53 | UI | Battery saver: lighter glass and no moving backgrounds when the phone saves power | BopDop | P2 | S | 8 | todo | Uses what the lag work taught us |
| F54 | UI | Accessibility pass: TalkBack labels on every button, large-text check | BopDop | P2 | M | 8 | todo | Few content descriptions today |
| F55 | Features | Home "Continue listening": playlists and albums you were in the middle of | BopDop | P2 | M | 8 | todo | |
| F56 | QoL | Report a problem: share the app's recent log from Settings | BopDop | P2 | S | 8 | todo | Release builds can't be debugged on the phone |
| F57 | Player | Equalizer remembers a preset per output (headphones, speaker, car) | BopDop | P3 | S | later | todo | |
| F58 | Features | Wake-up alarm with a song or playlist | BopDop | P3 | M | later | todo | |
| F59 | Features | Voice search from the search bar | BopDop | P3 | S | later | todo | |
| F60 | Features | Edit or sync your own lyrics for a song | BopDop | P3 | M | later | todo | |

Sources: [BitChord](https://github.com/kushagrasinghx/BitChord) ([releases](https://github.com/kushagrasinghx/BitChord/releases)),
[LastWave](https://github.com/Clash-Projects/LastWave-Native) ([releases](https://github.com/Clash-Projects/LastWave-Native/releases)).
