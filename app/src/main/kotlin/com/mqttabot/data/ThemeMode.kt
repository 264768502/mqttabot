package com.mqttabot.data

import androidx.annotation.StringRes
import com.mqttabot.R
import java.util.Calendar
import java.util.TimeZone

/** Light or dark appearance chosen by the user. */
enum class ThemeMode(
    @param:StringRes val label: Int,
) {
    /** Dark from [NIGHT_START_HOUR] until [DAY_START_HOUR] local time. */
    AUTO(R.string.theme_auto),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
    ;

    companion object {
        const val DAY_START_HOUR = 7
        const val NIGHT_START_HOUR = 19

        fun isNight(
            millis: Long,
            zone: TimeZone = TimeZone.getDefault(),
        ): Boolean {
            val hour = Calendar.getInstance(zone).apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)
            return hour !in DAY_START_HOUR until NIGHT_START_HOUR
        }

        /** Milliseconds from [millis] until auto mode next switches between light and dark. */
        fun millisUntilSwitch(
            millis: Long,
            zone: TimeZone = TimeZone.getDefault(),
        ): Long {
            val next = Calendar.getInstance(zone).apply { timeInMillis = millis }
            val hour = next.get(Calendar.HOUR_OF_DAY)
            next.set(Calendar.HOUR_OF_DAY, if (hour in DAY_START_HOUR until NIGHT_START_HOUR) NIGHT_START_HOUR else DAY_START_HOUR)
            next.set(Calendar.MINUTE, 0)
            next.set(Calendar.SECOND, 0)
            next.set(Calendar.MILLISECOND, 0)
            if (next.timeInMillis <= millis) next.add(Calendar.DAY_OF_MONTH, 1)
            return next.timeInMillis - millis
        }
    }
}
