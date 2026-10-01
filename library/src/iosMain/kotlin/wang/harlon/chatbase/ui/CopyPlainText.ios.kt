package wang.harlon.chatbase.ui

import androidx.compose.ui.platform.Clipboard
import platform.UIKit.UIPasteboard

internal actual suspend fun copyPlainText(clipboard: Clipboard, text: String) {
    UIPasteboard.generalPasteboard.string = text
}
