package com.s26ultra.netswitch

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
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

        // Kendi paketimizin ekranlarını atla
        if (pkg == packageName) return

        val now = System.currentTimeMillis()
        if (now - lastActionTimestampMs < 350L) return

        val pairs = mutableListOf<Pair<AccessibilityNodeInfo, AccessibilityNodeMatcher.ScannedNode>>()
        val activeRoot = rootInActiveWindow
        if (activeRoot != null) {
            collectNodes(activeRoot, pairs)
        }
        // Samsung One UI'da Dialog / BottomSheet ayrı bir AccessibilityWindowInfo içinde açılabilir
        runCatching {
            for (win in windows) {
                val r = win.root
                if (r != null && r != activeRoot) {
                    collectNodes(r, pairs)
                }
            }
        }

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
                if (clickNodeOrGesture(targetNode)) {
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
                            }, 200L)
                        }, 300L)
                    }
                    return
                }
            }
        }

        // 2. Henüz seçim penceresi açılmadıysa "Şebeke modu" / "Network mode" satırını bul ve tıkla
        val rowOrTab = AccessibilityNodeMatcher.findNetworkModeRowOrSimTab(
            nodes = scannedList,
            targetSimSlot = pending.targetSimSlot
        )
        if (rowOrTab != null) {
            val targetNode = pairs[rowOrTab.index].first
            if (clickNodeOrGesture(targetNode)) {
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

    private fun clickNodeOrGesture(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 7) {
            if (current.isClickable && current.isEnabled) {
                if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
            current = current.parent
            depth++
        }

        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }

        // Yedek: Samsung One UI satırlarında koordinata doğrudan dokunma (Gesture)
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (!rect.isEmpty && rect.centerX() > 0 && rect.centerY() > 0) {
            val path = Path().apply {
                moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    override fun onInterrupt() {
        SwitchPreferences.clearPendingRequest(this)
    }
}
