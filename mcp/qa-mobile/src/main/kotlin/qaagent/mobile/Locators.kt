package qaagent.mobile

/** Appium locator strategies (W3C names as used by Appium clients). */
enum class Strategy(val appiumName: String) {
    ACCESSIBILITY_ID("accessibility id"),
    ID("id"),
    UIAUTOMATOR("-android uiautomator"),
    IOS_PREDICATE("-ios predicate string"),
    IOS_CLASS_CHAIN("-ios class chain"),
    XPATH("xpath"),
}

data class Locator(val strategy: Strategy, val value: String, val score: Int, val notes: List<String>) {
    val stable: Boolean get() = score >= 70
}

data class RankedElement(
    val node: UiNode,
    val role: String,
    val name: String,
    val candidates: List<Locator>,
    val warnings: List<String>,
) {
    val best: Locator get() = candidates.first()
}

data class LocatorReport(val platform: Platform, val elements: List<RankedElement>) {
    val stableCount: Int get() = elements.count { it.best.stable }
    val unstableCount: Int get() = elements.size - stableCount
}

/**
 * Ranks locator candidates for every interactive (and optionally textual) element.
 * Scores: 95 accessibility id · 90 resource-id · 75/70 predicate/class chain on label · 60 text · 15 xpath,
 * minus penalties for non-unique, dynamic-looking or localised values.
 */
object LocatorRanker {
    private val DYNAMIC = listOf(
        Regex("""[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""", RegexOption.IGNORE_CASE) to "contains a UUID",
        Regex("""\d{5,}""") to "contains a long number (id/timestamp?)",
        Regex("""\d{1,2}[.:/]\d{1,2}[.:/]\d{2,4}""") to "contains a date",
        Regex("""\b\d{1,2}:\d{2}\b""") to "contains a time",
    )
    private val GENERATED_ID = Regex("""(^|[:/])(id/)?(0x[0-9a-f]+|[a-z]?\d+)$""", RegexOption.IGNORE_CASE)

    fun rank(source: PageSource, includeText: Boolean = true, densityDpi: Int? = null): LocatorReport {
        val targets = source.interactive + if (includeText) source.textual else emptyList()
        val counts = Counts(source.nodes)
        val used = mutableMapOf<String, Int>()
        val elements = targets.distinctBy { it.index }.sortedBy { it.index }.map { node ->
            val candidates = candidates(node, counts).sortedByDescending { it.score }
            RankedElement(
                node = node,
                role = role(node),
                name = uniqueName(baseName(node), used),
                candidates = candidates,
                warnings = warnings(node, candidates, densityDpi),
            )
        }
        return LocatorReport(source.platform, elements)
    }

    private class Counts(nodes: List<UiNode>) {
        val accessibilityId = nodes.mapNotNull { it.accessibilityId }.groupingBy { it }.eachCount()
        val resourceId = nodes.mapNotNull { it.resourceId }.groupingBy { it }.eachCount()
        val text = nodes.mapNotNull { it.text }.groupingBy { it }.eachCount()
        val typeLabel = nodes.mapNotNull { n -> n.label?.let { n.type to it } }.groupingBy { it }.eachCount()
    }

    private fun candidates(n: UiNode, c: Counts): List<Locator> = buildList {
        n.accessibilityId?.let { v -> add(scored(Strategy.ACCESSIBILITY_ID, v, 95, c.accessibilityId[v] ?: 1, v)) }
        n.resourceId?.let { v ->
            val extra = if (GENERATED_ID.containsMatchIn(v)) listOf("id looks generated") to 30 else emptyList<String>() to 0
            add(scored(Strategy.ID, v, 90 - extra.second, c.resourceId[v] ?: 1, null, extra.first))
        }
        when (n.platform) {
            Platform.ANDROID -> n.text?.let { t ->
                add(scored(Strategy.UIAUTOMATOR, "new UiSelector().text(\"${esc(t)}\")", 60, c.text[t] ?: 1, t, listOf("visible text — breaks on copy/language change")))
            }
            Platform.IOS -> n.label?.let { l ->
                val dup = c.typeLabel[n.type to l] ?: 1
                add(scored(Strategy.IOS_PREDICATE, "type == '${n.type}' AND label == '${esc(l, '\'')}'", 75, dup, l, listOf("label is user-visible text — breaks on language change")))
                add(scored(Strategy.IOS_CLASS_CHAIN, "**/${n.type}[`label == \"${esc(l)}\"`]", 70, dup, l, listOf("label is user-visible text")))
            }
        }
        add(Locator(Strategy.XPATH, n.xpath, 15, listOf("absolute xpath — breaks on any layout change")))
    }

