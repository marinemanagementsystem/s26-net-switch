package com.s26ultra.netswitch

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.core.content.ContextCompat

object SimNetworkHelper {

    fun hasPhonePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(context, NetSwitchAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices
            .split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it == expectedComponent }
    }

    @SuppressLint("MissingPermission")
    fun getSimStatuses(context: Context): List<SimSlotStatus> {
        val defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
        if (!hasPhonePermission(context)) {
            return listOf(
                SimSlotStatus(
                    slotIndex = 0,
                    subscriptionId = -1,
                    displayName = "SIM 1",
                    carrierName = "İzin bekleniyor",
                    isDefaultData = true,
                    liveNetworkBadge = null
                ),
                SimSlotStatus(
                    slotIndex = 1,
                    subscriptionId = -1,
                    displayName = "SIM 2",
                    carrierName = "İzin bekleniyor",
                    isDefaultData = false,
                    liveNetworkBadge = null
                )
            )
        }

        val subManager = context.getSystemService(SubscriptionManager::class.java)
        val activeList = runCatching { subManager?.activeSubscriptionInfoList.orEmpty() }
            .getOrDefault(emptyList())

        if (activeList.isEmpty()) {
            return listOf(
                SimSlotStatus(
                    slotIndex = 0,
                    subscriptionId = defaultDataSubId,
                    displayName = "SIM 1",
                    carrierName = "Aktif Hat",
                    isDefaultData = true,
                    liveNetworkBadge = detectLiveNetworkBadge(context, defaultDataSubId)
                ),
                SimSlotStatus(
                    slotIndex = 1,
                    subscriptionId = -1,
                    displayName = "SIM 2",
                    carrierName = "Algılanmadı",
                    isDefaultData = false,
                    liveNetworkBadge = null
                )
            )
        }

        return (0..1).map { slot ->
            val info = activeList.firstOrNull { it.simSlotIndex == slot }
            if (info != null) {
                val subId = info.subscriptionId
                val isData = (subId == defaultDataSubId) ||
                    (defaultDataSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID && slot == 0)
                val liveBadge = detectLiveNetworkBadge(context, subId)
                SimSlotStatus(
                    slotIndex = slot,
                    subscriptionId = subId,
                    displayName = info.displayName?.toString()?.takeIf { it.isNotBlank() }
                        ?: "SIM ${slot + 1}",
                    carrierName = info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                        ?: "SIM ${slot + 1}",
                    isDefaultData = isData,
                    liveNetworkBadge = liveBadge
                )
            } else {
                SimSlotStatus(
                    slotIndex = slot,
                    subscriptionId = -1,
                    displayName = "SIM ${slot + 1}",
                    carrierName = "Takılı değil",
                    isDefaultData = false,
                    liveNetworkBadge = null
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun detectLiveNetworkBadge(context: Context, subId: Int): String? {
        if (!hasPhonePermission(context)) return null
        return runCatching {
            val baseTm = context.getSystemService(TelephonyManager::class.java) ?: return null
            val tm = if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && subId > 0) {
                baseTm.createForSubscriptionId(subId)
            } else {
                baseTm
            }
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "4.5G"
                else -> null
            }
        }.getOrNull()
    }

    fun resolveTargetSim(
        context: Context,
        overrideSelection: SimSelectionMode? = null
    ): ResolvedSimTarget {
        val selection = overrideSelection ?: SwitchPreferences.getSimSelectionMode(context)
        val sims = getSimStatuses(context)

        val chosenSim = when (selection) {
            SimSelectionMode.AUTO_ACTIVE_DATA -> {
                sims.firstOrNull { it.isDefaultData } ?: sims.first()
            }
            SimSelectionMode.SIM_1 -> {
                sims.firstOrNull { it.slotIndex == 0 } ?: sims.first()
            }
            SimSelectionMode.SIM_2 -> {
                sims.firstOrNull { it.slotIndex == 1 } ?: sims.last()
            }
        }

        val currentSaved = SwitchPreferences.getSavedModeForSlot(context, chosenSim.slotIndex)
        val simLabel = if (chosenSim.carrierName.isNotBlank() &&
            !chosenSim.carrierName.contains("İzin", ignoreCase = true) &&
            !chosenSim.carrierName.contains("Takılı", ignoreCase = true) &&
            !chosenSim.carrierName.equals("SIM ${chosenSim.slotIndex + 1}", ignoreCase = true)
        ) {
            "SIM ${chosenSim.slotIndex + 1} (${chosenSim.carrierName})"
        } else {
            "SIM ${chosenSim.slotIndex + 1}"
        }

        return ResolvedSimTarget(
            slotIndex = chosenSim.slotIndex,
            subscriptionId = chosenSim.subscriptionId,
            displayTitle = simLabel,
            isFromAutoData = selection == SimSelectionMode.AUTO_ACTIVE_DATA,
            currentMode = currentSaved
        )
    }

    /**
     * Widget veya uygulama içinden 5G / 4.5G geçişini başlatır.
     * @param explicitTargetMode null ise mevcut modun tersine (5G <-> 4.5G) geçiş yapar.
     */
    fun triggerNetworkSwitch(
        context: Context,
        explicitTargetMode: NetworkModeTarget? = null
    ) {
        val resolved = resolveTargetSim(context)
        val targetMode = explicitTargetMode ?: resolved.currentMode.toggle()
        val accessibilityReady = isAccessibilityServiceEnabled(context)

        if (accessibilityReady) {
            SwitchPreferences.setPendingRequest(
                context = context,
                targetMode = targetMode,
                targetSimSlot = resolved.slotIndex,
                targetSubId = resolved.subscriptionId
            )
            SwitchPreferences.recordLog(
                context,
                "${resolved.displayTitle} -> ${targetMode.shortLabel} otomatik geçiş başlatıldı"
            )
        } else {
            // Erişilebilirlik kapalıysa bile kullanıcının hedeflediği modu widget'ta güncelle ve ayar ekranını aç
            SwitchPreferences.setSavedModeForSlot(context, resolved.slotIndex, targetMode)
            SwitchPreferences.recordLog(
                context,
                "${resolved.displayTitle} -> ${targetMode.shortLabel} için ayar ekranı açıldı (Manuel mod)"
            )
            Toast.makeText(
                context,
                "${resolved.displayTitle}: ${targetMode.shortLabel} seçimi için Şebeke Modu açılıyor...",
                Toast.LENGTH_SHORT
            ).show()
        }

        NetSwitchWidgetProvider.updateAllWidgets(context)
        launchMobileNetworkSettings(
            context = context,
            subId = resolved.subscriptionId,
            slotIndex = resolved.slotIndex
        )
    }

    /**
     * Samsung S26 Ultra (One UI) veya standart Android Mobil Ağlar ekranını
     * hedef SIM (subId / slotIndex) parametreleriyle açar.
     */
    fun launchMobileNetworkSettings(
        context: Context,
        subId: Int = -1,
        slotIndex: Int = 0
    ) {
        val candidateIntents = listOf(
            // 1. Samsung One UI doğrudan Mobil Ağlar (NetSettingsActivity)
            Intent().apply {
                component = ComponentName(
                    "com.samsung.android.app.telephonyui",
                    "com.samsung.android.app.telephonyui.netsettings.ui.NetSettingsActivity"
                )
            },
            // 2. Standart Android Mobil Ağ / Operatör Ayarları
            Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
            // 3. Yedek: Veri Dolaşımı / Mobil Ağ Ayarları
            Intent(Settings.ACTION_DATA_ROAMING_SETTINGS),
            // 4. Son yedek: Kablosuz ve Ağ Ayarları
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        )

        for (intent in candidateIntents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (subId > 0) {
                intent.putExtra(Settings.EXTRA_SUB_ID, subId)
                intent.putExtra("sub_id", subId)
                intent.putExtra("subscription", subId)
                intent.putExtra("android.telephony.extra.SUBSCRIPTION_INDEX", subId)
            }
            intent.putExtra("sim_slot", slotIndex)
            intent.putExtra("slot_id", slotIndex)
            intent.putExtra("simSlot", slotIndex)

            val launched = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)

            if (launched) return
        }
    }

    /**
     * Samsung SIM Yöneticisi (SIM Manager) ekranını açar.
     */
    fun launchSimManagerSettings(context: Context) {
        val candidates = listOf(
            Intent("com.samsung.settings.SIMCARD_MGR"),
            Intent().apply {
                component = ComponentName(
                    "com.android.settings",
                    "com.samsung.android.settings.connection.simcardmanager.SimcardManagerActivity"
                )
            },
            Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) return
        }
    }

    /**
     * Android Gizli Radyo Bilgisi (*#*#4636#*#* / RadioInfo) ekranını açmayı dener.
     */
    fun launchRadioInfoScreen(context: Context) {
        val candidates = listOf(
            Intent().apply {
                component = ComponentName(
                    "com.android.phone",
                    "com.android.phone.settings.RadioInfo"
                )
            },
            Intent().apply {
                component = ComponentName(
                    "com.android.settings",
                    "com.android.settings.TestingSettings"
                )
            }
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) return
        }
        Toast.makeText(
            context,
            "Samsung One UI bu cihazda gizli RadioInfo ekranını kısıtlıyor; Mobil Ağlar menüsü açılıyor.",
            Toast.LENGTH_SHORT
        ).show()
        launchMobileNetworkSettings(context)
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
