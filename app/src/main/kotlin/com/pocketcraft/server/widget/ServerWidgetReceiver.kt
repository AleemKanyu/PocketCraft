package com.pocketcraft.server.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ServerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ServerWidget()

    companion object {
        const val ACTION_TRIGGER_WIDGET_UPDATE = "com.pocketcraft.server.widget.ACTION_TRIGGER_WIDGET_UPDATE"
    }

    override fun onReceive(context: Context, intent: android.content.Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TRIGGER_WIDGET_UPDATE) {
            val pendingResult = goAsync()
            val statusOverride = intent.getStringExtra("status_override")
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                try {
                    ServerWidgetUpdater.push(context.applicationContext, statusOverride)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                ServerWidgetUpdater.push(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                ServerWidgetUpdater.push(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
