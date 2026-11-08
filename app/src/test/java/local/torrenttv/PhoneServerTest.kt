// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Torrent TV contributors

package local.torrenttv
import org.junit.Assert.*
import org.junit.Test
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.net.URLEncoder
class PhoneServerTest {
 private val path="/"
 private val link="magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Open%20movie"
 private fun withServer(accepted: Boolean=true,test: (PhoneServer,MutableList<String>)->Unit) {
  val received=mutableListOf<String>();lateinit var server: PhoneServer
  server=PhoneServer(0,{it=="127.0.0.1:${server.listeningPort}"},{"<html>phone</html>".toByteArray()},{received.add(it);accepted})
  server.start(5000,true)
  try {test(server,received)} finally {server.stop()}
 }
 private fun request(server: PhoneServer,route: String=path,magnet: String?=null,origin: String?=null,header: Boolean=true): Pair<Int,String> {
  val builder=Request.Builder().url("http://127.0.0.1:${server.listeningPort}$route")
  if(origin!=null) builder.header("Origin",origin)
  if(magnet!=null) {
   if(header) builder.header("X-TV-Request","1")
   val body="magnet="+URLEncoder.encode(magnet,"UTF-8")
   builder.post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
  }
  return OkHttpClient().newCall(builder.build()).execute().use {it.code to it.body!!.string()}

 }
 @Test fun servesPackagedPageAtPlainRootAddress()=withServer {server,_ -> assertEquals(200,request(server).first)}
 @Test fun validSubmissionInvokesPlayback()=withServer {server,received -> assertEquals(202,request(server,path+"play",link).first);assertEquals(listOf(link),received)}
 @Test fun rejectsInvalidMagnet()=withServer {server,received -> assertEquals(400,request(server,path+"play","https://example.com").first);assertTrue(received.isEmpty())}
 @Test fun rejectsUnknownRoute()=withServer {server,received -> assertEquals(404,request(server,"/t/wrong/play",link).first);assertTrue(received.isEmpty())}
 @Test fun requiresForegroundReceiver()=withServer(false) {server,_ -> assertEquals(409,request(server,path+"play",link).first)}
 @Test fun rejectsCrossOriginRequests()=withServer {server,received -> assertEquals(403,request(server,path+"play",link,"http://other.test").first);assertTrue(received.isEmpty())}
 @Test fun rejectsFormPostsWithoutCustomHeader()=withServer {server,received -> assertEquals(403,request(server,path+"play",link,header=false).first);assertTrue(received.isEmpty())}
}
