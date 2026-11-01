package com.mqttabot.testutil

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import com.mqttabot.ui.TAG_MAIN_LIST

/** Scrolls the main screen's lazy list to the node tagged [tag] and returns it. */
fun SemanticsNodeInteractionsProvider.onMain(tag: String): SemanticsNodeInteraction {
    onNodeWithTag(TAG_MAIN_LIST).performScrollToNode(hasTestTag(tag))
    return onNodeWithTag(tag)
}

/** Text of a text field, or of a plain text node. */
fun SemanticsNodeInteraction.text(): String {
    val config = fetchSemanticsNode().config
    return config.getOrNull(SemanticsProperties.EditableText)?.text
        ?: config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }
}
