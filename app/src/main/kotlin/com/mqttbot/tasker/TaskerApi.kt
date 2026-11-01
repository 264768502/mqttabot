package com.mqttbot.tasker

/** Locale / Tasker plugin protocol constants (see TaskerPlugin.java). */
object TaskerApi {
    const val ACTION_EDIT_SETTING = "com.twofortyfouram.locale.intent.action.EDIT_SETTING"
    const val ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"
    const val ACTION_EDIT_EVENT = "net.dinglisch.android.tasker.ACTION_EDIT_EVENT"
    const val ACTION_QUERY_CONDITION = "com.twofortyfouram.locale.intent.action.QUERY_CONDITION"
    const val ACTION_REQUEST_QUERY = "com.twofortyfouram.locale.intent.action.REQUEST_QUERY"

    const val EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    const val EXTRA_STRING_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"
    const val EXTRA_ACTIVITY = "com.twofortyfouram.locale.intent.extra.ACTIVITY"

    const val EXTRA_PASS_THROUGH_DATA = "net.dinglisch.android.tasker.extras.PASS_THROUGH_DATA"
    const val KEY_PASS_THROUGH_MESSAGE_ID = "net.dinglisch.android.tasker.MESSAGE_ID"
    const val EXTRA_VARIABLES = "net.dinglisch.android.tasker.extras.VARIABLES"
    const val KEY_VARIABLE_REPLACE_KEYS = "net.dinglisch.android.tasker.extras.VARIABLE_REPLACE_KEYS"
    const val EXTRA_RELEVANT_VARIABLES = "net.dinglisch.android.tasker.RELEVANT_VARIABLES"

    const val RESULT_CONDITION_SATISFIED = 16
    const val RESULT_CONDITION_UNSATISFIED = 17
    const val RESULT_CONDITION_UNKNOWN = 18

    // Local variables set by the "MQTT message received" event.
    const val VAR_TOPIC = "%mqtt_topic"
    const val VAR_PAYLOAD = "%mqtt_payload"
    const val VAR_QOS = "%mqtt_qos"
    const val VAR_RETAINED = "%mqtt_retained"
    const val VAR_RECEIVED_AT = "%mqtt_received_at"
}
