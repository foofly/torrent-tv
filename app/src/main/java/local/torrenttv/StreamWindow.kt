// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Torrent TV contributors

package local.torrenttv

/** Whole-piece budgets include pinned metadata and file-boundary overlap. */
data class StreamWindow(val pieces: Set<Int>,val budgetBytes: Long) {
 companion object {
  const val TARGET_BYTES=256L*1024*1024
  const val RESERVE_BYTES=512L*1024*1024
  fun plan(requested: Int,first: Int,last: Int,pieceLength: Int,budget: Long,pinned: Set<Int>): StreamWindow {
   require(pieceLength>0 && first>=0 && last>=first && requested in first..last)
   val slots=(budget/pieceLength).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
   require(slots>pinned.size) {"Torrent pieces or metadata exceed the temporary cache budget"}
   val count=slots-pinned.size
   val start=maxOf(first,requested-minOf(4,count/4))
   val end=minOf(last.toLong(),start.toLong()+count-1).toInt()
   return StreamWindow(pinned+(start..end),budget)
  }
 }
}
