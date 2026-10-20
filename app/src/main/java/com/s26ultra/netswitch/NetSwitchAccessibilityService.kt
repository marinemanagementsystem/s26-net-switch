package com.s26ultra.netswitch

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class NetSwitchAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastActionTimestampMs: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pending = SwitchPreferences.getActivePendingRequest(this) ?: return
        val pkg = event?.packageName?.toString().orEmpty()

        // Kendi uygulamamızın içindeyken ayar ekranı açılana kadar bekle
        if (pkg == packageName) return

        val now = System.currentTimeMillis()
        if (now - lastActionTimestampMs < 380L) return

        val root = rootInActiveWindow ?: return
        val pairs = mutableListOf<Pair<AccessibilityNodeInfo, AccessibilityNodeMatcher.ScannedNode>>()
        collectNodes(root, pairs)

        if (pairs.isEmpty()) return
        val scannedList = pairs.map { it.second }

        // 1. Seçim Diyaloğu (5G/LTE/3G/2G vs LTE/3G/2G) açık mı?
        if (AccessibilityNodeMatcher.isSelectionDialogVisible(scannedList)) {
            val bestOption = AccessibilityNodeMatcher.findBestModeOption(
                nodes = scannedList,
                targetMode = pending.targetMode
            )
            if (bestOption != null) {
                val targetNode = pairs[bestOption.index].first
                if (clickNodeOrParent(targetNode)) {
                    lastActionTimestampMs = now
                    SwitchPreferences.clearPendingRequest(this)
                    SwitchPreferences.setSavedModeForSlot(
                        context = this,
                        slotIndex = pending.targetSimSlot,
                        mode = pending.targetMode
                    )
                    val simLabel = "SIM ${pending.targetSimSlot + 1}"
                    SwitchPreferences.recordLog(
                        this,
                        "$simLabel -> ${pending.targetMode.shortLabel} otomatik seçildi"
                    )
                    NetSwitchWidgetProvider.updateAllWidgets(this)

                    Toast.makeText(
                        this,
                        "✓ $simLabel: ${pending.targetMode.shortLabel} seçildi",
                        Toast.LENGTH_SHORT
                    ).show()

                    if (SwitchPreferences.isAutoReturnHomeEnabled(this)) {
                        mainHandler.postDelayed({
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            mainHandler.postDelayed({
                                performGlobalAction(GLOBAL_ACTION_HOME)
                            }, 220L)
                        }, 320L)
                    }
                    return
                }
            }
        }

        // 2. Henüz seçim penceresi açılmadıysa "Şebeke modu" / "Network mode" satırını veya SIM sekmesini bul ve tıkla
        val rowOrTab = AccessibilityNodeMatcher.findNetworkModeRowOrSimTab(
            nodes = scannedList,
            targetSimSlot = pending.targetSimSlot
        )
        if (rowOrTab != null) {
            val targetNode = pairs[rowOrTab.index].first
            if (clickNodeOrParent(targetNode)) {
                lastActionTimestampMs = now
                SwitchPreferences.updatePendingStep(this, AutomationStep.SELECT_MODE_OPTION)
            }
        }
    }

    private fun collectNodes(
        node: AccessibilityNodeInfo?,
        out: MutableList<Pair<AccessibilityNodeInfo, AccessibilityNodeMatcher.ScannedNode>>
    ) {
        if (node == null) return
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        if (text.isNotEmpty() || desc.isNotEmpty()) {
            val idx = out.size
            out.add(
                node to AccessibilityNodeMatcher.ScannedNode(
                    index = idx,
                    text = text,
                    contentDescription = desc,
                    className = node.className?.toString().orEmpty(),
                    isClickable = node.isClickable,
                    isCheckable = node.isCheckable,
                    isSelected = node.isSelected
                )
            )
        }
        for (i in 0 until node.childCount) {
            collectNodes(node.getChild(i), out)
        }
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 6) {
            if (current.isClickable && current.isEnabled) {
                if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
            current = current.parent
            depth++
        }
        return node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    override fun onInterrupt() {
        SwitchPreferences.clearPendingRequest(this)
    }
}
