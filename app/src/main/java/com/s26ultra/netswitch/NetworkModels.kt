package com.s26ultra.netswitch

enum class NetworkModeTarget(val id: String, val shortLabel: String, val fullLabel: String) {
    MODE_5G("5G", "5G", "5G/LTE/3G/2G (Otomatik)"),
    MODE_4_5G("4.5G", "4.5G (LTE)", "LTE/3G/2G (Otomatik - 4.5G)");

    fun toggle(): NetworkModeTarget = if (this == MODE_5G) MODE_4_5G else MODE_5G

    companion object {
        fun fromId(id: String?): NetworkModeTarget {
            return entries.firstOrNull { it.id == id } ?: MODE_5G
        }
    }
}

enum class SimSelectionMode(val id: String, val chipLabel: String, val description: String) {
    AUTO_ACTIVE_DATA("AUTO", "OTO (Veri)", "Aktif mobil veri hangi SIM'deyse onu değiştirir"),
    SIM_1("SIM1", "SIM 1", "Her zaman SIM 1 (Hat 1) şebeke modunu değiştirir"),
    SIM_2("SIM2", "SIM 2", "Her zaman SIM 2 (Hat 2) şebeke modunu değiştirir");

    fun nextCycle(): SimSelectionMode = when (this) {
        AUTO_ACTIVE_DATA -> SIM_1
        SIM_1 -> SIM_2
        SIM_2 -> AUTO_ACTIVE_DATA
    }

    companion object {
        fun fromId(id: String?): SimSelectionMode {
            return entries.firstOrNull { it.id == id } ?: AUTO_ACTIVE_DATA
        }
    }
}

enum class AutomationStep {
    FIND_NETWORK_MODE,
    SELECT_MODE_OPTION,
    COMPLETED
}

data class SimSlotStatus(
    val slotIndex: Int, // 0 = SIM 1, 1 = SIM 2
    val subscriptionId: Int,
    val displayName: String,
    val carrierName: String,
    val isDefaultData: Boolean,
    val liveNetworkBadge: String? // "5G" veya "4.5G" (canlı okunabiliyorsa)
)

data class ResolvedSimTarget(
    val slotIndex: Int, // 0 = SIM 1, 1 = SIM 2
    val subscriptionId: Int,
    val displayTitle: String,
    val isFromAutoData: Boolean,
    val currentMode: NetworkModeTarget
)

data class PendingSwitchRequest(
    val targetMode: NetworkModeTarget,
    val targetSimSlot: Int, // 0 = SIM 1, 1 = SIM 2
    val targetSubId: Int,
    val createdAtMillis: Long,
    val step: AutomationStep
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis(), ttlMillis: Long = 8_500L): Boolean {
        return (nowMillis - createdAtMillis) > ttlMillis
    }
}
