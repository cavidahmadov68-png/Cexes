package com.cexes.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

object Pending {
  val map = ConcurrentHashMap<Int, Pair<Notification.Action, String>>()
}

fun sendReply(ctx: Context, action: Notification.Action, text: String) {
  val inputs = action.remoteInputs ?: return
  val b = Bundle()
  inputs.forEach { b.putCharSequence(it.resultKey, text) }
  val i = Intent()
  RemoteInput.addResultsToIntent(inputs, i, b)
  action.actionIntent.send(ctx, 0, i)
}

class SendReceiver : android.content.BroadcastReceiver() {
  override fun onReceive(c: Context, i: Intent) {
    val id = i.getIntExtra("id", -1)
    Pending.map.remove(id)?.let { (a, t) -> sendReply(c, a, t) }
    c.getSystemService(NotificationManager::class.java).cancel(id)
  }
}

class WaListener : NotificationListenerService() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val seen = ConcurrentHashMap<String, String>()

  override fun onNotificationPosted(sbn: StatusBarNotification) {
    if (sbn.packageName != "com.whatsapp") return
    val mode = Prefs.mode(this)
    if (mode == Prefs.OFF || !LocalLlm.isReady(this)) return
    val n = sbn.notification
    if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
    val ex = n.extras
    val sender = ex.getCharSequence("android.title")?.toString() ?: return
    val text = ex.getCharSequence("android.text")?.toString() ?: return
    val action = n.actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true } ?: return
    if (seen[sbn.key] == text) return
    seen[sbn.key] = text

    scope.launch {
      val prompt = "Sen kullanıcı adına WhatsApp'ta kısa, samimi ve kibar cevap yazan asistansın. " +
        "Sadece cevabı yaz, açıklama ekleme.\nGelen mesaj ($sender): $text\nCevap:"
      val answer = try { LocalLlm.generate(this@WaListener, prompt) } catch (e: Exception) { return@launch }
      if (answer.isBlank()) return@launch
      if (mode == Prefs.AUTO) sendReply(this@WaListener, action, answer)
      else showDraft(sender, text, answer, action)
    }
  }

  private fun showDraft(sender: String, msg: String, answer: String, action: Notification.Action) {
    val nm = getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel("draft", "Taslak cevaplar", NotificationManager.IMPORTANCE_HIGH))
    val id = (sender + msg).hashCode()
    Pending.map[id] = action to answer
    val pi = PendingIntent.getBroadcast(
      this, id, Intent(this, SendReceiver::class.java).putExtra("id", id),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val notif = Notification.Builder(this, "draft")
      .setSmallIcon(android.R.drawable.ic_dialog_email)
      .setContentTitle("$sender: $msg")
      .setContentText("Önerilen cevap: $answer")
      .setStyle(Notification.BigTextStyle().bigText("Önerilen cevap:\n$answer"))
      .addAction(Notification.Action.Builder(null, "Gönder", pi).build())
      .setAutoCancel(true)
      .build()
    nm.notify(id, notif)
  }

  override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
