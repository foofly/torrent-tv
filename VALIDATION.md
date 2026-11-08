# Validation

Last local verification: 2026-11-08. App version: **0.2.4**.

- Debug APK assembly and device-test APK compilation: passed.
- All 24 unit tests: passed.
- Android lint: passed with nonblocking warnings.
- Official Gradle 9.6.0 wrapper JAR and distribution checksums: verified.
- Portable cache-fixture CLI/bytecode and loopback metadata/cross-piece range checks: passed.
- Staged upload audit: no credential patterns, machine-specific paths, APKs, local SDK settings or build caches found.
- `PhoneReceiverTest` on SHIELD Android TV (Android 11, ARMv7): passed. The real HTTP receiver served the page and dispatched the submitted magnet to the player. The test intercepted the player launch to avoid public-swarm traffic.
- LAN receiver smoke check: the TV's plain root address returned the magnet page with HTTP 200.
- Existing resume preferences survived the cleanup unchanged.

The native cache test previously passed on this TV with a private 256 MiB torrent and a 64 MiB cache. It filled the initial window, verified bytes across forward/backward window replacement, checked physical allocation below 80 MiB, and verified deletion on close. The cache data path was not changed during repository preparation.

GitHub Actions CI has been prepared but cannot be verified on GitHub until the repository is uploaded. Device tests are not run by CI; they require hardware and, for the cache test, the local fixture.

## Manual acceptance limits

Automated checks do not establish sustained video decoding, high-bitrate performance, physical-phone QR scanning, or overlay/spinner appearance and timing. Window reconnection can cause brief buffering. The TV app must remain visible to accept submissions.
