package com.s26ultra.netswitch

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

class NetSwitchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateFullWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_SELECT_SIM_AUTO -> {
                SwitchPreferences.setSimSelectionMode(context, SimSelectionMode.AUTO_ACTIVE_DATA)
                updateAllWidgets(context)
            }
            ACTION_SELECT_SIM_1 -> {
                SwitchPreferences.setSimSelectionMode(context, SimSelectionMode.SIM_1)
                updateAllWidgets(context)
            }
            ACTION_SELECT_SIM_2 -> {
                SwitchPreferences.setSimSelectionMode(context, SimSelectionMode.SIM_2)
                updateAllWidgets(context)
            }
            ACTION_CYCLE_SIM -> {
                val next = SwitchPreferences.getSimSelectionMode(context).nextCycle()
                SwitchPreferences.setSimSelectionMode(context, next)
                updateAllWidgets(context)
            }
        }
    }

    companion object {
        const val ACTION_SWITCH_5G = "com.s26ultra.netswitch.ACTION_SWITCH_5G"
        const val ACTION_SWITCH_45G = "com.s26ultra.netswitch.ACTION_SWITCH_45G"
        const val ACTION_SWITCH_TOGGLE = "com.s26ultra.netswitch.ACTION_SWITCH_TOGGLE"
        const val ACTION_SELECT_SIM_AUTO = "com.s26ultra.netswitch.ACTION_SELECT_SIM_AUTO"
        const val ACTION_SELECT_SIM_1 = "com.s26ultra.netswitch.ACTION_SELECT_SIM_1"
        const val ACTION_SELECT_SIM_2 = "com.s26ultra.netswitch.ACTION_SELECT_SIM_2"
        const val ACTION_CYCLE_SIM = "com.s26ultra.netswitch.ACTION_CYCLE_SIM"
        const val ACTION_OPEN_NET_SETTINGS = "com.s26ultra.netswitch.ACTION_OPEN_NET_SETTINGS"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val fullIds = manager.getAppWidgetIds(
                ComponentName(context, NetSwitchWidgetProvider::class.java)
            )
            for (id in fullIds) {
                updateFullWidget(context, manager, id)
            }

            val compactIds = manager.getAppWidgetIds(
                ComponentName(context, NetSwitchCompactWidgetProvider::class.java)
            )
            for (id in compactIds) {
                NetSwitchCompactWidgetProvider.updateCompactWidget(context, manager, id)
            }
        }

        fun updateFullWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_net_switch_full)
            val simSelection = SwitchPreferences.getSimSelectionMode(context)
            val resolved = SimNetworkHelper.resolveTargetSim(context, simSelection)
            val is5G = resolved.currentMode == NetworkModeTarget.MODE_5G

            val prefix = if (resolved.isFromAutoData) "Aktif Veri: " else "Hedef: "
            views.setTextViewText(
                R.id.tv_widget_status,
                "$prefix${resolved.displayTitle} • ${resolved.currentMode.shortLabel}"
            )

            styleSimChip(
                views,
                R.id.btn_sim_auto,
                simSelection == SimSelectionMode.AUTO_ACTIVE_DATA
            )
            styleSimChip(
                views,
                R.id.btn_sim_1,
                simSelection == SimSelectionMode.SIM_1
            )
            styleSimChip(
                views,
                R.id.btn_sim_2,
                simSelection == SimSelectionMode.SIM_2
            )

            views.setInt(
                R.id.btn_switch_5g,
                "setBackgroundResource",
                if (is5G) R.drawable.btn_5g_active_bg else R.drawable.btn_inactive_bg
            )
            views.setTextColor(
                R.id.btn_switch_5g,
                if (is5G) Color.WHITE else Color.parseColor("#D1D5DB")
            )

            views.setInt(
                R.id.btn_switch_45g,
                "setBackgroundResource",
                if (!is5G) R.drawable.btn_45g_active_bg else R.drawable.btn_inactive_bg
            )
            views.setTextColor(
                R.id.btn_switch_45g,
                if (!is5G) Color.WHITE else Color.parseColor("#D1D5DB")
            )

            // SIM seçim sekmeleri (Broadcast ile anında güncellenir)
            views.setOnClickPendingIntent(
                R.id.btn_sim_auto,
                buildBroadcastPendingIntent(context, ACTION_SELECT_SIM_AUTO, 101)
            )
            views.setOnClickPendingIntent(
                R.id.btn_sim_1,
                buildBroadcastPendingIntent(context, ACTION_SELECT_SIM_1, 102)
            )
            views.setOnClickPendingIntent(
                R.id.btn_sim_2,
                buildBroadcastPendingIntent(context, ACTION_SELECT_SIM_2, 103)
            )

            // 5G / 4.5G Geçiş Butonları (Android 15/16 BAL engeline takılmamak için SwitchTriggerActivity başlatır)
            views.setOnClickPendingIntent(
                R.id.btn_switch_5g,
                buildTriggerActivityPendingIntent(context, ACTION_SWITCH_5G, 104)
            )
            views.setOnClickPendingIntent(
                R.id.btn_switch_45g,
                buildTriggerActivityPendingIntent(context, ACTION_SWITCH_45G, 105)
            )
            views.setOnClickPendingIntent(
                R.id.btn_switch_toggle,
                buildTriggerActivityPendingIntent(context, ACTION_SWITCH_TOGGLE, 106)
            )
            views.setOnClickPendingIntent(
                R.id.btn_open_settings,
                buildTriggerActivityPendingIntent(context, ACTION_OPEN_NET_SETTINGS, 107)
            )

            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPending = PendingIntent.getActivity(
                context,
                108,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.tv_widget_status, openAppPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun styleSimChip(views: RemoteViews, viewId: Int, active: Boolean) {
            views.setInt(
                viewId,
                "setBackgroundResource",
                if (active) R.drawable.chip_active_bg else R.drawable.chip_inactive_bg
            )
            views.setTextColor(
                viewId,
                if (active) Color.WHITE else Color.parseColor("#9CA3AF")
            )
        }

        fun buildBroadcastPendingIntent(
            context: Context,
            action: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, NetSwitchWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun buildTriggerActivityPendingIntent(
            context: Context,
            action: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, SwitchTriggerActivity::class.java).apply {
                this.action = action
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
