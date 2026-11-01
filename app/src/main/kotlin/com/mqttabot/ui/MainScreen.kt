package com.mqttabot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import com.mqttabot.AppGraph
import com.mqttabot.R
import com.mqttabot.data.AppLanguage
import com.mqttabot.data.Subscription
import com.mqttabot.data.ThemeMode
import com.mqttabot.log.AppLog
import com.mqttabot.mqtt.ReceivedMessage
import com.mqttabot.mqtt.Topics
import com.mqttabot.service.MqttService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val TAG_MAIN_LIST = "main_list"
const val TAG_SETTINGS = "open_settings"
const val TAG_THEME = "theme"
const val TAG_LANGUAGE = "language"
const val TAG_DIAGNOSTICS = "open_diagnostics"

/** Radio rows of the theme dialog are tagged "$TAG_THEME_OPTION-<ThemeMode name>". */
const val TAG_THEME_OPTION = "theme_option"

/** Radio rows of the language dialog are tagged "$TAG_LANGUAGE_OPTION-<tag>"; "" is automatic. */
const val TAG_LANGUAGE_OPTION = "language_option"
const val TAG_STATUS = "status"
const val TAG_STATUS_DETAIL = "status_detail"
const val TAG_STATUS_PROGRESS = "status_progress"
const val TAG_BROKER = "broker"
const val TAG_SERVICE = "service"
const val TAG_BACKGROUND = "background"
const val TAG_ALLOW_BACKGROUND = "allow_background"
const val TAG_TOPIC = "topic"
const val TAG_PAYLOAD = "payload"
const val TAG_QOS = "qos"
const val TAG_RETAIN = "retain"
const val TAG_SEND = "send"
const val TAG_PUBLISH_RESULT = "publish_result"
const val TAG_FILTER = "filter"
const val TAG_FILTER_QOS = "filter_qos"
const val TAG_ADD_SUBSCRIPTION = "add_subscription"
const val TAG_NO_SUBSCRIPTIONS = "no_subscriptions"
const val TAG_SUBSCRIPTION = "subscription"
const val TAG_REMOVE_SUBSCRIPTION = "remove_subscription"
const val TAG_CLEAR_MESSAGES = "clear_messages"
const val TAG_NO_MESSAGES = "no_messages"
const val TAG_MESSAGE = "message"

private const val TAG = "MainScreen"

/** Last manual publish; the message takes the topic or error as its argument. */
enum class PublishOutcome(
    val message: Int,
) {
    SENDING(R.string.publish_sending),
    OK(R.string.publish_ok),
    FAILED(R.string.publish_failed),
}

/** Why a permission or setting is needed; shown after the user declines it. */
enum class Explanation(
    val title: Int,
    val message: Int,
) {
    NOTIFICATIONS(R.string.rationale_notifications_title, R.string.rationale_notifications),
    BACKGROUND(R.string.rationale_background_title, R.string.rationale_background),
}

