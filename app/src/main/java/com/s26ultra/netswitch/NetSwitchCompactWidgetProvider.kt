package com.s26ultra.netswitch

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

class NetSwitchCompactWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateCompactWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateCompactWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_net_switch_compact)
            val simSelection = SwitchPreferences.getSimSelectionMode(context)
            val resolved = SimNetworkHelper.resolveTargetSim(context, simSelection)
            val is5G = resolved.currentMode == NetworkModeTarget.MODE_5G

            views.setTextViewText(R.id.tv_compact_sim_mode, simSelection.chipLabel)
            views.setTextViewText(R.id.tv_compact_sim_active, "SIM ${resolved.slotIndex + 1}")

            views.setInt(
                R.id.btn_compact_toggle,
                "setBackgroundResource",
                if (is5G) R.drawable.btn_5g_active_bg else R.drawable.btn_45g_active_bg
            )
            views.setTextViewText(
                R.id.tv_compact_net_badge,
                if (is5G) "5G AKTİF" else "4.5G AKTİF"
            )
            views.setTextViewText(
                R.id.tv_compact_net_sub,
                if (is5G) "⇄ 4.5G'ye Geç" else "⇄ 5G'ye Geç"
            )

            views.setOnClickPendingIntent(
                R.id.btn_compact_cycle_sim,
                NetSwitchWidgetProvider.buildBroadcastPendingIntent(
                    context,
                    NetSwitchWidgetProvider.ACTION_CYCLE_SIM,
                    201
                )
            )
            views.setOnClickPendingIntent(
                R.id.btn_compact_toggle,
                NetSwitchWidgetProvider.buildTriggerActivityPendingIntent(
                    context,
                    NetSwitchWidgetProvider.ACTION_SWITCH_TOGGLE,
                    202
                )
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
