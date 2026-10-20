package com.s26ultra.netswitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityNodeMatcherTest {

    @Test
    fun `detects selection dialog only when options or radio buttons are present`() {
        // Ana ekranda sadece tek bir summary satırı varken diyalog açık sayılmamalı
        val mainScreenNodes = listOf(
            AccessibilityNodeMatcher.ScannedNode(0, "Veri dolaşımı"),
            AccessibilityNodeMatcher.ScannedNode(1, "Şebeke modu SIM 1"),
            AccessibilityNodeMatcher.ScannedNode(2, "5G/LTE/3G/2G (otomatik bağlantı)")
        )
        assertFalse(AccessibilityNodeMatcher.isSelectionDialogVisible(mainScreenNodes))

        // Diyalog açıldığında birden fazla seçenek listelenir
        val dialogNodes = listOf(
            AccessibilityNodeMatcher.ScannedNode(0, "Şebeke modu SIM 1"),
            AccessibilityNodeMatcher.ScannedNode(
                1,
                "5G/LTE/3G/2G (otomatik bağlantı)",
                className = "android.widget.CheckedTextView",
                isCheckable = true
            ),
            AccessibilityNodeMatcher.ScannedNode(
                2,
                "LTE/3G/2G (otomatik bağlantı)",
                className = "android.widget.CheckedTextView",
                isCheckable = true
            ),
            AccessibilityNodeMatcher.ScannedNode(
                3,
                "3G/2G (otomatik bağlantı)",
                className = "android.widget.CheckedTextView",
                isCheckable = true
            )
        )
        assertTrue(AccessibilityNodeMatcher.isSelectionDialogVisible(dialogNodes))
    }

    @Test
    fun `selects 5G or 4_5G option accurately on Turkish and English One UI`() {
        val dialogNodes = listOf(
            AccessibilityNodeMatcher.ScannedNode(0, "5G/LTE/3G/2G (otomatik bağlantı)"),
            AccessibilityNodeMatcher.ScannedNode(1, "LTE/3G/2G (otomatik bağlantı)"),
            AccessibilityNodeMatcher.ScannedNode(2, "3G/2G (otomatik bağlantı)"),
            AccessibilityNodeMatcher.ScannedNode(3, "Yalnızca 2G")
        )

        val picked5G = AccessibilityNodeMatcher.findBestModeOption(
            dialogNodes,
            NetworkModeTarget.MODE_5G
        )
        assertNotNull(picked5G)
        assertEquals(0, picked5G?.index)

        val picked45G = AccessibilityNodeMatcher.findBestModeOption(
            dialogNodes,
            NetworkModeTarget.MODE_4_5G
        )
        assertNotNull(picked45G)
        assertEquals(1, picked45G?.index)
    }

    @Test
    fun `selects correct SIM 1 or SIM 2 network mode row on dual SIM screen`() {
        val dualSimRows = listOf(
            AccessibilityNodeMatcher.ScannedNode(0, "VoLTE aramaları SIM 1"),
            AccessibilityNodeMatcher.ScannedNode(1, "Şebeke modu SIM 1"),
            AccessibilityNodeMatcher.ScannedNode(2, "VoLTE aramaları SIM 2"),
            AccessibilityNodeMatcher.ScannedNode(3, "Şebeke modu SIM 2")
        )

        val sim1Row = AccessibilityNodeMatcher.findNetworkModeRowOrSimTab(dualSimRows, targetSimSlot = 0)
        val sim2Row = AccessibilityNodeMatcher.findNetworkModeRowOrSimTab(dualSimRows, targetSimSlot = 1)

        assertEquals(1, sim1Row?.index)
        assertEquals(3, sim2Row?.index)
    }
}
