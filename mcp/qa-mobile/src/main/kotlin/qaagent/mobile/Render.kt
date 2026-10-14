package qaagent.mobile

import java.time.LocalDate

object Render {

    /** Screen note in the vault's `screen.md` format. */
    fun screenMarkdown(screenId: String, title: String, report: LocatorReport, build: String?, screenshot: String?, purpose: String?): String {
        val platform = report.platform.name.lowercase()
        val rows = report.elements.joinToString("\n") { e ->
            val fallback = e.candidates.getOrNull(1)?.let { "${it.strategy.appiumName}: `${md(it.value)}`" } ?: "—"
            val stable = if (e.best.stable) "✅" else "⚠️ ${e.best.notes.joinToString("; ")}"
            "| ${e.name} | ${e.role} (${e.node.shortType}) | ${md(e.node.label ?: e.node.text ?: "")} | ${e.best.strategy.appiumName}: `${md(e.best.value)}` | $fallback | $stable |"
        }
        val warnings = report.elements.flatMap { e -> e.warnings.map { "- **${e.name}** — $it" } }.distinct()
        return buildString {
            appendLine("---")
            appendLine("type: screen")
            appendLine("id: screen-$screenId")
            appendLine("platform: $platform")
            appendLine("build: ${build ?: "<unknown>"}")
            appendLine("verified: ${LocalDate.now()}")
            appendLine("source: qa-mobile MCP")
            appendLine("---")
            appendLine("# $title")
            appendLine()
            appendLine("**Purpose:** ${purpose ?: "> [!question] Purpose of this screen?"}")
            appendLine("**Reached via:** > [!question] add the navigation path / flow link")
            if (screenshot != null) appendLine("**Screenshot:** ![[${screenshot.substringAfterLast('/')}]]")
            appendLine()
            appendLine("Locators: ${report.stableCount} stable · ${report.unstableCount} unstable")
            appendLine()
            appendLine("| Element | Role/type | Text | Best locator | Fallback | Stable? |")
            appendLine("|---|---|---|---|---|---|")
            appendLine(rows)
            if (warnings.isNotEmpty()) {
                appendLine()
                appendLine("## Findings")
                warnings.forEach { appendLine(it) }
            }
        }
    }

    enum class PoStyle { BY_FIELDS, ELEMENT_FUNCTIONS }

    /**
     * Kotlin page object. BY_FIELDS: `val x: By` constants (works with any java-client based framework).
     * ELEMENT_FUNCTIONS: functions taking the driver, returning WebElement — for repos without their own element wrapper.
     * Repos with their own abstraction (e.g. Element.create(android=…, ios=…)) should map from BY_FIELDS output.
     */
    fun pageObject(className: String, pkg: String, report: LocatorReport, style: PoStyle, onlyStable: Boolean): String {
        val elements = report.elements.filter { !onlyStable || it.best.stable }
        val skipped = report.elements.size - elements.size
        val imports = sortedSetOf("io.appium.java_client.AppiumBy", "org.openqa.selenium.By")
        if (style == PoStyle.ELEMENT_FUNCTIONS) imports += listOf("io.appium.java_client.AppiumDriver", "org.openqa.selenium.WebElement")
        return buildString {
            appendLine("package $pkg")
            appendLine()
            imports.forEach { appendLine("import $it") }
            appendLine()
            appendLine("/**")
            appendLine(" * ${className} (${report.platform.name.lowercase()}) — locators from a captured page source, ranked by stability.")
            if (skipped > 0) appendLine(" * $skipped element(s) without a stable locator were left out — see the app-map screen note.")
            appendLine(" */")
            appendLine("object $className {")
            elements.forEach { e ->
                val expr = byExpression(e.best)
                val label = (e.node.label ?: e.node.text)?.let { " \"${it.take(40).replace("*/", "* /")}\"" } ?: ""
                appendLine()
                appendLine("    /** ${e.role}$label — ${e.best.strategy.appiumName}${if (e.best.stable) "" else " (UNSTABLE: ${e.best.notes.joinToString("; ")})"} */")
                when (style) {
                    PoStyle.BY_FIELDS -> appendLine("    val ${e.name}: By = $expr")
                    PoStyle.ELEMENT_FUNCTIONS -> appendLine("    fun ${e.name}(driver: AppiumDriver): WebElement = driver.findElement($expr)")
                }
            }
            appendLine("}")
        }
    }

    private fun byExpression(l: Locator): String {
        val v = kotlinString(l.value)
        return when (l.strategy) {
            Strategy.ACCESSIBILITY_ID -> "AppiumBy.accessibilityId($v)"
            Strategy.ID -> "AppiumBy.id($v)"
            Strategy.UIAUTOMATOR -> "AppiumBy.androidUIAutomator($v)"
            Strategy.IOS_PREDICATE -> "AppiumBy.iOSNsPredicateString($v)"
            Strategy.IOS_CLASS_CHAIN -> "AppiumBy.iOSClassChain($v)"
            Strategy.XPATH -> "By.xpath($v)"
        }
    }

    private fun kotlinString(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""

    private fun md(s: String) = s.replace("|", "\\|").replace("\n", " ").take(80)

    // ---------- diff ----------

    data class ScreenDiff(val added: List<RankedElement>, val removed: List<RankedElement>, val changed: List<Pair<RankedElement, RankedElement>>)

    /** Matches elements by their identity (resource id → accessibility id → role+label) and reports what changed. */
    fun diff(old: LocatorReport, new: LocatorReport): ScreenDiff {
        fun key(e: RankedElement) = e.node.resourceId ?: e.node.accessibilityId ?: "${e.role}:${e.node.label ?: e.node.text ?: e.node.xpath}"
        val o = old.elements.associateBy(::key)
        val n = new.elements.associateBy(::key)
        val changed = o.keys.intersect(n.keys).mapNotNull { k ->
            val a = o.getValue(k); val b = n.getValue(k)
            if (a.best.value != b.best.value || a.node.label != b.node.label || a.node.text != b.node.text || a.node.type != b.node.type) a to b else null
        }
        return ScreenDiff(added = (n.keys - o.keys).map(n::getValue), removed = (o.keys - n.keys).map(o::getValue), changed = changed)
    }

    fun diffMarkdown(d: ScreenDiff): String = buildString {
        appendLine("Added: ${d.added.size} · Removed: ${d.removed.size} · Changed: ${d.changed.size}")
        if (d.removed.isNotEmpty()) { appendLine("\n### Removed (tests using these will break)"); d.removed.forEach { appendLine("- ${it.name}: ${it.best.strategy.appiumName} `${it.best.value}`") } }
        if (d.changed.isNotEmpty()) {
            appendLine("\n### Changed")
            d.changed.forEach { (a, b) ->
                val parts = buildList {
                    if (a.best.value != b.best.value) add("locator `${a.best.value}` → `${b.best.value}`")
                    if (a.node.label != b.node.label) add("label \"${a.node.label}\" → \"${b.node.label}\"")
                    if (a.node.text != b.node.text) add("text \"${a.node.text}\" → \"${b.node.text}\"")
                    if (a.node.type != b.node.type) add("type ${a.node.shortType} → ${b.node.shortType}")
                }
                appendLine("- ${a.name}: ${parts.joinToString(" · ")}")
            }
        }
        if (d.added.isNotEmpty()) { appendLine("\n### Added"); d.added.forEach { appendLine("- ${it.name} (${it.role}): ${it.best.strategy.appiumName} `${it.best.value}`") } }
    }
}
