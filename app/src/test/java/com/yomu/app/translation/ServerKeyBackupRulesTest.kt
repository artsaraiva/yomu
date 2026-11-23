package com.yomu.app.translation

import android.content.Context
import com.yomu.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.w3c.dom.Element
import org.xmlpull.v1.XmlPullParser
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

// Conscrypt off: once installed it stays in the JVM, and Oracle JDKs then refuse every javax.crypto call
// in later tests, ServerSettingsStoreTest's among them.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class ServerKeyBackupRulesTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val keyFile = "${ServerSettingsStore.KEY_FILE}.xml"

    @Test
    fun `cloud backup leaves the key's file out`() {
        assertTrue(keyFile in excludedSharedPrefs(R.xml.data_extraction_rules, section = "cloud-backup"))
    }

    @Test
    fun `device transfer leaves the key's file out`() {
        assertTrue(keyFile in excludedSharedPrefs(R.xml.data_extraction_rules, section = "device-transfer"))
    }

    @Test
    fun `backup on Android 11 and older leaves the key's file out`() {
        assertTrue(keyFile in excludedSharedPrefs(R.xml.backup_rules, section = "full-backup-content"))
    }

    @Test
    fun `the manifest points backup at both rule files`() {
        val application = manifestApplication()

        assertEquals("@xml/data_extraction_rules", application.getAttributeNS(ANDROID_NS, "dataExtractionRules"))
        assertEquals("@xml/backup_rules", application.getAttributeNS(ANDROID_NS, "fullBackupContent"))
    }

    // ApplicationInfo keeps both attributes hidden, and the manifest AGP merges for unit tests leaves
    // out the app's own <application> attributes, so the test reads the source manifest. Gradle runs
    // unit tests from the module directory.
    private fun manifestApplication(): Element {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val manifest = factory.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        return manifest.getElementsByTagName("application").item(0) as Element
    }

    private fun excludedSharedPrefs(rules: Int, section: String): List<String> {
        val parser = context.resources.getXml(rules)
        val excluded = mutableListOf<String>()
        var inSection = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when {
                    parser.name == section -> inSection = true
                    inSection && parser.name == "exclude" && parser.getAttributeValue(null, "domain") == "sharedpref" ->
                        excluded += parser.getAttributeValue(null, "path")
                }
                XmlPullParser.END_TAG -> if (parser.name == section) inSection = false
            }
        }
        return excluded
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
