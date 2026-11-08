// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Torrent TV contributors

package local.torrenttv
import android.content.Context
import org.libtorrent4j.*
import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import java.io.IOException
import java.util.UUID
import org.libtorrent4j.swig.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl

data class VideoFile(val index: Int, val path: String, val size: Long)
class TorrentSession(context: Context,private val targetCacheBytes: Long=StreamWindow.TARGET_BYTES) {
 companion object {private val ownerLock=Any();private var active: TorrentSession?=null}
 var directory = File(context.cacheDir,"torrent-streams/"+UUID.randomUUID()).apply {mkdirs()}; private set
 private val ioLock=Any()
 private var window: StreamWindow?=null
 private var budget=StreamWindow.TARGET_BYTES
 private var pinned=emptySet<Int>()
 private val readers=mutableMapOf<Int,RandomAccessFile>()
 private var cachedPiece=-1
 private var chosen: VideoFile?=null
 @Volatile private var paused=false
 @Volatile private var display="Preparing temporary cache…"
 private var torrentBytes=ByteArray(0)
 private val manager = SessionManager()
 lateinit var info: TorrentInfo
 lateinit var handle: TorrentHandle
 @Volatile var closed = false
 suspend fun open(source: String): List<VideoFile> = withContext(Dispatchers.IO) {
  synchronized(ownerLock) {
   active?.takeIf {it!==this@TorrentSession}?.close()
   active=this@TorrentSession
   directory.parentFile?.listFiles()?.filter {it!=directory}?.forEach {check(it.deleteRecursively()) {"Could not clear previous stream cache"}}
  }
  synchronized(ioLock) {
  check(!closed) {"Session closed"}
  manager.start()
  val bytes = if(source.startsWith("magnet:?")) manager.fetchMagnet(source,90,directory)
   ?: error("No metadata received. Check peers and network.") else resolve(source)
  ensureActive()
  torrentBytes=bytes
  info=TorrentInfo(bytes)

  val files=info.files()
  for(i in 0 until files.numFiles()) {
   val target=File(directory,files.filePath(i)).canonicalFile
   require(target.path.startsWith(directory.canonicalPath+File.separator)) { "Unsafe torrent path" }
  }
  (0 until files.numFiles()).filter { files.fileSize(it)>0 && files.filePath(it).substringAfterLast('.').lowercase() in setOf("mp4","mkv","webm","avi","mov","m4v","ts") }
   .map { VideoFile(it, files.filePath(it), files.fileSize(it)) }.also { require(it.isNotEmpty()) {"No video files in torrent"} }
  }
 }
 private fun resolve(source: String): ByteArray {
  val client=OkHttpClient.Builder().followRedirects(false).callTimeout(30,java.util.concurrent.TimeUnit.SECONDS).build()
  var url=source.toHttpUrl()
  val origin=source.toHttpUrl()
  repeat(5) {
   client.newCall(Request.Builder().url(url).build()).execute().use { response ->
    if(response.code in 300..399) {
     val location=response.header("Location") ?: error("Invalid redirect")
     if(location.startsWith("magnet:?")) return manager.fetchMagnet(location,90,directory) ?: error("No metadata received")
     val next=url.resolve(location) ?: error("Invalid download URL")
     require(url.scheme!="https" || next.scheme=="https") {"Insecure redirect rejected"}
     require(next.queryParameter("apikey")==null || (next.host==origin.host && next.port==origin.port && next.scheme==origin.scheme)) {"Credential redirect rejected"}
     url=next
    } else {
     check(response.isSuccessful) {"Torrent download failed: HTTP ${response.code}"}
     val data=response.body!!.byteStream().use { input -> val out=java.io.ByteArrayOutputStream(); val chunk=ByteArray(8192); while(out.size()<=10*1024*1024) {val n=input.read(chunk); if(n<0) break; out.write(chunk,0,n)}; out.toByteArray() }
     require(data.size<=10*1024*1024) {"Torrent metadata too large"}; return data
    }
   }
  }
  error("Too many redirects")
 }
 suspend fun select(file: VideoFile) = withContext(Dispatchers.IO) {
  synchronized(ioLock) {
   check(!closed) {"Session closed"}
   chosen=file
   // Metadata fetches can leave a temporary torrent in the session. Stop before cleanup.
   manager.stop()
   check(directory.deleteRecursively()) {"Could not clear temporary metadata"}
   check(directory.mkdirs()) {"Could not create temporary cache"}
   budget=minOf(targetCacheBytes,directory.usableSpace-StreamWindow.RESERVE_BYTES)
   require(budget>=64L*1024*1024) {"Streaming needs at least 64 MiB cache plus 512 MiB free-space reserve"}
   val storage=info.files();val length=info.pieceLength()
   val pins=mutableSetOf<Int>()
   fun pinRange(index: Int,start: Long,end: Long) {
    if(end<start) return
    val a=PieceWindow.piece(storage.fileOffset(index),start,length)
    val b=PieceWindow.piece(storage.fileOffset(index),end,length)
    for(piece in a..b) pins.add(piece)
   }
   pinRange(file.index,0,minOf(file.size-1,1024L*1024-1))
   pinRange(file.index,maxOf(0,file.size-1024L*1024),file.size-1)
   for(i in 0 until info.numFiles()) {
    val size=storage.fileSize(i)
    if(storage.filePath(i).endsWith(".srt",true) && size in 1..5L*1024*1024) {
     val before=pins.toSet();pinRange(i,0,size-1)
     if(pins.size.toLong()*length>minOf(32L*1024*1024,budget/4)) {pins.clear();pins.addAll(before)}
    }
   }
   pinned=pins
   startWindow(file,0)
  }
 }
 /** Called with ioLock held; native session shutdown completes before files are deleted. */
 private fun startWindow(file: VideoFile,position: Long) {
  check(!closed) {"Session closed"}
  val storage=info.files();val pieceLength=info.pieceLength()
  val first=PieceWindow.piece(storage.fileOffset(file.index),0,pieceLength)
  val last=PieceWindow.piece(storage.fileOffset(file.index),file.size-1,pieceLength)
  val requested=PieceWindow.piece(storage.fileOffset(file.index),position,pieceLength)
  val next=StreamWindow.plan(requested,first,last,pieceLength,budget,pinned)
  display="Refreshing temporary chunk…"
  readers.values.forEach {it.close()};readers.clear();cachedPiece=-1
  manager.stop()
  check(directory.deleteRecursively()) {"Could not remove expired temporary chunks"}
  check(directory.mkdirs()) {"Could not create temporary chunk directory"}
  require(directory.usableSpace>=budget+128L*1024*1024) {"Not enough free space for temporary streaming cache"}
  manager.start()
  // Loading full metainfo preserves trackers and webseeds, not just the info dictionary.
  val metadata=File(directory,"session.torrent").apply {writeBytes(torrentBytes)}
  val params=libtorrent.load_torrent_file(metadata.absolutePath)
  params.save_path=directory.absolutePath
  params.storage_mode=storage_mode_t.storage_mode_sparse
  // Start paused and not auto-managed: no whole-file download can start before priorities apply.
  params.flags=params.flags.and_(libtorrent.getAuto_managed().inv()).or_(libtorrent.getPaused())
  val priorities=byte_vector()
  for(i in 0 until info.numFiles()) {
   val selected=i==chosen?.index || (storage.filePath(i).endsWith(".srt",true) && storage.fileSize(i)<=5L*1024*1024)
   priorities.add(if(selected) Priority.DEFAULT.swig() else Priority.IGNORE.swig())
  }
  params.set_file_priorities(priorities)
  val wanted=byte_vector()
  for(i in 0 until info.numPieces()) wanted.add(if(i in next.pieces) Priority.DEFAULT.swig() else Priority.IGNORE.swig())
  params.set_piece_priorities(wanted)
  manager.swig().async_add_torrent(params)
  val expires=System.nanoTime()+15_000_000_000L
  while(true) {
   if(closed || Thread.currentThread().isInterrupted) throw IOException("Streaming cancelled")
   val found=manager.find(info.infoHash())
   if(found!=null && found.isValid) {handle=found;break}
   if(System.nanoTime()>expires) throw IOException("Torrent cache startup timed out")
   Thread.sleep(25)
  }
  val pieces=Priority.array(Priority.IGNORE,info.numPieces())
  next.pieces.forEach {pieces[it]=Priority.DEFAULT}
  handle.prioritizePieces(pieces)
  window=next
  if(!paused) handle.resume()
  display="Temporary cache: ${budget/1048576} MiB limit"
 }
 /** Serialize reads with window replacement so no reader can access an expired file. */
 fun read(file: VideoFile,position: Long,buffer: ByteArray,offset: Int,length: Int,cancelled: ()->Boolean): Int = synchronized(ioLock) {
  if(closed || cancelled()) throw IOException("Streaming cancelled")
  val storage=info.files();val pieceLength=info.pieceLength()
  val piece=PieceWindow.piece(storage.fileOffset(file.index),position,pieceLength)
  if(window?.pieces?.contains(piece)!=true) startWindow(file,position)
  val current=window ?: throw IOException("Cache unavailable")
  // Never set deadlines outside the allocated window: deadlines can enable ignored pieces.
  if(cachedPiece!=piece) {
   handle.clearPieceDeadlines()
   for(p in piece..minOf(info.numPieces()-1,piece+16)) if(p in current.pieces) handle.setPieceDeadline(p,(p-piece)*200)
   cachedPiece=piece
  }
  val expires=System.nanoTime()+90_000_000_000L
  while(!handle.havePiece(piece)) {
   if(closed || cancelled() || Thread.currentThread().isInterrupted) throw IOException("Streaming cancelled")
   if(paused) throw IOException("Playback paused while app is in background")
   if(System.nanoTime()>expires) throw IOException("Torrent stalled: no verified data for 90 seconds")
   if(directory.usableSpace<64L*1024*1024) throw IOException("TV storage is nearly full")
   try {Thread.sleep(50)} catch(e: InterruptedException) {Thread.currentThread().interrupt();throw IOException("Streaming cancelled",e)}
  }
  val reader=readers.getOrPut(file.index) {RandomAccessFile(File(directory,file.path),"r")}
  val count=PieceWindow.readSize(storage.fileOffset(file.index),position,pieceLength,file.size-position,length)
  reader.seek(position);reader.readFully(buffer,offset,count)
  count
 }
 fun stats(): String = synchronized(ioLock) {
  if(closed || !::handle.isInitialized || !handle.isValid) display else {
   val s=handle.status()
   "${s.numPeers()} peers • ${s.downloadRate()/1024} KiB/s • ${s.totalDone()/1048576}/${budget/1048576} MiB temporary cache"
  }
 }
 fun pause() {paused=true;synchronized(ioLock) {if(manager.isRunning) manager.pause()}}
 fun resume() {paused=false;synchronized(ioLock) {if(manager.isRunning) {manager.resume();if(::handle.isInitialized && handle.isValid) handle.resume()}}}
 fun close() {
  closed=true
  synchronized(ioLock) {
   readers.values.forEach {runCatching {it.close()}};readers.clear()
   manager.stop();directory.deleteRecursively();window=null
  }
 }
}
