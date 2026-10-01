package wang.harlon.chatbase.core

internal actual fun logWarn(tag: String, message: String, error: Throwable?) {
    println("W/$tag: $message${error?.let { " $it" } ?: ""}")
}
