package com.mqttabot.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.mqttabot.AppGraph
import com.mqttabot.R
import com.mqttabot.data.AppLanguage
import com.mqttabot.log.AppLog
import com.mqttabot.log.Diagnostics
import com.mqttabot.ui.theme.setThemedContent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val TAG_LOG_ENABLED = "log_enabled"
const val TAG_LOG_PATH = "log_path"
const val TAG_LOG_SIZE = "log_size"
const val TAG_LOG_MESSAGE = "log_message"
const val TAG_SHARE_LOG = "share_log"
const val TAG_SAVE_LOG = "save_log"
const val TAG_REPORT_ISSUE = "report_issue"
const val TAG_CLEAR_LOG = "clear_log"

/**
 * Diagnostic log: turn it on, reproduce a problem, then save or share the log and attach it to a
 * GitHub issue. The log is size-bounded (see [com.mqttabot.log.LogFile]).
 */
class DiagnosticsActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    private var sizeBytes by mutableLongStateOf(0)
    private var message by mutableStateOf<String?>(null)

    private val saveDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) saveTo(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i(TAG, "Opened")
        setThemedContent {
            val enabled by AppGraph.diagnosticLogging.observeAsState()
            DiagnosticsScreen(
                enabled = enabled,
                path = Diagnostics.displayPath(AppGraph.logFile.current),
                size = stringResource(R.string.log_size, formatSize(sizeBytes), formatSize(AppGraph.logFile.limitBytes)),
                hasLog = sizeBytes > 0,
                message = message,
                onEnabledChange = {
                    AppGraph.setDiagnosticLogging(it)
                    refresh()
                },
                onShare = ::share,
                onSave = { saveDocument.launch("mqttabot-log-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.txt") },
                onReport = ::reportIssue,
                onClear = {
                    AppLog.i(TAG, "Log cleared")
                    AppGraph.logFile.clear()
                    refresh()
                },
                onBack = ::finish,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        sizeBytes = AppGraph.logFile.sizeBytes()
    }

    private fun formatSize(bytes: Long): String = Formatter.formatShortFileSize(this, bytes)

    private fun share() {
        AppLog.i(TAG, "Sharing the log")
        val file = File(cacheDir, "shared/mqttabot-log.txt")
        file.parentFile!!.mkdirs()
        file.writeBytes(AppGraph.logFile.export(Diagnostics.report(this)))
        val uri = FileProvider.getUriForFile(this, "$packageName.logs", file)
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "MqttABot diagnostic log")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, getString(R.string.action_share_log)))
    }

    private fun saveTo(uri: Uri) {
        message =
            try {
                val bytes = AppGraph.logFile.export(Diagnostics.report(this))
                val out = contentResolver.openOutputStream(uri)!!
                try {
                    out.write(bytes)
                } finally {
                    out.close()
                }
                AppLog.i(TAG, "Log saved to $uri")
                getString(R.string.log_saved)
            } catch (e: Exception) {
                AppLog.w(TAG, "Saving the log to $uri failed", e)
                getString(R.string.log_save_failed, e.toString())
            }
    }

    private fun reportIssue() {
        AppLog.i(TAG, "Opening GitHub issues")
        val body =
            "**What happened?**\n\n\n**Steps to reproduce**\n1. \n\n**Environment**\n" +
                "MqttABot ${Diagnostics.appVersion(this)}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
                "${Build.MANUFACTURER} ${Build.MODEL}\n\n" +
                "Please attach the diagnostic log (Diagnostics > Save log).\n"
        val uri =
            ISSUES_URL
                .toUri()
                .buildUpon()
                .appendQueryParameter("body", body)
                .build()
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    companion object {
        const val ISSUES_URL = "https://github.com/264768502/mqttabot/issues/new"
        private const val TAG = "Diagnostics"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DiagnosticsScreen(
    enabled: Boolean,
    path: String,
    size: String,
    hasLog: Boolean,
    message: String?,
    onEnabledChange: (Boolean) -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onReport: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(TAG_BACK)) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ScreenBody(padding) {
            SectionCard(MaterialTheme.colorScheme.surfaceContainerHigh) {
                SwitchRow(stringResource(R.string.log_enable), enabled, onEnabledChange, TAG_LOG_ENABLED)
                Text(
                    stringResource(R.string.log_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            SectionCard(MaterialTheme.colorScheme.surfaceContainer) {
                CardTitle(stringResource(R.string.log_location), R.drawable.ic_bug_report)
                SelectionContainer {
                    Text(
                        path,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 8.dp).testTag(TAG_LOG_PATH),
                    )
                }
                Text(
                    size,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp).testTag(TAG_LOG_SIZE),
                )
                Button(
                    onClick = onSave,
                    enabled = hasLog,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag(TAG_SAVE_LOG),
                ) {
                    ButtonIcon(R.drawable.ic_download)
                    Text(stringResource(R.string.action_save_log))
                }
                FilledTonalButton(onClick = onShare, enabled = hasLog, modifier = Modifier.fillMaxWidth().testTag(TAG_SHARE_LOG)) {
                    ButtonIcon(R.drawable.ic_share)
                    Text(stringResource(R.string.action_share_log))
                }
                OutlinedButton(onClick = onClear, enabled = hasLog, modifier = Modifier.fillMaxWidth().testTag(TAG_CLEAR_LOG)) {
                    ButtonIcon(R.drawable.ic_delete)
                    Text(stringResource(R.string.action_clear_log))
                }
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag(TAG_LOG_MESSAGE),
                    )
                }
            }
            SectionCard(MaterialTheme.colorScheme.surfaceContainer) {
                CardTitle(stringResource(R.string.log_steps_title), R.drawable.ic_info)
                Text(
                    stringResource(R.string.log_steps),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FilledTonalButton(onClick = onReport, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag(TAG_REPORT_ISSUE)) {
                    ButtonIcon(R.drawable.ic_open_in_new)
                    Text(stringResource(R.string.action_report_issue))
                }
            }
        }
    }
}

@Composable
private fun ButtonIcon(icon: Int) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
}
