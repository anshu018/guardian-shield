package com.guardianshield.child.utils

object Constants {
    const val CHANNEL_ID = "sys_service"
    const val NOTIFICATION_ID = 1001

    const val SUPABASE_TABLE_LOCATION = "child_location"
    const val SUPABASE_TABLE_SOS = "sos_events"
    const val SUPABASE_TABLE_COMMANDS = "remote_commands"
    const val SUPABASE_TABLE_APP_USAGE = "app_usage"
    const val SUPABASE_TABLE_FAMILIES = "families"
    const val SUPABASE_TABLE_CHILDREN = "children"

    const val LOCATION_INTERVAL_NORMAL = 10_000L
    const val LOCATION_INTERVAL_SOS = 5_000L
    const val LOCATION_INTERVAL_STATIONARY = 30_000L

    const val STATIONARY_THRESHOLD_METERS = 20f
    const val STATIONARY_READING_COUNT = 3

    const val SECRET_DIALER_CODE = "*#1234#"

    const val RESTART_REQUEST_CODE = 9001
}
