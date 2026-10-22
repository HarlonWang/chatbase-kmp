package wang.harlon.chatbase.engine

import kotlin.concurrent.AtomicInt
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid

/**
 * 宽限期耗尽时系统回调 expiration handler，必须在其中 end，否则进程被杀；
 * 正常结束与到期可能先后发生，用 [AtomicInt] 保证只 end 一次。
 */
internal actual suspend fun <T> withBackgroundTask(name: String, block: suspend () -> T): T {
    val app = UIApplication.sharedApplication
    val ended = AtomicInt(0)
    var taskId: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid
    fun end() {
        if (taskId != UIBackgroundTaskInvalid && ended.compareAndSet(0, 1)) app.endBackgroundTask(taskId)
    }
    taskId = app.beginBackgroundTaskWithName(name) { end() }
    try {
        return block()
    } finally {
        end()
    }
}
