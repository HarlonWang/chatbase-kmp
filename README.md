# chatbase-kmp

> Compose Multiplatform chat UI and client for [chatbase](https://github.com/HarlonWang/chatbase) — streaming replies, model picker, voice input, image attachments, image generation, web search and local history.

**English** | [简体中文](README.zh-CN.md)

[![Maven Central](https://img.shields.io/maven-central/v/wang.harlon/chatbase-kmp)](https://central.sonatype.com/artifact/wang.harlon/chatbase-kmp)
[![license](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

The server half is [chatbase](https://github.com/HarlonWang/chatbase), an npm library that runs inside your own Cloudflare Worker. This library is the whole client side: the screen, the SSE engine, the Room-backed history and the wire protocol. Your app provides sign-in, the Pro decision and a few preferences through one interface; the SDK never sees a user or a subscription.

| Platform | Status |
|---|---|
| Android | In production ([TrendingAI](https://github.com/HarlonWang/TrendingAI)) |
| iOS | In production, consumed from a Kotlin Multiplatform host |

## What you get

- **A full chat screen in one call.** `ChatScreen(onBack = ...)` renders threads, the input bar, the model picker, Markdown with code highlighting, images and the quota / Pro gates. The host only paints the shell around it.
- **The protocol stays in the SDK.** Chat, transcribe, models and quota are all spoken here against the `/api/chat/*` endpoints that chatbase serves. The host never builds a request.
- **Local history that survives updates.** Threads and messages live in a Room database (`chat.db`) with versioned schema and migration tests.
- **Identity and billing stay yours.** `ChatHost` asks the app whether the user is signed in and whether they are Pro, and asks it to open the sign-in and paywall screens. The SDK does not store tokens or know about products.
- **Analytics as events, not as a dependency.** Requests, completions and voice input are reported as `ChatAiEvent`; the host maps them into its own vocabulary.

## Quick start

**1. Add the dependency.** The SDK brings its own Ktor engine per platform.

```kotlin
dependencies {
    implementation("wang.harlon:chatbase-kmp:<version>")
}
```

**2. Implement `ChatHost` and install it before any chat UI is reached**, in `Application.onCreate` on Android and in the `MainViewController` factory on iOS.

```kotlin
object MyChatHost : ChatHost {
    override val apiBaseUrl = "https://api.example.com/api"

    override val canSignIn = true
    override fun isLoggedInNow() = auth.state.value is LoggedIn
    override val isLoggedIn = auth.state.map { it is LoggedIn }
    override fun signIn(source: String) = auth.signIn(source)
    override fun openPaywall(source: String) = Paywall.open(source)
    override fun reportGeneratedImage(imageUrl: String) = Feedback.open(imageUrl)

    override fun currentIsPro() = settings.isPro
    override val isPro = settings.isProFlow

    override val chatModelChoice = settings.chatModelFlow
    override fun currentChatModelChoice() = settings.chatModel
    override fun pinChatModel(id: String) = settings.setChatModel(id)
    override fun followServerDefault() = settings.setChatModel(FOLLOW_SERVER_DEFAULT)

    override fun imagesMaxCount() = 9
    override fun imagesPerImageJpegKb() = 300
    override fun installId() = settings.installId
    override suspend fun requestLang() = if (settings.language == "zh") "zh" else "en"

    override fun configureHttpAuth(config: HttpClientConfig<*>) = config.installAuth(auth)
    override fun onAiEvent(event: ChatAiEvent) = analytics.track(event)
    override val proBadge: (@Composable () -> Unit)? = { ProBadge() }
}

fun installChatHost() { chatHost = MyChatHost }
```

**3. Warm the model catalog at startup and show the screen.**

```kotlin
ChatModelsProvider.warmUp(appScope)

ChatScreen(
    onBack = { navController.popBackStack() },
    suggestions = listOf(ChatSuggestion(label = "What can you do?", prompt = "What can you do?")),
)
```

A minimal host that runs without any backend is in [`sample/`](sample/src/main/kotlin/wang/harlon/chatbase/sample/DemoChatHost.kt).

## Wire protocol

Documented with the server: [chatbase `docs/chat-protocol.md`](https://github.com/HarlonWang/chatbase/blob/main/docs/chat-protocol.md). Endpoints are relative to `ChatHost.apiBaseUrl`.

## Building

- Android: `./gradlew :library:testAndroidHostTest` runs the common and Android tests under Robolectric.
- iOS: Markdown parsing binds [cmark-gfm](https://github.com/apple/swift-cmark). `library/native/build-cmark.sh` builds the static libraries with CMake on first use; they are packed into the klib, so consumers need nothing extra.
- Consuming the local source tree from an app: add `chatbase-kmp.dir=<path>` to the app's `local.properties` if the app supports composite builds through `gradle/composite-substitutions`.

## License

MIT. cmark-gfm is bundled under its own license, see `library/native/cmark-gfm-COPYING`.
