# Releasing BopDop

Every BopDop APK must be signed with the same key, or Android refuses to update one with another
(the in-app updater included). The key is `release.keystore` in the project folder; it is in
`.gitignore` and must never be committed. Keep a copy somewhere safe: losing it means every user
has to uninstall to get the next version.

## On this PC

`local.properties` points the build at it:

```
release.storeFile=release.keystore
release.storePassword=…
release.keyAlias=…
release.keyPassword=…
```

`./gradlew :app:assembleRelease` then signs with it.

## On GitHub

The Release workflow needs four repository secrets:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_B64` | `base64 -w0 release.keystore` |
| `RELEASE_STORE_PASSWORD` | the keystore password |
| `RELEASE_KEY_ALIAS` | the key alias |
| `RELEASE_KEY_PASSWORD` | the key password |

Without them the workflow stops instead of publishing an APK nobody can update to.

## Last.fm

BopDop ships no Last.fm key. Each user creates their own at last.fm/api/account/create and pastes
it in Settings › Scrobbling › Last.fm. Libre.fm needs none.
