package com.mqttabot.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mqttabot.AppGraph
import com.mqttabot.R
import com.mqttabot.log.AppLog
import com.mqttabot.log.Diagnostics
import com.mqttabot.mqtt.BrokerConfig
import com.mqttabot.mqtt.BrokerPresets
import com.mqttabot.mqtt.ConfigError
import com.mqttabot.ui.theme.setThemedContent

const val TAG_BACK = "back"
const val TAG_PRESET = "preset"

/** Dropdown entries are tagged "$TAG_PRESET_OPTION-<index>"; 0 is "Custom". */
const val TAG_PRESET_OPTION = "preset_option"
const val TAG_HOST = "host"
const val TAG_PORT = "port"
const val TAG_TLS = "tls"
const val TAG_USERNAME = "username"
const val TAG_PASSWORD = "password"
const val TAG_PASSWORD_TOGGLE = "password_toggle"
const val TAG_CLIENT_ID = "client_id"
const val TAG_KEEP_ALIVE = "keep_alive"
const val TAG_CLEAN_SESSION = "clean_session"
const val TAG_AUTO_START = "auto_start"
const val TAG_SAVE = "save"

private const val TAG = "BrokerSettings"

/** Broker endpoint, credentials and connection options. */
class BrokerSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i(TAG, "Opened")
        setThemedContent { BrokerSettingsScreen(onDone = ::finish) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrokerSettingsScreen(onDone: () -> Unit) {
    val saved = remember { AppGraph.settings.loadBroker() }
    // Ports and timeouts stay ASCII digits so they parse back.
    var host by rememberSaveable { mutableStateOf(saved.host) }
    var port by rememberSaveable { mutableStateOf(saved.port.toString()) }
    var tls by rememberSaveable { mutableStateOf(saved.tls) }
    var username by rememberSaveable { mutableStateOf(saved.username) }
    var password by rememberSaveable { mutableStateOf(saved.password) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var clientId by rememberSaveable { mutableStateOf(saved.clientId) }
    var keepAlive by rememberSaveable { mutableStateOf(saved.keepAliveSeconds.toString()) }
    var cleanSession by rememberSaveable { mutableStateOf(saved.cleanSession) }
    var autoStart by rememberSaveable { mutableStateOf(AppGraph.settings.autoStartOnBoot) }
    // Preset whose values are in the form; 0 = custom.
    var preset by rememberSaveable { mutableIntStateOf(BrokerPresets.indexOf(saved) + 1) }
    var error by rememberSaveable { mutableStateOf<ConfigError?>(null) }

    fun applyPreset(position: Int) {
        preset = position
        if (position == 0) return
        val p = BrokerPresets.all[position - 1]
        AppLog.i(TAG, "Preset chosen: ${p.name}")
        host = p.host
        port = p.port.toString()
        tls = p.tls
        // Presets without credentials keep the user's.
        if (p.username.isNotEmpty()) {
            username = p.username
            password = p.password
        }
    }

    fun save() {
        val config =
            BrokerConfig(
                host = host.trim(),
                port = port.toIntOrNull() ?: 0,
                tls = tls,
                username = username,
                password = password,
                clientId = clientId.trim(),
                keepAliveSeconds = keepAlive.toIntOrNull() ?: -1,
                cleanSession = cleanSession,
            )
        error = config.validate()
        if (error != null) {
            AppLog.i(TAG, "Not saved: invalid $error")
            return
        }
        AppLog.i(TAG, "Saved: ${Diagnostics.describe(config)}, start on boot: $autoStart")
        AppGraph.settings.saveBroker(config)
        AppGraph.settings.autoStartOnBoot = autoStart
        AppGraph.controller?.reconnect()
        onDone()
    }

    @Composable
    fun errorFor(
        kind: ConfigError,
        message: Int,
    ): String? = if (error == kind) stringResource(message) else null

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.broker_settings)) },
                navigationIcon = {
                    IconButton(onClick = onDone, modifier = Modifier.testTag(TAG_BACK)) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ScreenBody(padding) {
            SectionTitle(stringResource(R.string.section_server))
            PresetMenu(preset, ::applyPreset)
            Row {
                FormField(
                    host,
                    { host = it },
                    stringResource(R.string.hint_host),
                    TAG_HOST,
                    modifier = Modifier.weight(1f),
                    error = errorFor(ConfigError.HOST, R.string.error_host),
                    keyboardType = KeyboardType.Uri,
                )
                FormField(
                    port,
                    { port = it },
                    stringResource(R.string.hint_port),
                    TAG_PORT,
                    modifier = Modifier.padding(start = 8.dp).width(112.dp),
                    error = errorFor(ConfigError.PORT, R.string.error_port),
                    keyboardType = KeyboardType.Number,
                )
            }
            SwitchRow(stringResource(R.string.label_tls), tls, { tls = it }, TAG_TLS)

            SectionTitle(stringResource(R.string.section_auth))
            FormField(
                username,
                { username = it },
                stringResource(R.string.hint_username),
                TAG_USERNAME,
                error = errorFor(ConfigError.USERNAME, R.string.error_username),
            )
            FormField(
                password,
                { password = it },
                stringResource(R.string.hint_password),
                TAG_PASSWORD,
                keyboardType = KeyboardType.Password,
                leadingIcon = { Icon(painterResource(R.drawable.ic_key), contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }, modifier = Modifier.testTag(TAG_PASSWORD_TOGGLE)) {
                        Icon(
                            painterResource(if (showPassword) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                            contentDescription =
                                stringResource(
                                    if (showPassword) R.string.action_hide_password else R.string.action_show_password,
                                ),
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            )

            SectionTitle(stringResource(R.string.section_session))
            FormField(clientId, { clientId = it }, stringResource(R.string.hint_client_id), TAG_CLIENT_ID)
            FormField(
                keepAlive,
                { keepAlive = it },
                stringResource(R.string.hint_keep_alive),
                TAG_KEEP_ALIVE,
                error = errorFor(ConfigError.KEEP_ALIVE, R.string.error_keep_alive),
                keyboardType = KeyboardType.Number,
            )
            SwitchRow(stringResource(R.string.label_clean_session), cleanSession, { cleanSession = it }, TAG_CLEAN_SESSION)
            SwitchRow(stringResource(R.string.label_auto_start), autoStart, { autoStart = it }, TAG_AUTO_START)

            Button(
                onClick = ::save,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 16.dp,
                        ).heightIn(min = ButtonDefaults.MediumContainerHeight)
                        .testTag(TAG_SAVE),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetMenu(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val names = listOf(stringResource(R.string.preset_custom)) + BrokerPresets.all.map { it.name }
    var expanded by remember { mutableStateOf(false) }
    val close = { expanded = false }
    // Read-only field showing the selection; state-based, so there is no edit callback.
    val field = rememberTextFieldState()
    SideEffect { if (field.text != names[selected]) field.setTextAndPlaceCursorAtEnd(names[selected]) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            state = field,
            readOnly = true,
            lineLimits = TextFieldLineLimits.SingleLine,
            label = { Text(stringResource(R.string.label_preset)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_dns), contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).testTag(TAG_PRESET),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = close) {
            names.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        close()
                        onSelect(index)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    modifier = Modifier.testTag("$TAG_PRESET_OPTION-$index"),
                )
            }
        }
    }
}
