package com.s26ultra.netswitch

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SwitchPreferences {
    private const val PREFS_NAME = "s26_net_switch_prefs"

    private const val KEY_SIM_SELECTION = "sim_selection_mode"
    private const val KEY_SIM0_MODE = "sim0_mode"
    private const val KEY_SIM1_MODE = "sim1_mode"
    private const val KEY_AUTO_RETURN_HOME = "auto_return_home"
    private const val KEY_LAST_LOG = "last_action_log"

    private const val KEY_PENDING_ACTIVE = "pending_active"
    private const val KEY_PENDING_TARGET_MODE = "pending_target_mode"
    private const val KEY_PENDING_SIM_SLOT = "pending_sim_slot"
    private const val KEY_PENDING_SUB_ID = "pending_sub_id"
    private const val KEY_PENDING_CREATED_AT = "pending_created_at"
    private const val KEY_PENDING_STEP = "pending_step"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSimSelectionMode(context: Context): SimSelectionMode {
        val raw = prefs(context).getString(KEY_SIM_SELECTION, SimSelectionMode.AUTO_ACTIVE_DATA.id)
        return SimSelectionMode.fromId(raw)
    }

    fun setSimSelectionMode(context: Context, mode: SimSelectionMode) {
        prefs(context).edit().putString(KEY_SIM_SELECTION, mode.id).apply()
    }

    fun getSavedModeForSlot(context: Context, slotIndex: Int): NetworkModeTarget {
        val key = if (slotIndex == 1) KEY_SIM1_MODE else KEY_SIM0_MODE
        val raw = prefs(context).getString(key, NetworkModeTarget.MODE_5G.id)
        return NetworkModeTarget.fromId(raw)
    }

    fun setSavedModeForSlot(context: Context, slotIndex: Int, mode: NetworkModeTarget) {
        val key = if (slotIndex == 1) KEY_SIM1_MODE else KEY_SIM0_MODE
        prefs(context).edit().putString(key, mode.id).apply()
    }

    fun isAutoReturnHomeEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AUTO_RETURN_HOME, true)
    }

    fun setAutoReturnHomeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_RETURN_HOME, enabled).apply()
    }

    fun getLastLog(context: Context): String {
        return prefs(context).getString(KEY_LAST_LOG, "Henüz geçiş yapılmadı") ?: "Henüz geçiş yapılmadı"
    }

    fun recordLog(context: Context, message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        prefs(context).edit().putString(KEY_LAST_LOG, "[$time] $message").apply()
    }

    fun setPendingRequest(
        context: Context,
        targetMode: NetworkModeTarget,
        targetSimSlot: Int,
        targetSubId: Int
    ) {
        prefs(context).edit()
            .putBoolean(KEY_PENDING_ACTIVE, true)
            .putString(KEY_PENDING_TARGET_MODE, targetMode.id)
            .putInt(KEY_PENDING_SIM_SLOT, targetSimSlot)
            .putInt(KEY_PENDING_SUB_ID, targetSubId)
            .putLong(KEY_PENDING_CREATED_AT, System.currentTimeMillis())
            .putString(KEY_PENDING_STEP, AutomationStep.FIND_NETWORK_MODE.name)
            .apply()
    }

    fun updatePendingStep(context: Context, step: AutomationStep) {
        prefs(context).edit().putString(KEY_PENDING_STEP, step.name).apply()
    }

    fun clearPendingRequest(context: Context) {
        prefs(context).edit().putBoolean(KEY_PENDING_ACTIVE, false).apply()
    }

    fun getActivePendingRequest(context: Context): PendingSwitchRequest? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_PENDING_ACTIVE, false)) return null

        val req = PendingSwitchRequest(
            targetMode = NetworkModeTarget.fromId(p.getString(KEY_PENDING_TARGET_MODE, null)),
            targetSimSlot = p.getInt(KEY_PENDING_SIM_SLOT, 0),
            targetSubId = p.getInt(KEY_PENDING_SUB_ID, -1),
            createdAtMillis = p.getLong(KEY_PENDING_CREATED_AT, 0L),
            step = runCatching {
                AutomationStep.valueOf(
                    p.getString(KEY_PENDING_STEP, AutomationStep.FIND_NETWORK_MODE.name)!!
                )
            }.getOrDefault(AutomationStep.FIND_NETWORK_MODE)
        )

        if (req.isExpired()) {
            clearPendingRequest(context)
            return null
        }
        return req
    }
}
