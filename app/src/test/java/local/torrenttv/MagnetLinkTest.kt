// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Torrent TV contributors

package local.torrenttv
import org.junit.Assert.*
import org.junit.Test
class MagnetLinkTest {
 private val hash="0123456789abcdef0123456789abcdef01234567"
 @Test fun acceptsEncodedMagnetWithoutLosingTrackers() {val link="magnet:?xt=urn%3Abtih%3A$hash&dn=Open%20movie&tr=udp%3A%2F%2Ftracker.test%3A80";assertEquals(link,MagnetLink.validate("  $link  "))}
 @Test fun acceptsBase32AndV2() {MagnetLink.validate("magnet:?xt=urn:btih:"+"A".repeat(32));MagnetLink.validate("magnet:?xt=urn:btmh:1220"+"a".repeat(64))}
 @Test(expected=IllegalArgumentException::class) fun rejectsWebLinks() {MagnetLink.validate("https://example.com/movie.torrent")}
 @Test(expected=IllegalArgumentException::class) fun rejectsMissingHash() {MagnetLink.validate("magnet:?dn=Movie")}
 @Test(expected=IllegalArgumentException::class) fun rejectsMalformedHash() {MagnetLink.validate("magnet:?xt=urn:btih:abc")}
 @Test(expected=IllegalArgumentException::class) fun rejectsOversizedLinks() {MagnetLink.validate("magnet:?xt=urn:btih:$hash&dn="+"a".repeat(9000))}
}
