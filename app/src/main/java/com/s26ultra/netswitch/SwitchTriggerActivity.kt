package com.s26ultra.netswitch

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import rikka.shizuku.Shizuku

/**
 * Ana ekrandaki Widget butonlarına basıldığında Android 15/16 Background Activity Launch (BAL)
 * engeline takılmadan geçişi anında çalıştıran görünmez (Translucent) köprü Activity.
 */
class SwitchTriggerActivity : Activity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var handled = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        mainHandler.post { handleAction() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Uygulama süreci widget'tan soğuk başlatıldığında Shizuku binder'ı birkaç yüz ms
        // sonra gelir; beklemezsek sessiz mod atlanıp Ayarlar ekranı açılıyordu.
        if (intent?.action != NetSwitchWidgetProvider.ACTION_OPEN_NET_SETTINGS &&
            !DirectModemSwitcher.isShizukuAvailable()
        ) {
            runCatching { Shizuku.addBinderReceivedListenerSticky(binderReceivedListener) }
            mainHandler.postDelayed({ handleAction() }, SHIZUKU_BINDER_WAIT_MS)
        } else {
            handleAction()
        }
    }

    private fun handleAction() {
        if (handled) return
        handled = true
        runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }

        when (intent?.action) {
            NetSwitchWidgetProvider.ACTION_SWITCH_5G -> {
                SimNetworkHelper.triggerNetworkSwitch(this, NetworkModeTarget.MODE_5G)
            }
            NetSwitchWidgetProvider.ACTION_SWITCH_45G -> {
                SimNetworkHelper.triggerNetworkSwitch(this, NetworkModeTarget.MODE_4_5G)
            }
            NetSwitchWidgetProvider.ACTION_SWITCH_TOGGLE -> {
                SimNetworkHelper.triggerNetworkSwitch(this, null)
            }
            NetSwitchWidgetProvider.ACTION_OPEN_NET_SETTINGS -> {
                val resolved = SimNetworkHelper.resolveTargetSim(this)
                SimNetworkHelper.launchMobileNetworkSettings(
                    context = this,
                    subId = resolved.subscriptionId,
                    slotIndex = resolved.slotIndex
                )
            }
        }

        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
        super.onDestroy()
    }

    private companion object {
        const val SHIZUKU_BINDER_WAIT_MS = 1_500L
    }
}
