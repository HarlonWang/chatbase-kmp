package wang.harlon.chatbase.engine

/**
 * 在平台允许的后台宽限期内执行 [block]：iOS 切后台后进程很快被挂起，URLSession 随之断流（NSURLError -1005），
 * 包一层 background task 可多争取约 30 秒把回复收完；Android 进程不会被立即挂起，直接执行。
 */
internal expect suspend fun <T> withBackgroundTask(name: String, block: suspend () -> T): T
