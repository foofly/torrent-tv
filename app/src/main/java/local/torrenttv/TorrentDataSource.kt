// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Torrent TV contributors

package local.torrenttv
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TorrentDataSource(private val session: TorrentSession, private var selected: VideoFile): BaseDataSource(false) {
 private var position=0L; private var remaining=0L; private var uri: Uri?=null
 @Volatile private var cancelled=false
 override fun open(spec: DataSpec): Long {
  transferInitializing(spec); cancelled=false; uri=spec.uri; position=spec.position
  val index=spec.uri.lastPathSegment?.toIntOrNull() ?: selected.index
  if(index!=selected.index) {
   val storage=session.info.files()
   if(index !in 0 until storage.numFiles() || !storage.filePath(index).endsWith(".srt",true) || storage.fileSize(index)>5*1024*1024) throw IOException("Invalid subtitle")
   selected=VideoFile(index,storage.filePath(index),storage.fileSize(index))
  }
  if(position>selected.size) throw IOException("Read past end")
  remaining=if(spec.length==C.LENGTH_UNSET.toLong()) selected.size-position else minOf(spec.length,selected.size-position)
  transferStarted(spec); return remaining
 }
 override fun getUri()=uri
 override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
  if(length==0) return 0
  if(remaining==0L) return C.RESULT_END_OF_INPUT
  val count=session.read(selected,position,buffer,offset,minOf(length.toLong(),remaining).toInt()) {cancelled}
  position+=count; remaining-=count; bytesTransferred(count); return count
 }
 override fun close() {cancelled=true; if(uri!=null) transferEnded(); uri=null}
}
