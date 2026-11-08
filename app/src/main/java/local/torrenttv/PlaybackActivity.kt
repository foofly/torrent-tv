// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Torrent TV contributors

package local.torrenttv
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import android.os.Bundle
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.content.Intent
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.*
import android.app.AlertDialog
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.*
import androidx.media3.ui.PlayerView
import androidx.media3.datasource.DataSource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackActivity: ComponentActivity() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private lateinit var session: TorrentSession
 private var player: ExoPlayer?=null
 private lateinit var view: PlayerView
 private var historyKey=""
 private lateinit var controls: LinearLayout
 private lateinit var options: Button
 private lateinit var stats: TextView
 private lateinit var loadingIcon: ProgressBar
 private lateinit var loadingText: TextView
 private var overlayHideJob: Job?=null
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  onBackPressedDispatcher.addCallback(this,object: OnBackPressedCallback(true) {override fun handleOnBackPressed() {if(player!=null && view.isControllerFullyVisible) view.hideController() else finish()}})
  window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
  window.decorView.setBackgroundColor(Color.BLACK)
  window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  WindowCompat.getInsetsController(window,window.decorView).apply {
   systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
   hide(WindowInsetsCompat.Type.systemBars())
  }
  val dp=resources.displayMetrics.density
  loadingIcon=ProgressBar(this).apply {
   isIndeterminate=true;indeterminateTintList=ColorStateList.valueOf(Color.WHITE)
   contentDescription="Loading video";isFocusable=false
  }
  loadingText=TextView(this).apply {setTextColor(Color.WHITE);text="Finding torrent metadata…";textSize=18f;gravity=android.view.Gravity.CENTER}
  val loading=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL;gravity=android.view.Gravity.CENTER;setBackgroundColor(Color.BLACK)
   addView(loadingIcon,LinearLayout.LayoutParams((36*dp).toInt(),(36*dp).toInt()))
   addView(loadingText,LinearLayout.LayoutParams(-2,-2).apply {topMargin=(16*dp).toInt()})
  }
  setContentView(loading)
  PhoneReceiver.ensure(this);PhoneReceiver.status("Finding torrent metadata…")
  session=TorrentSession(this)
  scope.launch {
   try {
    val files=session.open(intent.getStringExtra("source") ?: error("Missing torrent"))
    if(files.size==1) play(files.first()) else {loadingIcon.visibility=android.view.View.GONE;PhoneReceiver.status("Choose a video with the TV remote");AlertDialog.Builder(this@PlaybackActivity).setTitle("Choose video")
     .setItems(files.map {it.path}.toTypedArray()) {_,index -> scope.launch {play(files[index])} }.setOnCancelListener {finish()}.show()}
   } catch(e: CancellationException) {throw e} catch(e: Exception) {showError(e)}
  }
 }
 override fun onNewIntent(intent: Intent) {super.onNewIntent(intent);setIntent(intent);recreate()}
 private suspend fun play(file: VideoFile) {
  try {
   loadingIcon.visibility=android.view.View.VISIBLE;loadingText.text="Buffering video…"
   PhoneReceiver.status("Buffering video…")
   session.select(file)
   historyKey=session.info.infoHash().toString()+":"+file.index
   val factory=DataSource.Factory {TorrentDataSource(session,file)}
   player=ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(factory)).build().also {p ->
    p.setAudioAttributes(AudioAttributes.DEFAULT,true); p.setHandleAudioBecomingNoisy(true)
    p.addListener(object: Player.Listener {
     override fun onRenderedFirstFrame() {hideOverlay()}
     override fun onIsPlayingChanged(isPlaying: Boolean) {if(isPlaying) hideOverlay()}
     override fun onPlayerError(error: PlaybackException) {showError(error)}
     override fun onPlaybackStateChanged(state: Int) {PhoneReceiver.status(when(state) {
      Player.STATE_BUFFERING -> "Buffering video…"
      Player.STATE_ENDED -> "Finished playing"
      Player.STATE_READY -> if(p.playWhenReady) "Playing video" else "Paused"
      else -> "Opening video…"
     })}
    })
    val subs=(0 until session.info.numFiles()).filter {session.info.files().filePath(it).endsWith(".srt",true) && session.info.files().fileSize(it)<=5*1024*1024}.map {index ->
     MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse("torrent://video/$index")).setMimeType(MimeTypes.APPLICATION_SUBRIP).setLabel(session.info.files().fileName(index)).build()
    }
    p.setMediaItem(MediaItem.Builder().setUri("torrent://video/${file.index}").setSubtitleConfigurations(subs).build())
    p.seekTo(getSharedPreferences("history",MODE_PRIVATE).getLong(historyKey,0)); p.prepare();p.playWhenReady=true
   }
   view=PlayerView(this).apply {setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);setBackgroundColor(Color.BLACK);setShutterBackgroundColor(Color.BLACK);findViewById<android.view.View>(androidx.media3.ui.R.id.exo_content_frame)?.setBackgroundColor(Color.BLACK);player=this@PlaybackActivity.player; controllerShowTimeoutMs=3000;controllerAutoShow=false; setShowSubtitleButton(true);setShowNextButton(false);setShowPreviousButton(false)}
   view.findViewById<ProgressBar>(androidx.media3.ui.R.id.exo_buffering)?.apply {
    indeterminateTintList=ColorStateList.valueOf(Color.WHITE);contentDescription="Buffering";isFocusable=false
   }
   val frame=FrameLayout(this).apply {setBackgroundColor(Color.BLACK)};frame.addView(view,FrameLayout.LayoutParams(-1,-1))
   controls=LinearLayout(this).apply {visibility=android.view.View.GONE;orientation=LinearLayout.VERTICAL;setPadding(24,12,24,12);setBackgroundColor(0xBB000000.toInt())}
   options=Button(this).apply {text="Playback options";setOnClickListener {showOptions()}};controls.addView(options)
   stats=TextView(this).apply {setTextColor(android.graphics.Color.WHITE)};controls.addView(stats)
   frame.addView(controls,FrameLayout.LayoutParams(-1,-2,android.view.Gravity.TOP))
   view.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener {
    controls.visibility=it
    if(it==android.view.View.VISIBLE) scheduleOverlayHide()
   })
   setContentView(frame);view.requestFocus();hideOverlay()
   scope.launch {while(isActive) {
    val line=withContext(Dispatchers.IO) {session.stats()}
    stats.text=line;delay(1000)
   }}
  } catch(e: CancellationException) {throw e} catch(e: Exception) {showError(e)}
 }
 private fun hideOverlay() {
  if(::view.isInitialized) view.hideController()
  if(::controls.isInitialized) {
   val hadFocus=controls.hasFocus();controls.visibility=android.view.View.GONE
   if(hadFocus && ::view.isInitialized) view.requestFocus()
  }
 }
 private fun scheduleOverlayHide() {
  overlayHideJob?.cancel()
  if(player?.playWhenReady==true) overlayHideJob=scope.launch {delay(3000);if(player?.playWhenReady==true) hideOverlay()}
 }
 override fun onUserInteraction() {super.onUserInteraction();scheduleOverlayHide()}
 private fun showOptions() {
  val p=player ?: return
  AlertDialog.Builder(this).setTitle("Playback options").setItems(arrayOf("Speed: 0.75×","Speed: 1×","Speed: 1.25×","Speed: 1.5×","Fit picture","Fill picture","Audio tracks","Subtitles")) {_,which ->
   when(which) {
    in 0..3 -> p.setPlaybackSpeed(floatArrayOf(.75f,1f,1.25f,1.5f)[which])
    4 -> view.resizeMode=androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
    5 -> view.resizeMode=androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    6 -> chooseTrack(C.TRACK_TYPE_AUDIO)
    7 -> chooseTrack(C.TRACK_TYPE_TEXT)
   }
  }.show()
 }
 private fun chooseTrack(type: Int) {
  val p=player ?: return
  val tracks=p.currentTracks.groups.filter {it.type==type}.flatMap {group -> (0 until group.length).map {group to it}}
  val labels=listOf("Off / automatic")+tracks.map {(group,index) -> val f=group.getTrackFormat(index);f.label ?: f.language ?: "Track ${index+1}"}
  AlertDialog.Builder(this).setTitle(if(type==C.TRACK_TYPE_AUDIO) "Audio" else "Subtitles").setItems(labels.toTypedArray()) {_,which ->
   val builder=p.trackSelectionParameters.buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type,which==0 && type==C.TRACK_TYPE_TEXT)
   if(which>0) {val (group,index)=tracks[which-1];builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup,index))}
   p.trackSelectionParameters=builder.build()
  }.show()
 }

 private fun showError(e: Exception) {PhoneReceiver.status("Playback failed. Send another magnet or check the TV."); if(!isFinishing) AlertDialog.Builder(this).setTitle("Playback unavailable").setMessage(e.message ?: "Unable to play this torrent").setPositiveButton("Retry") {_,_->recreate()}.setNegativeButton("Close") {_,_->finish()}.setOnCancelListener {finish()}.show() }
 override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
  val p=player
  if(p!=null && event.action==KeyEvent.ACTION_DOWN) {
   when(event.keyCode) {
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {if(p.isPlaying) p.pause() else p.play(); return true}
    KeyEvent.KEYCODE_MEDIA_PLAY -> {p.play();return true}
    KeyEvent.KEYCODE_MEDIA_PAUSE -> {p.pause();return true}
    KeyEvent.KEYCODE_MEDIA_STOP -> {finish();return true}
    KeyEvent.KEYCODE_MEDIA_REWIND -> {p.seekTo(maxOf(0,p.currentPosition-10000));return true}
    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {p.seekTo(p.currentPosition+10000);return true}
    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> if(!view.isControllerFullyVisible) {p.seekTo(maxOf(0,p.currentPosition+if(event.keyCode==KeyEvent.KEYCODE_DPAD_LEFT) -10000 else 10000));return true}
   }
  }
  return super.onKeyDown(keyCode,event)
 }
 override fun onResume() {super.onResume();PhoneReceiver.bind(this)}
 override fun onPause() {PhoneReceiver.unbind(this);super.onPause()}
 override fun onStart() {super.onStart();if(::session.isInitialized) scope.launch(Dispatchers.IO) {session.resume()}}
 override fun onStop() {super.onStop();player?.pause();savePosition();if(::session.isInitialized) scope.launch(Dispatchers.IO) {session.pause()}}
 private fun savePosition() {
  player?.let {getSharedPreferences("history",MODE_PRIVATE).edit().putLong(historyKey,it.currentPosition).apply()}
 }
 override fun onDestroy() {if(isFinishing) PhoneReceiver.status("Ready for a magnet link");savePosition();player?.release();scope.cancel();Thread {session.close()}.start();super.onDestroy()}
}
