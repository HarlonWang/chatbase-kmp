package wang.harlon.chatbase.store

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import wang.harlon.chatbase.db.chatDatabase

@Composable
internal actual fun rememberDefaultChatStore(): ChatStore {
    val appContext = LocalContext.current.applicationContext
    return remember {
        RoomChatStore(chatDatabase(appContext), File(appContext.filesDir, "chat_images").absolutePath)
    }
}
