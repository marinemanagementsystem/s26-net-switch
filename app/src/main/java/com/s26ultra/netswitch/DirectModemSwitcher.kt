package com.s26ultra.netswitch

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Shizuku (Kablosuz ADB yetkisi) aktif olduğunda telefon ayarlarını HİÇ AÇMADAN
 * doğrudan ana ekran üzerinden arka planda 5G <-> 4.5G (LTE) değiştiren sınıf.
 */
object DirectModemSwitcher {

    // TelephonyManager.NETWORK_TYPE_BITMASK_NR (1 << 19)
    private const val BITMASK_NR_5G = 524288L
    // Standart LTE + 3G + 2G bitmask (GSM | GPRS | EDGE | UMTS | HSDPA | HSUPA | HSPA | LTE | HSPAP | LTE_CA)
    private const val BITMASK_LTE_3G_2G_DEFAULT = 345471L
    // Standart 5G + LTE + 3G + 2G bitmask
    private const val BITMASK_5G_LTE_3G_2G_DEFAULT = BITMASK_NR_5G or BITMASK_LTE_3G_2G_DEFAULT

    // REASON_USER = 0
    private const val ALLOWED_NETWORK_TYPES_REASON_USER = 0

    // RIL / Settings.Global network mode sabitleri (26 = NR/LTE/WCDMA/GSM, 9 = LTE/WCDMA/GSM)
    private const val NETWORK_MODE_NR_LTE_TDSCDMA_GSM_WCDMA = 26
    private const val NETWORK_MODE_LTE_GSM_WCDMA = 9

    fun isShizukuAvailable(): Boolean {
        return runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    }

    fun hasShizukuPermission(): Boolean {
        if (!isShizukuAvailable()) return false
        return runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    fun requestShizukuPermission(requestCode: Int = 1001) {
        if (!isShizukuAvailable()) return
        runCatching {
            Shizuku.requestPermission(requestCode)
        }
    }

    /**
     * Ayarlar ekranını açmadan doğrudan ITelephony ve Settings.Global üzerinden
     * hedef SIM'in (subId) 5G / 4.5G modunu değiştirir.
     */
    fun switchSilentlyInBackground(
        context: Context,
        subId: Int,
        slotIndex: Int,
        targetMode: NetworkModeTarget
    ): Boolean {
        if (!hasShizukuPermission()) return false

        val validSubId = if (subId > 0) subId else getDefaultSubIdFallback()
        var binderSuccess = false

        // 1. Yöntem: ITelephony Binder (ShizukuBinderWrapper) üzerinden setAllowedNetworkTypesForReason
        runCatching {
            val rawBinder: IBinder = SystemServiceHelper.getSystemService("phone") ?: return@runCatching
            val wrappedBinder = ShizukuBinderWrapper(rawBinder)
            val iTelephonyStubClass = Class.forName("com.android.internal.telephony.ITelephony\$Stub")
            val asInterfaceMethod = iTelephonyStubClass.getMethod("asInterface", IBinder::class.java)
            val iTelephony = asInterfaceMethod.invoke(null, wrappedBinder)

            val getAllowedMethod = iTelephony.javaClass.methods.firstOrNull {
                it.name == "getAllowedNetworkTypesForReason" && it.parameterTypes.size == 2
            }
            val setAllowedMethod = iTelephony.javaClass.methods.firstOrNull {
                it.name == "setAllowedNetworkTypesForReason" && it.parameterTypes.size == 3
            }

            if (setAllowedMethod != null) {
                val currentMask = runCatching {
                    (getAllowedMethod?.invoke(
                        iTelephony,
                        validSubId,
                        ALLOWED_NETWORK_TYPES_REASON_USER
                    ) as? Long)?.takeIf { it > 0L }
                }.getOrNull() ?: BITMASK_5G_LTE_3G_2G_DEFAULT

                val newMask = when (targetMode) {
                    NetworkModeTarget.MODE_5G -> currentMask or BITMASK_NR_5G
                    NetworkModeTarget.MODE_4_5G -> (currentMask and BITMASK_NR_5G.inv()).let {
                        if (it == 0L) BITMASK_LTE_3G_2G_DEFAULT else it
                    }
                }

                setAllowedMethod.invoke(
                    iTelephony,
                    validSubId,
                    ALLOWED_NETWORK_TYPES_REASON_USER,
                    newMask
                )
                binderSuccess = true
            }

            // Ek olarak setPreferredNetworkType varsa onu da tetikle
            val setPrefMethod = iTelephony.javaClass.methods.firstOrNull {
                it.name == "setPreferredNetworkType" && it.parameterTypes.size == 2
            }
            if (setPrefMethod != null) {
                val netType = if (targetMode == NetworkModeTarget.MODE_5G) {
                    NETWORK_MODE_NR_LTE_TDSCDMA_GSM_WCDMA
                } else {
                    NETWORK_MODE_LTE_GSM_WCDMA
                }
                val res = setPrefMethod.invoke(iTelephony, validSubId, netType) as? Boolean ?: false
                if (res) binderSuccess = true
            }
        }

        // 2. Yöntem: Samsung Settings.Global preferred_network_mode değerlerini de senkronize et
        runCatching {
            val modeInt = if (targetMode == NetworkModeTarget.MODE_5G) {
                NETWORK_MODE_NR_LTE_TDSCDMA_GSM_WCDMA
            } else {
                NETWORK_MODE_LTE_GSM_WCDMA
            }
            val slotKey = if (slotIndex == 1) "preferred_network_mode2" else "preferred_network_mode1"
            val cmd = buildString {
                append("settings put global preferred_network_mode$validSubId $modeInt; ")
                append("settings put global $slotKey $modeInt; ")
                append("settings put global preferred_network_mode $modeInt")
            }
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
            val proc = newProcessMethod.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            proc.waitFor()
        }

        return binderSuccess
    }

    private fun getDefaultSubIdFallback(): Int {
        return runCatching {
            android.telephony.SubscriptionManager.getDefaultDataSubscriptionId()
                .takeIf { it > 0 } ?: 1
        }.getOrDefault(1)
    }
}
