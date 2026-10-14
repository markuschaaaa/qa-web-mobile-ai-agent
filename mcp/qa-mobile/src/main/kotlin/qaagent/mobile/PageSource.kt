package qaagent.mobile

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

enum class Platform { ANDROID, IOS }

/** Pixel rectangle on screen (Android: px, iOS: pt). */
data class Bounds(val x: Int, val y: Int, val width: Int, val height: Int)

/** One element of a mobile page source, platform-neutral. */
data class UiNode(
    val platform: Platform,
    /** Class / element type, e.g. android.widget.Button or XCUIElementTypeButton. */
    val type: String,
    val attributes: Map<String, String>,
    val bounds: Bounds?,
    /** 1-based position among all nodes in document order (stable within one source). */
    val index: Int,
    /** Absolute XPath to this node, e.g. /hierarchy/android.widget.FrameLayout[1]/... */
    val xpath: String,
) {
    operator fun get(name: String): String? = attributes[name]?.takeIf { it.isNotBlank() }

    val text: String? get() = if (platform == Platform.ANDROID) this["text"] else this["value"] ?: this["label"]
    val accessibilityId: String? get() = if (platform == Platform.ANDROID) this["content-desc"] else this["name"]
    val resourceId: String? get() = if (platform == Platform.ANDROID) this["resource-id"] else null
    val label: String? get() = if (platform == Platform.ANDROID) this["content-desc"] ?: this["text"] else this["label"] ?: this["name"]
    val shortType: String get() = type.substringAfterLast('.').removePrefix("XCUIElementType")

    val isVisible: Boolean
        get() = when (platform) {
            Platform.ANDROID -> this["displayed"] != "false"
            Platform.IOS -> this["visible"] != "false"
        }

    val isInteractive: Boolean
        get() = when (platform) {
            Platform.ANDROID -> listOf("clickable", "checkable", "long-clickable", "scrollable").any { this[it] == "true" } ||
                shortType in ANDROID_INPUT_TYPES
            Platform.IOS -> shortType in IOS_INTERACTIVE_TYPES
        }

    val isTextual: Boolean get() = !isInteractive && !text.isNullOrBlank()

    companion object {
        val ANDROID_INPUT_TYPES = setOf("EditText", "AutoCompleteTextView", "Switch", "CheckBox", "RadioButton", "Spinner", "SeekBar")
        val IOS_INTERACTIVE_TYPES = setOf(
            "Button", "TextField", "SecureTextField", "SearchField", "TextView", "Switch", "Slider", "Stepper",
            "SegmentedControl", "Link", "Cell", "Picker", "PickerWheel", "Toggle", "Tab", "MenuItem", "Key",
        )
    }
}

data class PageSource(val platform: Platform, val nodes: List<UiNode>) {
    val interactive: List<UiNode> get() = nodes.filter { it.isVisible && it.isInteractive }
    val textual: List<UiNode> get() = nodes.filter { it.isVisible && it.isTextual }

    companion object {
        private val ANDROID_BOUNDS = Regex("""\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")

        /** Parses an Appium / UiAutomator / XCUITest page source. Rejects DTDs and external entities. */
        fun parse(xml: String, platformHint: Platform? = null): PageSource {
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                isExpandEntityReferences = false
                isNamespaceAware = false
            }
            val cleaned = xml.substring(xml.indexOf('<').coerceAtLeast(0)).substringBeforeLast('>') + ">"
            val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(cleaned)))
            val root = doc.documentElement
            val platform = platformHint ?: detect(root)
            val nodes = mutableListOf<UiNode>()
            walk(root, "", platform, nodes)
            return PageSource(platform, nodes)
        }

        private fun detect(root: Element): Platform {
            val src = root.tagName + " " + (root.firstElementChild()?.tagName ?: "")
            return if (src.contains("XCUIElementType")) Platform.IOS else Platform.ANDROID
        }

        private fun walk(el: Element, parentPath: String, platform: Platform, out: MutableList<UiNode>) {
            val tag = el.tagName
            val sameTagBefore = generateSequence(el.previousSibling) { it.previousSibling }
                .count { it is Element && it.tagName == tag }
            val path = "$parentPath/$tag[${sameTagBefore + 1}]"
            val attrs = buildMap {
                for (i in 0 until el.attributes.length) {
                    val a = el.attributes.item(i)
                    put(a.nodeName, a.nodeValue)
                }
            }
            // UiAutomator dumps use <node class="…">; Appium sources use the class as tag name.
            val type = attrs["class"]?.takeIf { tag == "node" } ?: attrs["type"]?.takeIf { platform == Platform.IOS } ?: tag
            if (tag != "hierarchy" && tag != "AppiumAUT") {
                out += UiNode(platform, type, attrs, bounds(attrs, platform), out.size + 1, path)
            }
            var child = el.firstChild
            while (child != null) {
                if (child is Element) walk(child, path, platform, out)
                child = child.nextSibling
            }
        }

        private fun bounds(attrs: Map<String, String>, platform: Platform): Bounds? = when (platform) {
            Platform.ANDROID -> attrs["bounds"]?.let { ANDROID_BOUNDS.find(it) }?.destructured?.let { (x1, y1, x2, y2) ->
                Bounds(x1.toInt(), y1.toInt(), x2.toInt() - x1.toInt(), y2.toInt() - y1.toInt())
            }
            Platform.IOS -> {
                val v = listOf("x", "y", "width", "height").map { attrs[it]?.toIntOrNull() }
                if (v.all { it != null }) Bounds(v[0]!!, v[1]!!, v[2]!!, v[3]!!) else null
            }
        }

        private fun Element.firstElementChild(): Element? {
            var c: Node? = firstChild
            while (c != null && c !is Element) c = c.nextSibling
            return c as Element?
        }
    }
}