/** Status, background behavior, manual publish, subscriptions and received messages. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(
    background: BackgroundMode,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onChooseLanguage: (String) -> Unit,
    onToggleService: () -> Unit,
    onAllowBackground: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val messages by AppGraph.messageLog.updates.observeAsState()
    var brokerUri by remember { mutableStateOf("") }
    var subscriptions by remember { mutableStateOf(emptyList<Subscription>()) }
    var choosingTheme by rememberSaveable { mutableStateOf(false) }
    var choosingLanguage by rememberSaveable { mutableStateOf(false) }
    // Settings may have changed in another screen or in Tasker.
    LifecycleStartEffect(Unit) {
        brokerUri = AppGraph.settings.loadBroker().serverUri
        subscriptions = AppGraph.settings.subscriptions()
        onStopOrDispose {}
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { choosingTheme = true }, modifier = Modifier.testTag(TAG_THEME)) {
                        Icon(painterResource(R.drawable.ic_contrast), contentDescription = stringResource(R.string.theme_title))
                    }
                    IconButton(onClick = { choosingLanguage = true }, modifier = Modifier.testTag(TAG_LANGUAGE)) {
                        Icon(painterResource(R.drawable.ic_language), contentDescription = stringResource(R.string.language_title))
                    }
                    IconButton(onClick = onOpenDiagnostics, modifier = Modifier.testTag(TAG_DIAGNOSTICS)) {
                        Icon(painterResource(R.drawable.ic_bug_report), contentDescription = stringResource(R.string.diagnostics_title))
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag(TAG_SETTINGS)) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.broker_settings))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Below the app bar, so items scrolled into view are never hidden under it.
                    .padding(top = padding.calculateTopPadding())
                    .consumeWindowInsets(padding)
                    .imePadding()
                    .testTag(TAG_MAIN_LIST),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Segments of the message list sit 2dp apart; cards add their own larger gap.
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { CardSlot { StatusCard(brokerUri, onToggleService) } }
            item { CardSlot { BackgroundCard(background, onAllowBackground) } }
            item { CardSlot { PublishCard() } }
            item {
                CardSlot { SubscriptionsCard(subscriptions) { subscriptions = AppGraph.settings.subscriptions() } }
            }
            item { MessagesHeader(empty = messages.isEmpty()) }
            itemsIndexed(messages) { index, message -> MessageItem(message, last = index == messages.lastIndex) }
        }
    }
    if (choosingTheme) {
        val current by AppGraph.themeMode.observeAsState()
        ChoiceDialog(
            title = stringResource(R.string.theme_title),
            options = ThemeMode.entries,
            selected = current,
            label = { stringResource(it.label) },
            tag = { "$TAG_THEME_OPTION-${it.name}" },
            onChoose = AppGraph::setThemeMode,
            onDismiss = { choosingTheme = false },
        )
    }
    if (choosingLanguage) {
        val context = LocalContext.current
        // Languages are listed in their own names; automatic in the current one.
        val auto = stringResource(R.string.language_auto)
        ChoiceDialog(
            title = stringResource(R.string.language_title),
            options = listOf(AppLanguage.AUTO) + AppLanguage.choices.keys,
            selected = remember { AppLanguage.current(context) },
            label = { AppLanguage.choices[it] ?: auto },
            tag = { "$TAG_LANGUAGE_OPTION-$it" },
            onChoose = onChooseLanguage,
            onDismiss = { choosingLanguage = false },
        )
    }
}

/** Radio list of [options]; choosing one closes the dialog. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    tag: (T) -> String,
    onChoose: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                for (option in options) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .selectable(selected = option == selected, role = Role.RadioButton) {
                                    onDismiss()
                                    onChoose(option)
                                }.testTag(tag(option)),
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(label(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** A card followed by the gap to the next one. */
@Composable
private fun CardSlot(card: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        card()
        Spacer(Modifier.height(10.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusCard(
    brokerUri: String,
    onToggleService: () -> Unit,
) {
    val status by AppGraph.status.observeAsState()
    val style = status.state.style
    val (container, onContainer) = status.state.colors()
    SectionCard(container = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(container),
            ) {
                if (style.busy) {
                    ContainedLoadingIndicator(modifier = Modifier.size(48.dp).testTag(TAG_STATUS_PROGRESS))
                } else {
                    Icon(painterResource(style.icon), contentDescription = null, tint = onContainer)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.section_status),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(style.headline),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(TAG_STATUS),
                )
                SelectionContainer {
                    Text(
                        brokerUri,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(TAG_BROKER),
                    )
                }
            }
        }
        if (style.showDetail) {
            Text(
                status.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp).testTag(TAG_STATUS_DETAIL),
            )
        }
        val running = MqttService.isRunning
        Button(
            onClick = onToggleService,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 20.dp,
                    ).heightIn(min = ButtonDefaults.MediumContainerHeight)
                    .testTag(TAG_SERVICE),
            contentPadding = ButtonDefaults.MediumContentPadding,
        ) {
            Icon(
                painterResource(if (running) R.drawable.ic_stop else R.drawable.ic_play_arrow),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.MediumIconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.MediumIconSpacing))
            Text(stringResource(if (running) R.string.action_stop_service else R.string.action_start_service))
        }
    }
}

