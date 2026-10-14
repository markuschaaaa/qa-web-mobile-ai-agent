package qaagent.mobile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QaMobileTest {
    private fun res(name: String) = javaClass.getResource("/$name")!!.readText()
    private val android by lazy { LocatorRanker.rank(PageSource.parse(res("android-login.xml")), densityDpi = 420) }
    private val ios by lazy { LocatorRanker.rank(PageSource.parse(res("ios-login.xml"))) }
    private fun LocatorReport.byLabel(label: String) = elements.first { it.node.label == label || it.node.text == label }
    private fun LocatorReport.byResource(suffix: String) = elements.first { it.node.resourceId?.endsWith(suffix) == true }

    @Test fun `detects platforms and ignores trailing dump text`() {
        assertEquals(Platform.ANDROID, android.platform)
        assertEquals(Platform.IOS, ios.platform)
    }

    @Test fun `accessibility id wins on android`() {
        val email = android.byResource("/email")
        assertEquals(Strategy.ACCESSIBILITY_ID, email.best.strategy)
        assertEquals("E-Mail", email.best.value)
        assertTrue(email.best.stable)
    }

    @Test fun `resource id is used when there is no content-desc, and the missing label is flagged`() {
        val password = android.byResource("/password")
        assertEquals(Strategy.ID, password.best.strategy)
        assertTrue(password.best.stable)
        assertTrue(password.warnings.any { "without accessibility label" in it })
    }

    @Test fun `content-desc equal to the id is an a11y finding`() {
        val login = android.byResource("/login_button")
        assertEquals("login_button", login.best.value)
        assertTrue(login.warnings.any { "screen readers will read an id" in it })
    }

    @Test fun `generated id, small target and no label are unstable findings`() {
        val icon = android.elements.first { it.node.shortType == "ImageButton" }
        assertFalse(icon.best.stable)
        assertTrue(icon.warnings.any { "touch target" in it })
        assertTrue(icon.warnings.any { "no stable locator" in it })
    }

    @Test fun `duplicate texts are not unique and get distinct names`() {
        val help = android.elements.filter { it.node.text == "Hilfe" }
        assertEquals(2, help.size)
        assertTrue(help.all { !it.best.stable })
        assertEquals(setOf("hilfeButton", "hilfeButton2"), help.map { it.name }.toSet())
    }

    @Test fun `dates in values are penalised as dynamic`() {
        val last = android.elements.first { it.node.text?.startsWith("Zuletzt") == true }
        assertTrue(last.candidates.first { it.strategy == Strategy.UIAUTOMATOR }.notes.any { "date" in it })
    }

    @Test fun `ios uses name, falls back to label predicate, skips invisible`() {
        assertEquals("email_field", ios.byLabel("E-Mail").best.value)
        val pw = ios.byLabel("Passwort")
        assertEquals(Strategy.IOS_PREDICATE, pw.best.strategy)
        assertTrue(pw.best.stable)
        assertTrue(ios.elements.none { it.node.label == "Hidden" })
        val unlabeled = ios.elements.first { it.node.shortType == "Button" && it.node.label == null }
        assertTrue(unlabeled.warnings.any { "touch target 30×30 pt" in it })
    }

    @Test fun `screen note follows the vault template`() {
        val md = Render.screenMarkdown("login", "Login", android, "1.2.3", "/tmp/a.png", "Sign in")
        assertTrue(md.startsWith("---\ntype: screen\nid: screen-login\nplatform: android\nbuild: 1.2.3"))
        assertTrue("| Element | Role/type | Text | Best locator | Fallback | Stable? |" in md)
        assertTrue("## Findings" in md)
    }

    @Test fun `page object uses AppiumBy and leaves out unstable elements`() {
        val po = Render.pageObject("LoginScreen", "com.example.screens", android, Render.PoStyle.BY_FIELDS, onlyStable = true)
        assertTrue("object LoginScreen {" in po)
        assertTrue("val emailField: By = AppiumBy.accessibilityId(\"E-Mail\")" in po)
        assertTrue("AppiumBy.id(\"com.example.demoshop:id/password\")" in po)
        assertFalse("hilfeButton" in po)
        assertTrue("left out" in po)
    }

    @Test fun `diff reports removed, added and changed elements`() {
        val v2 = LocatorRanker.rank(PageSource.parse(res("android-login-v2.xml")))
        val d = Render.diff(android, v2)
        assertTrue(d.added.any { it.node.resourceId?.endsWith("/google") == true })
        assertTrue(d.removed.any { it.node.text == "Passwort vergessen?" })
        assertNotNull(d.changed.firstOrNull { (a, _) -> a.node.resourceId?.endsWith("/password") == true })
        assertNotNull(d.changed.firstOrNull { (a, b) -> a.node.text == "Anmelden" && b.node.text == "Einloggen" })
    }

    @Test fun `doctype and external entities are rejected`() {
        val evil = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><hierarchy><node text="&e;"/></hierarchy>"""
        assertFailsWith<Exception> { PageSource.parse(evil) }
    }

    @Test fun `server registers all tools`() {
        assertNotNull(buildServer())
    }
}
