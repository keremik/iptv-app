package com.ipflix.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log

// „Beim Einschalten starten“: öffnet IPFLIX nach dem Hochfahren des Sticks und wenn er aus dem
// Standby aufwacht (Fernseher an). Seit Android 10 darf eine App sich nur aus dem Hintergrund öffnen,
// wenn sie „Über anderen Apps einblenden“ darf; ohne diese Erlaubnis passiert nichts.
object AutoStart {
  private const val PREFS = "ipflix"
  private const val KEY = "autostart"

  fun enabled(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

  fun set(c: Context, on: Boolean) {
    c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    if (on) startWatcher(c) else c.stopService(Intent(c, WakeService::class.java))
  }

  fun allowed(c: Context) = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c)

  fun startWatcher(c: Context) {
    if (!enabled(c)) return
    val i = Intent(c, WakeService::class.java)
    try {
      if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
    } catch (e: Exception) { Log.w("IPFLIX", "autostart watcher: $e") }
  }

  fun launch(c: Context) {
    if (!enabled(c)) return
    try {
      c.startActivity(Intent(c, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    } catch (e: Exception) { Log.w("IPFLIX", "autostart launch: $e") }
  }
}

class BootReceiver : BroadcastReceiver() {
  override fun onReceive(c: Context, i: Intent) {
    if (!AutoStart.enabled(c)) return
    AutoStart.startWatcher(c)
    AutoStart.launch(c)
  }
}

// Kleiner Dienst ohne eigene Arbeit: er hört nur, wann der Bildschirm wieder angeht.
class WakeService : Service() {
  private val screenOn = object : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { AutoStart.launch(c) }
  }
  private var registered = false

  override fun onBind(i: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    goForeground()
    registerReceiver(screenOn, IntentFilter(Intent.ACTION_SCREEN_ON))
    registered = true
  }

  override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int {
    goForeground()
    if (!AutoStart.enabled(this)) { stopSelf(); return START_NOT_STICKY }
    return START_STICKY
  }

  private fun goForeground() {
    val n: Notification = if (Build.VERSION.SDK_INT >= 26) {
      val nm = getSystemService(NotificationManager::class.java)
      nm.createNotificationChannel(NotificationChannel("autostart", "Beim Einschalten starten", NotificationManager.IMPORTANCE_MIN))
      Notification.Builder(this, "autostart")
    } else {
      @Suppress("DEPRECATION") Notification.Builder(this).setPriority(Notification.PRIORITY_MIN)
    }.setSmallIcon(R.mipmap.ic_launcher).setContentTitle("IPFLIX startet beim Einschalten").build()
    try {
      if (Build.VERSION.SDK_INT >= 34) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
      else startForeground(7, n)
    } catch (e: Exception) { Log.w("IPFLIX", "autostart foreground: $e") }
  }

  override fun onDestroy() {
    if (registered) try { unregisterReceiver(screenOn) } catch (e: Exception) {}
    super.onDestroy()
  }
}