@Composable
private fun BackgroundCard(
    mode: BackgroundMode,
    onAllow: () -> Unit,
) {
    val unrestricted = mode == BackgroundMode.UNRESTRICTED
    SectionCard(MaterialTheme.colorScheme.surfaceContainer) {
        CardTitle(stringResource(R.string.section_background), R.drawable.ic_battery_android_full)
        Text(
            stringResource(mode.label),
            style = MaterialTheme.typography.titleMedium,
            color = if (unrestricted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 12.dp).testTag(TAG_BACKGROUND),
        )
        Text(
            stringResource(if (unrestricted) R.string.background_hint_ok else R.string.background_hint_limited),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (!unrestricted) {
            FilledTonalButton(onClick = onAllow, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag(TAG_ALLOW_BACKGROUND)) {
                Text(stringResource(R.string.action_allow_background))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PublishCard() {
    val context = LocalContext.current
    var topic by rememberSaveable { mutableStateOf("") }
    var payload by rememberSaveable { mutableStateOf("") }
    var qos by rememberSaveable { mutableIntStateOf(0) }
    var retain by rememberSaveable { mutableStateOf(false) }
    var topicError by rememberSaveable { mutableStateOf(false) }
    var outcome by rememberSaveable { mutableStateOf<PublishOutcome?>(null) }
    var outcomeDetail by rememberSaveable { mutableStateOf("") }

    fun publish() {
        val topicText = topic.trim()
        topicError = !Topics.isValidTopicName(topicText)
        if (topicError) {
            AppLog.i(TAG, "Publish rejected: invalid topic ${AppLog.preview(topicText)}")
            return
        }
        AppLog.i(TAG, "Publish requested from the main screen")
        outcome = PublishOutcome.SENDING
        val (payloadText, qosLevel, retained) = Triple(payload, qos, retain)
        AppGraph.ioExecutor.execute {
            val (result, detail) =
                try {
                    AppGraph.publisher().publish(topicText, payloadText, qosLevel, retained)
                    PublishOutcome.OK to topicText
                } catch (e: Exception) {
                    AppLog.w(TAG, "Publish from the main screen failed", e)
                    PublishOutcome.FAILED to e.toString()
                }
            ContextCompat.getMainExecutor(context).execute {
                outcome = result
                outcomeDetail = detail
            }
        }
    }

    SectionCard(MaterialTheme.colorScheme.surfaceContainer) {
        CardTitle(stringResource(R.string.section_publish), R.drawable.ic_send)
        FormField(
            topic,
            { topic = it },
            stringResource(R.string.hint_topic),
            TAG_TOPIC,
            modifier = Modifier.padding(top = 12.dp),
            error = if (topicError) stringResource(R.string.error_topic) else null,
        )
        FormField(payload, { payload = it }, stringResource(R.string.hint_payload), TAG_PAYLOAD, singleLine = false)
        QosSelector(qos, { qos = it }, TAG_QOS, Modifier.padding(top = 12.dp))
        SwitchRow(stringResource(R.string.label_retain), retain, { retain = it }, TAG_RETAIN)
        Button(
            onClick = ::publish,
            enabled = outcome != PublishOutcome.SENDING,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .heightIn(min = ButtonDefaults.MediumContainerHeight)
                    .testTag(TAG_SEND),
            contentPadding = ButtonDefaults.MediumContentPadding,
        ) {
            Icon(painterResource(R.drawable.ic_send), contentDescription = null, modifier = Modifier.size(ButtonDefaults.MediumIconSize))
            Spacer(Modifier.width(ButtonDefaults.MediumIconSpacing))
            Text(stringResource(R.string.action_send))
        }
        outcome?.let {
            Text(
                stringResource(it.message, outcomeDetail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag(TAG_PUBLISH_RESULT),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubscriptionsCard(
    subscriptions: List<Subscription>,
    onChanged: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("") }
    var qos by rememberSaveable { mutableIntStateOf(0) }
    var filterError by rememberSaveable { mutableStateOf(false) }

    fun add() {
        val filterText = filter.trim()
        filterError = !Topics.isValidFilter(filterText)
        if (filterError) {
            AppLog.i(TAG, "Subscription rejected: invalid filter ${AppLog.preview(filterText)}")
            return
        }
        val subscription = Subscription(filterText, qos)
        AppLog.i(TAG, "Subscription added: $filterText (QoS $qos)")
        AppGraph.settings.addSubscription(subscription)
        AppGraph.controller?.subscribe(subscription)
        filter = ""
        onChanged()
    }

    SectionCard(MaterialTheme.colorScheme.surfaceContainer) {
        CardTitle(stringResource(R.string.section_subscriptions), R.drawable.ic_tag)
        FormField(
            filter,
            { filter = it },
            stringResource(R.string.hint_filter),
            TAG_FILTER,
            modifier = Modifier.padding(top = 12.dp),
            error = if (filterError) stringResource(R.string.error_filter) else null,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            QosSelector(qos, { qos = it }, TAG_FILTER_QOS, Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(onClick = ::add, modifier = Modifier.testTag(TAG_ADD_SUBSCRIPTION)) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.action_add))
            }
        }
        if (subscriptions.isEmpty()) {
            EmptyText(stringResource(R.string.no_subscriptions), TAG_NO_SUBSCRIPTIONS)
        }
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (s in subscriptions) {
                SubscriptionItem(s) {
                    AppLog.i(TAG, "Subscription removed: ${s.filter}")
                    AppGraph.settings.removeSubscription(s.filter)
                    AppGraph.controller?.unsubscribe(s.filter)
                    onChanged()
                }
            }
        }
    }
}

@Composable
private fun SubscriptionItem(
    subscription: Subscription,
    onRemove: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().testTag(TAG_SUBSCRIPTION),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp)) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(subscription.filter, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
                Text(
                    stringResource(R.string.qos_value, subscription.qos),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.testTag(TAG_REMOVE_SUBSCRIPTION)) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.action_remove))
            }
        }
    }
}

