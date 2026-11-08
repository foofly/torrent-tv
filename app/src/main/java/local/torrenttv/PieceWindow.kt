// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Torrent TV contributors

package local.torrenttv
/** All arithmetic uses torrent-global offsets; files can begin halfway through a piece. */
object PieceWindow {
 fun piece(fileOffset: Long, position: Long, pieceLength: Int): Int {
  require(fileOffset>=0 && position>=0 && pieceLength>0)
  return Math.addExact(fileOffset,position).div(pieceLength).toInt()
 }
 fun readSize(fileOffset: Long, position: Long, pieceLength: Int, remaining: Long, requested: Int): Int {
  require(remaining>=0 && requested>=0)
  piece(fileOffset,position,pieceLength)
  return minOf(requested.toLong(),remaining,pieceLength-(fileOffset+position)%pieceLength).toInt()
 }
}
