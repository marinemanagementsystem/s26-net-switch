package com.s26ultra.netswitch

import java.util.Locale

/**
 * Samsung One UI (S26 Ultra / One UI 7-8) ve standart Android Mobil Ağlar ekranındaki
 * düğümleri (Şebeke modu satırı, SIM 1/SIM 2 sekmeleri ve 5G / 4.5G seçenekleri)
 * tespit eden saf eşleştirme mantığı.
 */
object AccessibilityNodeMatcher {

    data class ScannedNode(
        val index: Int,
        val text: String,
        val contentDescription: String = "",
        val className: String = "",
        val isClickable: Boolean = true,
        val isCheckable: Boolean = false,
        val isSelected: Boolean = false
    ) {
        val combinedText: String
            get() = "$text $contentDescription".trim()
    }

    private val NETWORK_MODE_ROW_KEYWORDS = listOf(
        "şebeke modu",
        "sebeke modu",
        "ağ modu",
        "ag modu",
        "tercih edilen ağ türü",
        "tercih edilen şebeke türü",
        "network mode",
        "preferred network type"
    )

    /**
     * Bir metnin "5G/LTE/3G/2G" veya "LTE/3G/2G" gibi bir şebeke modu seçeneği olup olmadığını kontrol eder.
     */
    fun isNetworkOptionText(raw: String): Boolean {
        val normalized = normalize(raw)
        if (normalized.isBlank()) return false
        // Seçeneklerde genellikle "/" (örn: 5G/LTE/3G/2G, LTE/3G/2G) veya "yalnızca 5g", "5g only", "lte only", "otomatik bağlantı" bulunur
        val hasSlashPattern = normalized.contains("5g/") ||
            normalized.contains("lte/") ||
            normalized.contains("4.5g/") ||
            normalized.contains("4g/") ||
            normalized.contains("3g/2g") ||
            normalized.contains("nr/lte")

        val hasExplicitOptionPhrase = normalized.contains("yalnızca 5g") ||
            normalized.contains("yalnızca lte") ||
            normalized.contains("yalnızca 4.5g") ||
            normalized.contains("5g only") ||
            normalized.contains("lte only") ||
            normalized.contains("5g otomatik") ||
            normalized.contains("5g auto")

        return hasSlashPattern || hasExplicitOptionPhrase
    }

    /**
     * Şebeke Modu seçim penceresinin (Dialog / Liste) gerçekten açık olup olmadığını belirler.
     * Ana ayar ekranında da "Şebeke modu" satırının altında mevcut modun özeti (summary) yazar.
     * Diyalogun açıldığını anlamak için:
     * - Ekranda en az 2 farklı şebeke modu seçeneği (örn. hem 5G/LTE/... hem LTE/3G/2G) görünmeli, VEYA
     * - Seçenek bir RadioButton / CheckedTextView (`isCheckable == true`) içinde yer almalıdır.
     */
    fun isSelectionDialogVisible(nodes: List<ScannedNode>): Boolean {
        val optionNodes = nodes.filter { isNetworkOptionText(it.combinedText) }
        if (optionNodes.size >= 2) return true
        return optionNodes.any {
            it.isCheckable ||
                it.className.contains("RadioButton", ignoreCase = true) ||
                it.className.contains("CheckedTextView", ignoreCase = true)
        }
    }

    /**
     * Açılan Şebeke Modu diyaloğunda hedef moda (5G veya 4.5G/LTE) karşılık gelen en uygun satırı bulur.
     */
    fun findBestModeOption(
        nodes: List<ScannedNode>,
        targetMode: NetworkModeTarget
    ): ScannedNode? {
        val candidates = nodes.filter { isNetworkOptionText(it.combinedText) }
        if (candidates.isEmpty()) return null

        return when (targetMode) {
            NetworkModeTarget.MODE_5G -> {
                // İçinde 5G veya NR geçen seçenekler; öncelik otomatik bağlantı (5G/LTE/3G/2G) olanlarda
                candidates
                    .filter {
                        val norm = normalize(it.combinedText)
                        norm.contains("5g") || norm.contains("nr")
                    }
                    .maxByOrNull { score5GOption(normalize(it.combinedText)) }
            }

            NetworkModeTarget.MODE_4_5G -> {
                // İçinde 5G veya NR GEÇMEYEN, fakat LTE / 4.5G / 4G geçen seçenekler
                candidates
                    .filter {
                        val norm = normalize(it.combinedText)
                        val has5G = norm.contains("5g") || norm.contains("nr")
                        val has45G = norm.contains("lte") || norm.contains("4.5g") || norm.contains("4g")
                        !has5G && has45G
                    }
                    .maxByOrNull { score45GOption(normalize(it.combinedText)) }
            }
        }
    }

