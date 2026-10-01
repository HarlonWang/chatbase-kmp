package wang.harlon.chatbase.core

import android.util.Log

internal actual fun logWarn(tag: String, message: String, error: Throwable?) {
    Log.w(tag, message, error)
}
