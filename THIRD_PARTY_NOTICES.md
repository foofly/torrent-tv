# Third-party components

The application and phone page were written for this project. No application source from Torflix, Rivulet, or Butter is included.

Direct runtime dependencies:

- AndroidX Activity, Compose and Media3: Apache-2.0.
- Kotlin and kotlinx.coroutines: Apache-2.0.
- OkHttp/Okio: Apache-2.0.
- libtorrent4j: MIT, with native libtorrent and its bundled dependencies.
- NanoHTTPD: BSD-3-Clause.
- ZXing Core: Apache-2.0.

JUnit and AndroidX Test are used for testing only. The local phone page loads no external assets or services. No Jackett or custom FFmpeg build is bundled.

This is a dependency inventory. Before public distribution, collect complete resolved dependency licence texts and native-library notices.

## Project licence

Original application code, tests, and phone assets are licensed under **GPL-3.0-or-later**; see [LICENSE](LICENSE). Third-party dependencies retain their own licences. The Gradle wrapper scripts/JAR are Gradle components under Apache-2.0 and retain their upstream notices.
