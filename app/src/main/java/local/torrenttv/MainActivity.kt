// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Torrent TV contributors

package local.torrenttv
import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay

class MainActivity: ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  PhoneReceiver.ensure(this)
  setContent {MaterialTheme(colorScheme=darkColorScheme(background=Color(0xFF0B1422),surface=Color(0xFF0B1422))) {
   val receiver by PhoneReceiver.state.collectAsState()
   LaunchedEffect(Unit) {while(true) {PhoneReceiver.refreshAddresses();delay(5000)}}
   val url=receiver.addresses.firstOrNull()?.let {PhoneReceiver.url(it)}
   val qr=remember(url) {url?.let {value ->
    val matrix=QRCodeWriter().encode(value,BarcodeFormat.QR_CODE,384,384)
    Bitmap.createBitmap(384,384,Bitmap.Config.ARGB_8888).apply {
     val pixels=IntArray(384*384) {i -> if(matrix[i%384,i/384]) android.graphics.Color.BLACK else android.graphics.Color.WHITE}
     setPixels(pixels,0,384,0,0,384,384)
    }.asImageBitmap()
   }}
   Surface(Modifier.fillMaxSize()) {Row(Modifier.padding(40.dp),horizontalArrangement=Arrangement.spacedBy(36.dp)) {
    qr?.let {Image(it,contentDescription="Scan to open the phone magnet page",modifier=Modifier.size(280.dp))}
    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(20.dp)) {
     Text("Torrent TV",style=MaterialTheme.typography.headlineLarge)
     Text("Scan. Paste. Play.",style=MaterialTheme.typography.headlineMedium,color=Color(0xFF5EEAD4))
     Text("Scan the QR code with your phone, paste a magnet link, and tap Go.",style=MaterialTheme.typography.titleLarge)
     if(url==null) Text("Connect the TV to Wi-Fi or Ethernet to receive links.",color=Color(0xFFFFD54F))
     else Text(url,style=MaterialTheme.typography.bodyLarge)
     Text("Your phone and TV must be on the same local network. Keep this app open.")
     HorizontalDivider()
     Text(receiver.status,style=MaterialTheme.typography.titleLarge)
    }
   }}
  }}
 }
 override fun onResume() {super.onResume();PhoneReceiver.ensure(this);PhoneReceiver.bind(this)}
 override fun onPause() {PhoneReceiver.unbind(this);super.onPause()}
}
