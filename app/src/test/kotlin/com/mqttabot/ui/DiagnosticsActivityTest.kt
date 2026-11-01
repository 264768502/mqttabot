package com.mqttabot.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.IntentCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mqttabot.AppGraph
import com.mqttabot.R
import com.mqttabot.log.AppLog
import com.mqttabot.log.Diagnostics
import com.mqttabot.testutil.text
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream

@RunWith(AndroidJUnit4::class)
class DiagnosticsActivityTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private var scenario: ActivityScenario<DiagnosticsActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        AppGraph.logFile.clear()
    }

    private fun launch() {
        scenario = ActivityScenario.launchActivityForResult(DiagnosticsActivity::class.java)
    }

    private fun activity(): DiagnosticsActivity {
        var a: DiagnosticsActivity? = null
        scenario!!.onActivity { a = it }
        return a!!
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag).performScrollTo()

    private fun enableAndLog() {
        launch()
        node(TAG_LOG_ENABLED).assertIsOff().performClick().assertIsOn()
        AppLog.i("Test", "something happened")
        // Size refreshes on resume.
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
    }

    @Test
    fun showsPathAndSizeAndToggles() {
        launch()
        node(TAG_LOG_PATH).assertTextEquals(Diagnostics.displayPath(AppGraph.logFile.current))
        node(TAG_SAVE_LOG).assertIsNotEnabled()
        node(TAG_SHARE_LOG).assertIsNotEnabled()
        node(TAG_CLEAR_LOG).assertIsNotEnabled()

        node(TAG_LOG_ENABLED).assertIsOff().performClick().assertIsOn()
        assertTrue(AppGraph.diagnosticLogging.value)
        assertTrue(AppGraph.settings.diagnosticLogging)
        node(TAG_SAVE_LOG).assertIsEnabled()
        assertTrue(node(TAG_LOG_SIZE).text().isNotEmpty())

        node(TAG_LOG_ENABLED).performClick().assertIsOff()
        assertNull(AppLog.file)
    }

    @Test
    fun savesTheLogWithAReportHeader() {
        enableAndLog()
        node(TAG_SAVE_LOG).performClick()
        val request = shadowOf(activity()).nextStartedActivityForResult
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.intent.action)
        assertTrue(request.intent.getStringExtra(Intent.EXTRA_TITLE)!!.startsWith("mqttabot-log-"))

        val uri = Uri.parse("content://documents/log.txt")
        val out = ByteArrayOutputStream()
        shadowOf(app.contentResolver).registerOutputStream(uri, out)
        shadowOf(activity()).receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(uri))
        node(TAG_LOG_MESSAGE).assertTextEquals(app.getString(R.string.log_saved))
        val text = out.toString()
        assertTrue(text.startsWith("=== MqttABot diagnostic report"))
        assertTrue(text.contains("something happened"))
    }

    @Test
    fun saveFailureAndCancelAreHandled() {
        enableAndLog()
        node(TAG_SAVE_LOG).performClick()
        val request = shadowOf(activity()).nextStartedActivityForResult
        // Cancelled picker: nothing happens.
        shadowOf(activity()).receiveResult(request.intent, Activity.RESULT_CANCELED, null)
        compose.onNodeWithTag(TAG_LOG_MESSAGE).assertDoesNotExist()

        node(TAG_SAVE_LOG).performClick()
        val retry = shadowOf(activity()).nextStartedActivityForResult
        val unwritable = Uri.fromFile(File("/nonexistent/dir/log.txt"))
        shadowOf(activity()).receiveResult(retry.intent, Activity.RESULT_OK, Intent().setData(unwritable))
        assertTrue(node(TAG_LOG_MESSAGE).text().startsWith(app.getString(R.string.log_save_failed, "").trimEnd()))

        // The stream opens but writing fails (e.g. the disk is full); it is still closed.
        node(TAG_SAVE_LOG).performClick()
        val full = shadowOf(activity()).nextStartedActivityForResult
        val uri = Uri.parse("content://documents/full.txt")
        var closed = false
        shadowOf(app.contentResolver).registerOutputStream(
            uri,
            object : OutputStream() {
                override fun write(b: Int) = throw IOException("No space left on device")

                override fun close() {
                    closed = true
                }
            },
        )
        shadowOf(activity()).receiveResult(full.intent, Activity.RESULT_OK, Intent().setData(uri))
        assertTrue(node(TAG_LOG_MESSAGE).text().contains("No space left"))
        assertTrue(closed)
    }

    @Test
    fun sharesTheLogThroughAFileProvider() {
        enableAndLog()
        node(TAG_SHARE_LOG).performClick()
        val chooser = shadowOf(activity()).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        val stream = IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java)!!
        assertEquals("${app.packageName}.logs", stream.authority)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(File(app.cacheDir, "shared/mqttabot-log.txt").readText().contains("something happened"))
    }

    @Test
    fun reportsOnGitHubAndClears() {
        enableAndLog()
        node(TAG_REPORT_ISSUE).performClick()
        val view = shadowOf(activity()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertTrue(view.dataString!!.startsWith(DiagnosticsActivity.ISSUES_URL))
        assertTrue(view.data!!.getQueryParameter("body")!!.contains("diagnostic log"))

        node(TAG_CLEAR_LOG).performClick()
        node(TAG_SAVE_LOG).assertIsNotEnabled().let {}
        compose.onNodeWithTag(TAG_BACK).performClick()
        scenario!!.result
    }
}
