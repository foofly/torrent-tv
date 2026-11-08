// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Torrent TV contributors

package local.torrenttv
import org.junit.Assert.*
import org.junit.Test
class PieceWindowTest {
 @Test fun fileStartingInsidePiece() { assertEquals(2,PieceWindow.piece(3000,0,1024)); assertEquals(72,PieceWindow.readSize(3000,0,1024,5000,4096)) }
 @Test fun nextPieceAfterBoundary() { assertEquals(3,PieceWindow.piece(3000,72,1024)); assertEquals(1024,PieceWindow.readSize(3000,72,1024,5000,4096)) }
 @Test fun finalShortRead() {assertEquals(17,PieceWindow.readSize(3000,72,1024,17,4096))}
 @Test fun emptyRead() {assertEquals(0,PieceWindow.readSize(0,0,1024,0,4096))}
 @Test(expected=IllegalArgumentException::class) fun negativeSeekRejected() {PieceWindow.piece(0,-1,1024)}
}
