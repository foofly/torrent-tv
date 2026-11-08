# Torrent TV

An Android TV torrent player controlled from a phone or laptop on the same local network.

1. Open **Torrent TV** on the TV.
2. Scan its QR code, or type the displayed local address into a browser.
3. Paste a magnet link and press **Go**.

The browser page contains only a magnet box, Go button, and submission feedback. The TV downloads the torrent and plays it fullscreen. No accounts, search services, Jackett, or cloud hosting are needed. For torrents containing several videos, choose a file with the TV remote.

## Features

- Phone/laptop submission at `http://TV-IP:8765/`, with a QR code on the TV.
- On-device torrent playback with bounded temporary storage.
- Remote playback controls, seeking, playback speed, aspect-ratio options, and resume positions.
- Embedded audio/subtitle selection and bundled SRT support.
- Pure-black letterbox/pillarbox areas, auto-hiding controls, and a buffering indicator.

Keep the TV app visible, either on its QR screen or in the player, to accept links. It does not wake the TV or launch over another app. Both devices must be able to communicate on the LAN. The receiver uses local HTTP without authentication; any reachable LAN device can submit a link. Do not forward its port to the internet.

## Build and install

Requirements:

- JDK 25, Gradle 9.6.0 (included wrapper), and Android SDK command-line tools.
- Android SDK platform `platforms;android-37.0` and build tools `build-tools;36.0.0`.
- Android TV 9+ for installation. ARMv7, ARM64 and x86_64 native libraries are included.

Install the SDK packages:

```sh
sdkmanager 'platforms;android-37.0' 'build-tools;36.0.0'
```

Set `ANDROID_HOME`, or create an untracked `local.properties` containing `sdk.dir=/absolute/path/to/Android/Sdk`.

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows use `gradlew.bat`. Debug APKs are development-signed. Builds from different machines can have different debug signing keys, so an APK from CI may require uninstalling an existing development build first.

The GitHub Actions workflow builds the app, runs unit tests and lint, compiles device tests, and uploads a debug APK plus reports. APKs and local `dist/` files are not committed to Git.

## Temporary storage

Cache windows target **256 MiB** of torrent data. With limited space, the budget can shrink to 64 MiB; startup also requires a 512 MiB free-space reserve. Pinned container headers/tails and small SRT files count toward the piece budget. Filesystem/metainfo overhead is additional. Sparse files may report the video's full logical size without allocating that much disk space.

Expired windows are deleted after closing the native torrent session. Seeking to expired data downloads it again; reconnecting at window boundaries can briefly buffer. Normal playback exit deletes the temporary directory, and the next stream removes abandoned directories. Resume saves a playback position, not a permanent download. BitTorrent uploads can occur during an active session.

Device-supported codecs are required; there is no transcoding. Sustained high-bitrate playback and device-specific decoding still need broader testing.

## Tests

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

The 24 unit tests cover magnet validation, real receiver HTTP requests, request/origin checks, piece offsets and bounded cache planning. Device tests require a connected Android TV:

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=local.torrenttv.PhoneReceiverTest
```

The cache test uses a private synthetic 256 MiB torrent, without public-swarm traffic. Start the fixture on the development PC's LAN address:

```sh
python3 tools/cache_fixture.py --host 192.168.1.100
```

Replace the example address with your PC's address, then run:

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=local.torrenttv.TemporaryStreamingTest -Pandroid.testInstrumentationRunnerArguments.cacheFixtureUrl=http://192.168.1.100:18761/fixture.torrent
```

The test fills a 64 MiB window, verifies forward/backward reads and physical disk allocation, and checks cleanup. Stop the fixture with Ctrl+C afterward. Use `--help` for bind/advertised-address options.

See [VALIDATION.md](VALIDATION.md) for tested behavior and limits.

## Licence

Original project code is licensed under **GPL-3.0-or-later**: GNU GPL version 3, or (at your option) any later version. See [LICENSE](LICENSE). Third-party components, including the Apache-licensed Gradle wrapper, retain their own licences; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
