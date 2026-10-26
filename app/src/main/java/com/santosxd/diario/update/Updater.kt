package com.santosxd.diario.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.santosxd.diario.BuildConfig
import com.santosxd.diario.security.Prefs
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Busca versiones nuevas en las "Releases" públicas del repositorio.
 * Solo hace una petición GET; no envía nada del diario ni datos personales.
 */
object Updater {
    private const val REPO = "SantosXD1945/Mi-diario-secreto"
    private const val CHANNEL = "actualizaciones"
    private const val WORK_NAME = "buscar-actualizaciones"

    data class Release(val code: Int, val name: String, val apkUrl: String)

    /** Llamar desde un hilo de fondo. Devuelve null si no hay red o no hay versión publicada. */
    fun fetchLatest(): Release? {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest")
            .openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "MiDiarioSecreto")
            if (conn.responseCode != 200) {
                null
            } else {
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val tag = json.getString("tag_name")
                val code = tag.removePrefix("v").toIntOrNull()
                val assets = json.getJSONArray("assets")
                var url: String? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) {
                        url = a.getString("browser_download_url")
                        break
                    }
                }
                if (code == null || url == null) null
                else Release(code, json.optString("name", tag), url)
            }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    fun isNewer(r: Release): Boolean = r.code > BuildConfig.VERSION_CODE

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            wm.cancelUniqueWork(WORK_NAME)
        }
    }

    fun notifyUpdate(context: Context, r: Release) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Actualizaciones", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(Intent.ACTION_VIEW, Uri.parse(r.apkUrl)),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Mi diario secreto")
            .setContentText("Hay una versión nueva. Toca para descargarla.")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(2, notification)
        } catch (e: SecurityException) {
            // Sin permiso de notificaciones.
        }
    }
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (!prefs.updateChecks) return Result.success()
        val r = withContext(Dispatchers.IO) { Updater.fetchLatest() } ?: return Result.success()
        if (Updater.isNewer(r) && prefs.lastNotifiedUpdate < r.code) {
            Updater.notifyUpdate(applicationContext, r)
            prefs.lastNotifiedUpdate = r.code
        }
        return Result.success()
    }
}
