package qaagent.mobile

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File

const val VERSION = "0.1.0"

private val INSTRUCTIONS = """
    QA helpers for mobile page sources (Android UiAutomator2 / iOS XCUITest). Complements the Appium MCP:
    capture a screen (capture_android, or appium_source for an existing session), then rank_locators,
    screen_to_markdown (vault app-map note), generate_page_object (Kotlin) or diff_screens (after a new build).
    Every tool accepts the page source either inline (`source`) or as a file path (`source_path`).
""".trimIndent()

fun main(): Unit = runBlocking {
    val server = buildServer()
    val transport = StdioServerTransport(System.`in`.asSource().buffered(), System.out.asSink().buffered())
    val session = server.createSession(transport)
    val closed = CompletableDeferred<Unit>()
    session.onClose { closed.complete(Unit) }
    closed.await()
}

fun buildServer(): Server {
    val server = Server(
        Implementation(name = "qa-mobile", version = VERSION),
        ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
        INSTRUCTIONS,
    )

    server.addTool(
        name = "capture_android",
        description = "Capture the current Android screen via adb (no Appium session needed): page source XML + screenshot + density + focused activity. Returns file paths and a short summary.",
        inputSchema = schema(
            "serial" to prop("string", "adb device serial (default: the only connected device)"),
            "out_dir" to prop("string", "directory for the .xml/.png files (default: ~/.qa-agent/captures)"),
            "screenshot" to prop("boolean", "also save a screenshot (default true)"),
        ),
    ) { req ->
        tool {
            val outDir = File(req.str("out_dir") ?: "${System.getProperty("user.home")}/.qa-agent/captures")
            val c = Capture.android(req.str("serial"), outDir, req.bool("screenshot") ?: true)
            val report = LocatorRanker.rank(PageSource.parse(c.source, Platform.ANDROID), densityDpi = c.densityDpi)
            """
            source_path: ${c.sourcePath}
            screenshot_path: ${c.screenshotPath ?: "-"}
            density_dpi: ${c.densityDpi ?: "-"}
            focus: ${c.focus ?: "-"}
            elements: ${report.elements.size} (stable ${report.stableCount}, unstable ${report.unstableCount})
            Next: rank_locators / screen_to_markdown / generate_page_object with source_path (pass density_dpi for touch-target checks).
            """.trimIndent()
        }
    }

    server.addTool(
        name = "appium_source",
        description = "Fetch the page source of an existing Appium session (iOS or Android) over the Appium HTTP API and save it to a file.",
        inputSchema = schema(
            "session_id" to prop("string", "Appium session id"),
            "server_url" to prop("string", "Appium server URL (default http://127.0.0.1:4723)"),
            "out_dir" to prop("string", "directory for the .xml file (default: ~/.qa-agent/captures)"),
            required = listOf("session_id"),
        ),
    ) { req ->
        tool {
            val xml = Capture.appiumSource(req.str("server_url") ?: "http://127.0.0.1:4723", req.str("session_id")!!)
            val src = PageSource.parse(xml)
            val dir = File(req.str("out_dir") ?: "${System.getProperty("user.home")}/.qa-agent/captures").apply { mkdirs() }
            val f = File(dir, "${src.platform.name.lowercase()}-${System.currentTimeMillis()}.xml").apply { writeText(xml) }
            "source_path: ${f.absolutePath}\nplatform: ${src.platform.name.lowercase()}\nnodes: ${src.nodes.size}, interactive: ${src.interactive.size}"
        }
    }

    server.addTool(
        name = "rank_locators",
        description = "Rank locator candidates for every interactive (and text) element of a page source by stability: accessibility id > resource id > predicate/class chain > text > xpath, with penalties for non-unique, dynamic-looking or localised values. Also flags accessibility issues (missing labels, small touch targets).",
        inputSchema = sourceSchema(
            "include_text" to prop("boolean", "include non-interactive text elements (assertion targets), default true"),
            "density_dpi" to prop("integer", "Android screen density for touch-target checks (from capture_android)"),
        ),
    ) { req ->
        tool {
            val report = LocatorRanker.rank(req.pageSource(), req.bool("include_text") ?: true, req.int("density_dpi"))
            buildString {
                appendLine("platform: ${report.platform.name.lowercase()} · elements: ${report.elements.size} · stable: ${report.stableCount} · unstable: ${report.unstableCount}")
                report.elements.forEach { e ->
                    appendLine()
                    appendLine("${e.name} [${e.role}, ${e.node.shortType}] \"${e.node.label ?: e.node.text ?: ""}\"")
                    e.candidates.take(3).forEach { l -> appendLine("  ${l.score.toString().padStart(3)}  ${l.strategy.appiumName}: ${l.value}" + if (l.notes.isEmpty()) "" else "  — ${l.notes.joinToString("; ")}") }
                    e.warnings.forEach { appendLine("  ⚠ $it") }
                }
            }
        }
    }

    server.addTool(
        name = "screen_to_markdown",
        description = "Turn a page source into an app-map screen note (vault screen.md format: element table with best + fallback locator and stability, findings). Optionally writes it to a file.",
        inputSchema = sourceSchema(
            "screen_id" to prop("string", "kebab-case id, e.g. login"),
            "title" to prop("string", "human title, e.g. Login"),
            "build" to prop("string", "app build/version the source was captured on"),
            "screenshot_path" to prop("string", "screenshot to reference in the note"),
            "purpose" to prop("string", "one line: what the screen is for"),
            "density_dpi" to prop("integer", "Android density for touch-target checks"),
            "write_to" to prop("string", "file path to write the note to (e.g. <vault>/projects/<p>/app-map/screens/screen-login.md); omitted = return only"),
            required = listOf("screen_id", "title"),
        ),
    ) { req ->
        tool {
            val report = LocatorRanker.rank(req.pageSource(), densityDpi = req.int("density_dpi"))
            val md = Render.screenMarkdown(req.str("screen_id")!!, req.str("title")!!, report, req.str("build"), req.str("screenshot_path"), req.str("purpose"))
            req.str("write_to")?.let { path ->
                val f = File(path)
                require(f.extension == "md") { "write_to must be a .md file" }
                f.parentFile?.mkdirs(); f.writeText(md)
                "Written to ${f.absolutePath}\n\n$md"
            } ?: md
        }
    }

    server.addTool(
        name = "generate_page_object",
        description = "Generate a Kotlin page object (Appium java-client: AppiumBy locators) from a page source, using the best-ranked locator per element. style=by_fields (val x: By) or element_functions (fun x(driver): WebElement). Adapt to the repo's own element abstraction if it has one.",
        inputSchema = sourceSchema(
            "class_name" to prop("string", "e.g. LoginScreen"),
            "package" to prop("string", "Kotlin package"),
            "style" to prop("string", "by_fields (default) | element_functions"),
            "only_stable" to prop("boolean", "leave out elements without a stable locator (default true)"),
            required = listOf("class_name", "package"),
        ),
    ) { req ->
        tool {
            val style = if (req.str("style") == "element_functions") Render.PoStyle.ELEMENT_FUNCTIONS else Render.PoStyle.BY_FIELDS
            val name = req.str("class_name")!!
            val pkg = req.str("package")!!
            require(Regex("""[A-Z][A-Za-z0-9]*""").matches(name)) { "class_name must be a Kotlin class name" }
            require(Regex("""[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*""").matches(pkg)) { "package must be a Kotlin package name" }
            Render.pageObject(name, pkg, LocatorRanker.rank(req.pageSource(), includeText = true), style, req.bool("only_stable") ?: true)
        }
    }

    server.addTool(
        name = "diff_screens",
        description = "Compare two page sources of the same screen (e.g. old vs new build): elements added, removed (tests will break) and changed (locator or label).",
        inputSchema = schema(
            "old_source" to prop("string", "old page source XML"),
            "old_source_path" to prop("string", "path to the old page source"),
            "new_source" to prop("string", "new page source XML"),
            "new_source_path" to prop("string", "path to the new page source"),
        ),
    ) { req ->
        tool {
            val old = LocatorRanker.rank(PageSource.parse(req.text("old_source", "old_source_path")))
            val new = LocatorRanker.rank(PageSource.parse(req.text("new_source", "new_source_path")))
            Render.diffMarkdown(Render.diff(old, new))
        }
    }

    return server
}

