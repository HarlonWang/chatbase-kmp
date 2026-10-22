package wang.harlon.chatbase.engine

internal actual suspend fun <T> withBackgroundTask(name: String, block: suspend () -> T): T = block()