// Received messages form a segmented list: the header is the first segment, messages follow.
private val SegmentLarge = 28.dp
private val SegmentSmall = 4.dp

@Composable
private fun MessagesHeader(empty: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape =
            RoundedCornerShape(
                SegmentLarge,
                SegmentLarge,
                if (empty) SegmentLarge else SegmentSmall,
                if (empty) SegmentLarge else SegmentSmall,
            ),
        modifier = Modifier.contentWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            CardTitle(stringResource(R.string.section_messages), R.drawable.ic_inbox) {
                TextButton(onClick = ::clearMessages, modifier = Modifier.testTag(TAG_CLEAR_MESSAGES)) {
                    Icon(
                        painterResource(R.drawable.ic_delete_sweep),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.action_clear))
                }
            }
            if (empty) EmptyText(stringResource(R.string.no_messages), TAG_NO_MESSAGES)
        }
    }
}

private fun clearMessages() {
    AppLog.i(TAG, "Received messages cleared")
    AppGraph.messageLog.clear()
}

@Composable
private fun MessageItem(
    message: ReceivedMessage,
    last: Boolean,
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val bottom = if (last) SegmentLarge else SegmentSmall
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(SegmentSmall, SegmentSmall, bottom, bottom),
        modifier = Modifier.contentWidth().testTag(TAG_MESSAGE),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row {
                Text(message.topic, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.message_meta, timeFormat.format(Date(message.timestamp)), message.qos),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SelectionContainer {
                Text(
                    message.payload,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyText(
    text: String,
    tag: String,
) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag(tag),
    )
}

/** Explains a declined permission or setting and offers to open the system screen for it. */
@Composable
fun ExplanationDialog(
    explanation: Explanation,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(explanation.title)) },
        text = { Text(stringResource(explanation.message)) },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.action_open_settings)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) } },
    )
}
