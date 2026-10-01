package wang.harlon.chatbase.ui

import androidx.compose.runtime.Composable

/** 系统分享面板的平台缝（Android=chooser，iOS=UIActivityViewController）。 */
@Composable
internal expect fun rememberShareText(): (String) -> Unit
