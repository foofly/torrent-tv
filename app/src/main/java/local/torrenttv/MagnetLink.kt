// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Torrent TV contributors

package local.torrenttv
import java.net.URI
import java.net.URLDecoder
object MagnetLink {
 fun validate(raw: String): String {
  val value=raw.trim()
  require(value.length in 1..8192 && value.none {it.code<32 || it.isWhitespace()}) {"Paste a valid magnet link."}
  val uri=runCatching {URI(value)}.getOrNull()
  require(uri?.scheme.equals("magnet",true) && value.substringAfter(':').startsWith("?")) {"Only magnet links are accepted."}
  val hashes=value.substringAfter('?').substringBefore('#').split('&').map {it.split('=',limit=2)}.filter {it.size==2 && it[0].equals("xt",true)}.map {URLDecoder.decode(it[1],"UTF-8")}
  require(hashes.any {Regex("(?i)urn:btih:(?:[0-9a-f]{40}|[a-z2-7]{32})|urn:btmh:1220[0-9a-f]{64}").matches(it)}) {"The magnet link needs a valid BitTorrent hash."}
  return "magnet:"+value.substringAfter(':')
 }
}
