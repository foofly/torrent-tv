// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Torrent TV contributors

package local.torrenttv
import org.junit.Assert.*
import org.junit.Test
class StreamWindowTest {
 private val mib=1024L*1024
 @Test fun hugeFileDoesNotIncreaseCacheBudget() {
  val window=StreamWindow.plan(700000,0,2000000,1024*1024,256*mib,setOf(0,2000000))
  assertTrue(700000 in window.pieces);assertTrue(0 in window.pieces);assertTrue(2000000 in window.pieces)
  assertTrue(window.pieces.size*mib<=256*mib)
 }
 @Test fun forwardAndBackwardSeeksReplaceExpiredPieces() {
  val first=StreamWindow.plan(0,0,99999,1024*1024,64*mib,setOf(0,99999))
  val forward=StreamWindow.plan(1000,0,99999,1024*1024,64*mib,setOf(0,99999))
  assertFalse(1000 in first.pieces);assertTrue(1000 in forward.pieces);assertFalse(20 in forward.pieces)
  val back=StreamWindow.plan(20,0,99999,1024*1024,64*mib,setOf(0,99999))
  assertTrue(20 in back.pieces);assertFalse(1000 in back.pieces)
 }
 @Test fun fileBoundaryNeverEnablesNeighbourFiles() {
  val window=StreamWindow.plan(105,100,110,1024*1024,256*mib,emptySet())
  assertTrue(window.pieces.all {it in 100..110});assertTrue(105 in window.pieces)
 }
 @Test fun randomizedSeeksStayBounded() {
  val random=java.util.Random(42)
  repeat(500) {
   val pieceLength=1 shl (14+random.nextInt(10));val requested=random.nextInt(100000)
   val window=StreamWindow.plan(requested,0,99999,pieceLength,256*mib,setOf(0,99999))
   assertTrue(requested in window.pieces);assertTrue(window.pieces.size.toLong()*pieceLength<=256*mib)
  }
 }
 @Test(expected=IllegalArgumentException::class) fun oversizedMetadataIsRejected() {StreamWindow.plan(0,0,50,1024*1024,2*mib,setOf(0,50))}
 @Test(expected=IllegalArgumentException::class) fun outOfRangeSeekRejected() {StreamWindow.plan(101,0,100,1024*1024,64*mib,emptySet())}
}