    private fun scored(s: Strategy, value: String, base: Int, occurrences: Int, rawForDynamic: String?, notes: List<String> = emptyList()): Locator {
        var score = base
        val n = notes.toMutableList()
        if (occurrences > 1) { score -= 50; n += "not unique ($occurrences matches)" }
        val raw = rawForDynamic ?: value
        DYNAMIC.firstOrNull { it.first.containsMatchIn(raw) }?.let { score -= 35; n += "value ${it.second}" }
        return Locator(s, value, score.coerceAtLeast(1), n)
    }

    private fun warnings(n: UiNode, candidates: List<Locator>, densityDpi: Int?): List<String> = buildList {
        if (n.isInteractive && n.label.isNullOrBlank()) add("a11y: interactive element without accessibility label")
        if (n.isInteractive && n.platform == Platform.ANDROID && n.accessibilityId != null && n.accessibilityId == n.resourceId?.substringAfterLast('/')) {
            add("a11y: content-desc equals the resource id — screen readers will read an id")
        }
        n.bounds?.let { b ->
            if (n.isInteractive) {
                val (w, h, unit, min) = when (n.platform) {
                    Platform.ANDROID -> densityDpi?.let { d -> Quad(b.width * 160 / d, b.height * 160 / d, "dp", 48) }
                    Platform.IOS -> Quad(b.width, b.height, "pt", 44)
                } ?: return@let
                if (w < min || h < min) add("a11y: touch target ${w}×$h $unit (< $min $unit)")
            }
        }
        if (candidates.first().score < 70) add("no stable locator — ask devs for an accessibility id / testID")
    }

    private data class Quad(val a: Int, val b: Int, val c: String, val d: Int)

    fun role(n: UiNode): String {
        val t = n.shortType.lowercase()
        return when {
            "button" in t || "imagebutton" in t -> "button"
            "edittext" in t || "textfield" in t || "searchfield" in t || "textview" == t && n.platform == Platform.IOS -> "input"
            "switch" in t || "toggle" in t || "checkbox" in t || "radiobutton" in t -> "toggle"
            "cell" in t || "tab" in t || "link" in t || "menuitem" in t -> "item"
            "image" in t -> "image"
            n.isInteractive -> "action"
            else -> "text"
        }
    }

    private fun baseName(n: UiNode): String {
        // Developer ids name better than localised labels ("email" vs "E-Mail"); generated ids are skipped.
        val devId = n.resourceId?.substringAfterLast('/')?.takeUnless { GENERATED_ID.containsMatchIn(it) }
        val source = devId ?: n.accessibilityId ?: n.label ?: n.text ?: n.shortType
        val words = source.replace(Regex("""([a-z])([A-Z])"""), "$1 $2").split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotBlank() }.take(5)
        val camel = words.mapIndexed { i, w -> transliterate(w.lowercase()).let { if (i == 0) it else it.replaceFirstChar(Char::uppercase) } }.joinToString("")
        val suffix = when (role(n)) {
            "button" -> "Button"; "input" -> "Field"; "toggle" -> "Toggle"; "item" -> "Item"; "image" -> "Image"; "text" -> "Text"; else -> ""
        }
        val stem = camel.ifBlank { "element" }.let { if (it.first().isDigit()) "e$it" else it }
        return if (stem.endsWith(suffix, ignoreCase = true)) stem else stem + suffix
    }

    private fun uniqueName(base: String, used: MutableMap<String, Int>): String {
        val n = (used[base] ?: 0) + 1
        used[base] = n
        return if (n == 1) base else "$base$n"
    }

    private fun transliterate(s: String) = s.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")

    private fun esc(s: String, quote: Char = '"') = s.replace("\\", "\\\\").replace(quote.toString(), "\\$quote")
}
