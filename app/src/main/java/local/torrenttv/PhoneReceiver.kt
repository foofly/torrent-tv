// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Torrent TV contributors

package local.torrenttv
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.flow.MutableStateFlow

data class ReceiverState(val addresses: List<String> = emptyList(),val status: String="Ready for a magnet link")
object PhoneReceiver {
 const val PORT=8765
 val state=MutableStateFlow(ReceiverState())
 private val main=Handler(Looper.getMainLooper())
 private var server: PhoneServer?=null
 @Volatile private var activity=WeakReference<Activity>(null)
 fun ensure(context: Context) {
  if(server!=null) return
  refreshAddresses()
  val app=context.applicationContext
  val candidate=PhoneServer(PORT,
   allowedHost={host -> host in (state.value.addresses.map {"$it:$PORT"}+listOf("127.0.0.1:$PORT","localhost:$PORT"))},
   asset={name -> app.assets.open("phone/$name").use {it.readBytes()}},play={magnet -> submit(magnet)})
  try {candidate.start(5000,true);server=candidate}
  catch(e: Exception) {candidate.stop();status("Phone receiver could not start. Reopen the TV app to retry.")}
 }
 fun refreshAddresses() {
  val addresses=runCatching {NetworkInterface.getNetworkInterfaces().toList().filter {it.isUp && !it.isLoopback}.sortedBy {if(it.name.startsWith("eth") || it.name.startsWith("wlan")) 0 else 1}.flatMap {it.inetAddresses.toList()}.filterIsInstance<Inet4Address>().filter {it.isSiteLocalAddress}.map {it.hostAddress!!}.distinct()}.getOrDefault(emptyList())
  state.value=state.value.copy(addresses=addresses)
 }
 fun url(address: String)="http://$address:$PORT/"
 fun bind(host: Activity) {activity=WeakReference(host)}
 fun unbind(host: Activity) {if(activity.get()===host) activity.clear()}
 fun status(message: String) {state.value=state.value.copy(status=message)}
 private fun submit(magnet: String): Boolean {
  val host=activity.get() ?: return false
  main.post {
   if(activity.get()!==host || host.isFinishing || host.isDestroyed) {status("Open Torrent TV, then send the link again");return@post}
   status("Finding torrent metadata…")
   host.startActivity(Intent(host,PlaybackActivity::class.java).putExtra("source",magnet).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
  };return true
 }
}
