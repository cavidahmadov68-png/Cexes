package com.cexes.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    setContent { MaterialTheme { App() } }
  }
}

@Composable
fun App() {
  var tab by remember { mutableIntStateOf(0) }
  val names = listOf("Sohbet", "Ayarlar", "Hakkında")
  Scaffold(
    bottomBar = {
      NavigationBar {
        names.forEachIndexed { i, n ->
          NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {}, label = { Text(n) })
        }
      }
    }
  ) { pad ->
    Box(Modifier.padding(pad).fillMaxSize()) {
      when (tab) { 0 -> ChatScreen(); 1 -> SettingsScreen(); else -> AboutScreen() }
    }
  }
}

@Composable
fun ChatScreen() {
  val ctx = LocalContext.current
  val scope = rememberCoroutineScope()
  var input by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  val msgs = remember { mutableStateListOf<Pair<Boolean, String>>() }
  Column(Modifier.fillMaxSize().padding(12.dp)) {
    if (!LocalLlm.isReady(ctx)) Text("Önce Ayarlar'dan model dosyasını (.task) yükle.")
    LazyColumn(Modifier.weight(1f)) {
      items(msgs) { (me, t) ->
        Text(
          (if (me) "Sen: " else "Cexes: ") + t,
          Modifier.padding(vertical = 4.dp)
        )
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Mesaj yaz") })
      Spacer(Modifier.width(8.dp))
      Button(enabled = !busy && input.isNotBlank() && LocalLlm.isReady(ctx), onClick = {
        val q = input; input = ""; msgs.add(true to q); busy = true
        scope.launch {
          val a = withContext(Dispatchers.Default) {
            try { LocalLlm.generate(ctx, q) } catch (e: Exception) { "Hata: ${e.message}" }
          }
          msgs.add(false to a); busy = false
        }
      }) { Text(if (busy) "..." else "Gönder") }
    }
  }
}

@Composable
fun SettingsScreen() {
  val ctx = LocalContext.current
  val scope = rememberCoroutineScope()
  var mode by remember { mutableIntStateOf(Prefs.mode(ctx)) }
  var status by remember { mutableStateOf(if (LocalLlm.isReady(ctx)) "Model yüklü" else "Model yok") }
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
    if (uri != null) {
      status = "Kopyalanıyor..."
      scope.launch {
        withContext(Dispatchers.IO) {
          LocalLlm.reset()
          ctx.contentResolver.openInputStream(uri)?.use { i ->
            LocalLlm.modelFile(ctx).outputStream().use { o -> i.copyTo(o) }
          }
        }
        status = "Model yüklendi"
      }
    }
  }
  Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("WhatsApp otomatik cevap", style = MaterialTheme.typography.titleMedium)
    listOf("Kapalı", "Taslak (ben onaylarım)", "Otomatik").forEachIndexed { i, n ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = mode == i, onClick = { mode = i; Prefs.setMode(ctx, i) })
        Text(n)
      }
    }
    Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
      Text("Bildirim erişimi ver")
    }
    Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Model dosyası seç (.task)") }
    Text(status)
  }
}

@Composable
fun AboutScreen() {
  Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Cexes", style = MaterialTheme.typography.headlineLarge)
    Text("Kurucu: Cavid Ahmadov")
    Text("Tamamen cihaz üzerinde çalışır. Mesajların telefondan çıkmaz, internet gerekmez.")
  }
}