// ---------- helpers ----------

private inline fun tool(block: () -> String): CallToolResult = try {
    CallToolResult(content = listOf(TextContent(block())))
} catch (e: Exception) {
    CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e::class.simpleName}")), isError = true)
}

private fun prop(type: String, description: String) = type to description

private fun schema(vararg props: Pair<String, Pair<String, String>>, required: List<String> = emptyList()) = ToolSchema(
    properties = buildJsonObject {
        props.forEach { (name, p) -> putJsonObject(name) { put("type", p.first); put("description", p.second) } }
    },
    required = required.ifEmpty { null },
)

private fun sourceSchema(vararg extra: Pair<String, Pair<String, String>>, required: List<String> = emptyList()) = schema(
    "source" to prop("string", "page source XML (inline)"),
    "source_path" to prop("string", "path to a page source XML file (e.g. from capture_android)"),
    "platform" to prop("string", "android | ios (default: detected)"),
    *extra,
    required = required,
)

private fun CallToolRequest.args(): JsonObject = arguments ?: JsonObject(emptyMap())
private fun CallToolRequest.str(name: String): String? = args()[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
private fun CallToolRequest.bool(name: String): Boolean? = args()[name]?.jsonPrimitive?.booleanOrNull
private fun CallToolRequest.int(name: String): Int? = args()[name]?.jsonPrimitive?.intOrNull

private fun CallToolRequest.text(inline: String, path: String): String =
    str(inline) ?: str(path)?.let { File(it).readText() } ?: throw IllegalArgumentException("provide `$inline` or `$path`")

private fun CallToolRequest.pageSource(): PageSource {
    val hint = when (str("platform")?.lowercase()) { "android" -> Platform.ANDROID; "ios" -> Platform.IOS; else -> null }
    return PageSource.parse(text("source", "source_path"), hint)
}