    private fun score5GOption(norm: String): Int {
        var score = 10
        if (norm.contains("lte") || norm.contains("4.5g") || norm.contains("4g")) score += 30
        if (norm.contains("otomatik") || norm.contains("auto")) score += 25
        if (norm.contains("3g")) score += 10
        if (norm.contains("yalnızca") || norm.contains("only")) score -= 15
        return score
    }

    private fun score45GOption(norm: String): Int {
        var score = 10
        if (norm.contains("3g")) score += 30
        if (norm.contains("otomatik") || norm.contains("auto")) score += 25
        if (norm.contains("yalnızca") || norm.contains("only")) score -= 15
        return score
    }

    /**
     * Mobil Ağlar ana sayfasında hedef SIM (slot 0 = SIM 1, slot 1 = SIM 2) için
     * tıklanacak "Şebeke modu" satırını veya önce geçilmesi gereken SIM sekmesini bulur.
     */
    fun findNetworkModeRowOrSimTab(
        nodes: List<ScannedNode>,
        targetSimSlot: Int
    ): ScannedNode? {
        val simNumber = if (targetSimSlot == 1) 2 else 1

        // 1. Önce doğrudan "Şebeke modu SIM 1" / "Şebeke modu SIM 2" gibi açıkça SIM numarası içeren satır var mı?
        val explicitSimRow = nodes.firstOrNull { node ->
            val norm = normalize(node.combinedText)
            val isNetModeRow = NETWORK_MODE_ROW_KEYWORDS.any { norm.contains(it) }
            val hasExplicitSim = norm.contains("sim $simNumber") || norm.contains("sim$simNumber")
            isNetModeRow && hasExplicitSim
        }
        if (explicitSimRow != null) return explicitSimRow

        // 2. Ekrandaki tüm "Şebeke modu" / "Network mode" satırlarını topla
        val allNetModeRows = nodes.filter { node ->
            val norm = normalize(node.combinedText)
            NETWORK_MODE_ROW_KEYWORDS.any { norm.contains(it) }
        }

        // Eğer ekranda 2 adet "Şebeke modu" satırı varsa (üstteki SIM 1, alttaki SIM 2)
        if (allNetModeRows.size >= 2) {
            return if (targetSimSlot == 1) allNetModeRows[1] else allNetModeRows[0]
        }

        // 3. Eğer ekranda SIM 1 / SIM 2 sekmeleri varsa ve hedef SIM sekmesi seçili değilse önce sekmeye tıkla
        val targetSimTab = nodes.firstOrNull { node ->
            val norm = normalize(node.combinedText)
            (norm == "sim $simNumber" || norm == "sim$simNumber" || norm.startsWith("sim $simNumber ")) &&
                !node.isSelected &&
                !NETWORK_MODE_ROW_KEYWORDS.any { norm.contains(it) }
        }
        if (targetSimTab != null && allNetModeRows.size <= 1 && targetSimSlot == 1) {
            return targetSimTab
        }

        // 4. Tek bir "Şebeke modu" satırı varsa onu döndür
        return allNetModeRows.firstOrNull()
    }

    fun isNetworkModeRow(node: ScannedNode): Boolean {
        val norm = normalize(node.combinedText)
        return NETWORK_MODE_ROW_KEYWORDS.any { norm.contains(it) }
    }

    fun normalize(input: String): String {
        return input
            .lowercase(Locale("tr", "TR"))
            .replace('ı', 'i')
            .replace('ş', 's')
            .replace('ğ', 'g')
            .replace("  ", " ")
            .trim()
    }
}
