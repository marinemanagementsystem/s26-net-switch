package com.s26ultra.netswitch

import android.app.Activity
import android.os.Bundle

/**
 * Ana ekrandaki Widget butonlarına basıldığında Android 15/16 Background Activity Launch (BAL)
 * engeline takılmadan geçişi anında çalıştıran görünmez (Translucent) köprü Activity.
 */
class SwitchTriggerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
}
