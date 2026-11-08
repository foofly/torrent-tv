// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Torrent TV contributors

package local.torrenttv
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PhoneReceiverTest {
 @Test fun servesPhonePageAndDispatchesMagnetToPlayer() {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val context=instrumentation.targetContext
  val main=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  instrumentation.waitForIdleSync()
  val base="http://127.0.0.1:${PhoneReceiver.PORT}/"
  val page=URL(base).readText()
  assertTrue(page.contains("<textarea"));assertTrue(page.contains(">Go</button>"));assertFalse(page.contains("Search"))
  val magnet="magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"
  val dispatched=CountDownLatch(1)
  var received: String?=null
  // Intercept launch after it traverses HTTP and the UI handler, avoiding public swarm traffic.
  val monitor=object: Instrumentation.ActivityMonitor() {
   override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
    if(intent.component?.className==PlaybackActivity::class.java.name) {
     received=intent.getStringExtra("source");dispatched.countDown()
     return Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null)
    }
    return null
   }
  }
  instrumentation.addMonitor(monitor)
  try {
   val c=URL(base+"play").openConnection() as HttpURLConnection
   c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");c.setRequestProperty("X-TV-Request","1")
   val bytes=("magnet="+URLEncoder.encode(magnet,"UTF-8")).toByteArray();c.setFixedLengthStreamingMode(bytes.size);c.outputStream.use {it.write(bytes)}
   assertEquals(202,c.responseCode);c.inputStream.close();c.disconnect()
   assertTrue("HTTP submission must dispatch player intent",dispatched.await(5,TimeUnit.SECONDS));assertEquals(magnet,received)
  } finally {instrumentation.removeMonitor(monitor);instrumentation.runOnMainSync {main.finish()}}
 }
}
