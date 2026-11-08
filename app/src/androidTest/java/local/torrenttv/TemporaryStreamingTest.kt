// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Torrent TV contributors

package local.torrenttv
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import android.system.Os

@RunWith(AndroidJUnit4::class)
class TemporaryStreamingTest {
 @Test fun readsAcrossWindowsAndRedownloadsExpiredData() = runBlocking {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val url=InstrumentationRegistry.getArguments().getString("cacheFixtureUrl")
  org.junit.Assume.assumeTrue("Start tools/cache_fixture.py and pass cacheFixtureUrl",!url.isNullOrBlank())
  val session=TorrentSession(instrumentation.targetContext,64L*1024*1024)
  try {
   android.util.Log.i("CacheTest","Opening metadata")
   val file=session.open(url!!).single()
   assertTrue(file.size>64L*1024*1024)
   android.util.Log.i("CacheTest","Selecting video")
   session.select(file)
   android.util.Log.i("CacheTest","Reading windows")
   val data=ByteArray(8192)
   for(piece in listOf(0,120,20,240)) {
    val count=session.read(file,piece*1024L*1024+123,data,0,data.size) {false}
    assertEquals(data.size,count)
    assertTrue("Verified data at piece $piece",data.all {(it.toInt() and 255)==piece%251})
    if(piece==0) {
     // Fill the window, not just the first requested block, before measuring disk use.
     val one=ByteArray(1)
     for(p in 0..61) {session.read(file,p*1024L*1024,one,0,1) {false};assertEquals(p%251,one[0].toInt() and 255)}
     session.read(file,255L*1024*1024,one,0,1) {false}
    }
    val allocated=session.directory.walkTopDown().filter {it.isFile}.sumOf {Os.stat(it.path).st_blocks*512L}
    android.util.Log.i("CacheTest","piece=$piece allocatedBytes=$allocated")
    assertTrue("Physical disk $allocated exceeds cache plus filesystem allowance",allocated<=80L*1024*1024)
   }
  } finally {session.close()}
  assertFalse("Temporary data must be removed on close",session.directory.exists())
 }
}
