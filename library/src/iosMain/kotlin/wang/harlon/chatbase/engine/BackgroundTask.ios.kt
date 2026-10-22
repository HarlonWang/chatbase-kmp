package wang.harlon.chatbase.engine

import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicLong
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid

/**
 * 宽限期耗尽时系统回调 expiration handler，必须在其中 end，否则进程被杀；
 * 正常结束与到期可能先后发生，用 [ended] 保证只 end 一次。
 * 到期回调可能早于 taskId 赋值执行（此时 end 不了），故先记 [expired]，赋值后补 end。
 */
internal actual suspend fun <T> withBackgroundTask(name: String, block: suspend () -> T): T {
    val app = UIApplication.sharedApplication
    val invalid = UIBackgroundTaskInvalid.toLong()
    val taskId = AtomicLong(invalid)
    val expired = AtomicInt(0)
    val ended = AtomicInt(0)
    fun end() {
        val id = taskId.value
        if (id != invalid && ended.compareAndSet(0, 1)) app.endBackgroundTask(id.toULong())
    }
    taskId.value = app.beginBackgroundTaskWithName(name) {
        expired.value = 1
        end()
    }.toLong()
    if (expired.value == 1) end()
    try {
        return block()
    } finally {
        end()
    }
}
