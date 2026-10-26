package com.santosxd.diario.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import android.widget.Toast
import com.santosxd.diario.R
import com.santosxd.diario.data.DiaryDb
import com.santosxd.diario.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Widget con cinco botones de ánimo. Un toque guarda una entrada sin abrir la app. */
class QuickWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            manager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {
        private val BUTTONS = intArrayOf(
            R.id.mood0, R.id.mood1, R.id.mood2, R.id.mood3, R.id.mood4
        )

        fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_quick)
            for (i in BUTTONS.indices) {
                val intent = Intent(context, QuickLogReceiver::class.java)
                    .putExtra("mood", i)
                val pending = PendingIntent.getBroadcast(
                    context,
                    i,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(BUTTONS[i], pending)
            }
            return views
        }
    }
}

class QuickLogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mood = intent.getIntExtra("mood", -1)
        if (mood !in 0..4) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            var ok = false
            try {
                Repo(DiaryDb.get(app)).saveEntry(mood, "", emptySet())
                ok = true
            } catch (e: Exception) {
                ok = false
            } finally {
                pending.finish()
            }
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    app,
                    if (ok) "Guardado" else "No se pudo guardar",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
