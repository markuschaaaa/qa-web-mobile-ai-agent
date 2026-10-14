package qaagent.mobile

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

data class AndroidCapture(
    val serial: String?,
    val sourcePath: String,
    val screenshotPath: String?,
    val densityDpi: Int?,
    val focus: String?,
    val source: String,
)

/** Captures page source + screenshot without an Appium session (adb), or from a running Appium session (HTTP). */
object Capture {
    private val SAFE_ARG = Regex("""[A-Za-z0-9._:\-]+""")

    fun android(serial: String?, outDir: File, screenshot: Boolean = true): AndroidCapture {
        require(serial == null || SAFE_ARG.matches(serial)) { "invalid device serial" }
        outDir.mkdirs()
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val remote = "/sdcard/qa-mobile-ui.xml"
        adb(serial, "shell", "uiautomator", "dump", remote)
        val xml = adb(serial, "exec-out", "cat", remote)
        require(xml.contains("<hierarchy")) { "uiautomator dump returned no hierarchy (is the screen locked or the app showing a secure window?)" }
        adb(serial, "shell", "rm", "-f", remote)
        val sourceFile = File(outDir, "android-$stamp.xml").apply { writeText(xml) }

        val shot = if (screenshot) File(outDir, "android-$stamp.png").also { f ->
            val p = ProcessBuilder(adbCmd(serial, "exec-out", "screencap", "-p")).redirectOutput(f).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            if (!p.waitFor(30, TimeUnit.SECONDS) || p.exitValue() != 0) f.delete()
        }.takeIf { it.exists() && it.length() > 0 } else null

        val density = runCatching { Regex("""(\d+)""").findAll(adb(serial, "shell", "wm", "density")).last().value.toInt() }.getOrNull()
        val focus = runCatching {
            adb(serial, "shell", "dumpsys", "window").lineSequence().firstOrNull { "mCurrentFocus" in it }?.trim()
        }.getOrNull()
        return AndroidCapture(serial, sourceFile.absolutePath, shot?.absolutePath, density, focus, xml)
    }

    /** Page source of an existing Appium session (iOS or Android), e.g. one started by the Appium MCP or a test. */
    fun appiumSource(serverUrl: String, sessionId: String): String {
        require(SAFE_ARG.matches(sessionId)) { "invalid session id" }
        val uri = URI.create(serverUrl.trimEnd('/') + "/session/$sessionId/source")
        require(uri.scheme in setOf("http", "https")) { "server url must be http(s)" }
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
        val res = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofString())
        require(res.statusCode() == 200) { "Appium returned HTTP ${res.statusCode()}: ${res.body().take(300)}" }
        return Json.parseToJsonElement(res.body()).jsonObject["value"]!!.jsonPrimitive.content
    }

    private fun adbCmd(serial: String?, vararg args: String): List<String> =
        listOf(System.getenv("ADB") ?: "adb") + (if (serial != null) listOf("-s", serial) else emptyList()) + args

    private fun adb(serial: String?, vararg args: String): String {
        val p = ProcessBuilder(adbCmd(serial, *args)).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        check(p.waitFor(60, TimeUnit.SECONDS)) { "adb ${args.first()} timed out" }
        check(p.exitValue() == 0) { "adb ${args.joinToString(" ")} failed: ${out.take(300)}" }
        return out
    }
}
