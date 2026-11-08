// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Torrent TV contributors

package local.torrenttv
import fi.iki.elonen.NanoHTTPD
/** Local receiver page and same-origin playback endpoint. */
class PhoneServer(port: Int,
 private val allowedHost: (String)->Boolean,
 private val asset: (String)->ByteArray,
 private val play: (String)->Boolean): NanoHTTPD(port) {
 private fun reply(status: Response.Status,body: String,mime: String="text/plain"): Response = newFixedLengthResponse(status,mime,body).apply {
  addHeader("Cache-Control","no-store");addHeader("Referrer-Policy","no-referrer")
  addHeader("X-Content-Type-Options","nosniff");addHeader("X-Frame-Options","DENY")
  addHeader("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
 }
 override fun serve(session: IHTTPSession): Response {
  val host=session.headers["host"]?.lowercase() ?: ""
  if(!allowedHost(host)) return reply(Response.Status.FORBIDDEN,"Use the TV's local address.")
  val origin=session.headers["origin"]
  if(origin!=null && origin!="http://$host") return reply(Response.Status.FORBIDDEN,"Open the page directly from the TV's local address.")
  val route=session.uri.removePrefix("/")
  if(session.method==Method.GET && route in setOf("","app.js","styles.css")) {
   val name=route.ifEmpty {"index.html"}
   val mime=when(name) {"app.js"->"application/javascript";"styles.css"->"text/css";else->"text/html"}
   return reply(Response.Status.OK,asset(name).toString(Charsets.UTF_8),mime)
  }
  if(route!="play") return reply(Response.Status.NOT_FOUND,"Not found.")
  if(session.method!=Method.POST) return reply(Response.Status.METHOD_NOT_ALLOWED,"Use the Go button.")
  if(session.headers["x-tv-request"]!="1") return reply(Response.Status.FORBIDDEN,"Use the phone page to submit a link.")
  val length=session.headers["content-length"]?.toLongOrNull()
  if(length==null || length !in 1..32768 || session.headers.containsKey("transfer-encoding")) return reply(Response.Status.BAD_REQUEST,"The submitted link is too large.")
  if(session.headers["content-type"]?.substringBefore(';')?.trim()!="application/x-www-form-urlencoded") return reply(Response.Status.BAD_REQUEST,"Invalid submission.")
  return try {
   session.parseBody(mutableMapOf())
   val magnet=MagnetLink.validate(session.parameters["magnet"]?.singleOrNull() ?: "")
   if(play(magnet)) reply(Response.Status.ACCEPTED,"Opening on the TV. If there are several videos, choose one with the TV remote.")
   else reply(Response.Status.CONFLICT,"Open Torrent TV on the TV, then tap Go again.")
  } catch(e: IllegalArgumentException) {reply(Response.Status.BAD_REQUEST,e.message ?: "Invalid magnet link.")}
   catch(e: Exception) {reply(Response.Status.BAD_REQUEST,"Could not read the magnet link.")}
 }
}
