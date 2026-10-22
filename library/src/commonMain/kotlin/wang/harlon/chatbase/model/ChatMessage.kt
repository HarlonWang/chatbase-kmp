package wang.harlon.chatbase.model

/** 消息角色 */
enum class Role { USER, ASSISTANT }

/**
 * 一条聊天消息。
 *
 * @param content 文本内容；assistant 为 Markdown 源串，渲染时按需解析
 * @param images 用户随消息发送的图片（压缩后的本地缓存文件路径，仅 USER 消息使用）；
 *   UI 直接按路径渲染，发送时由 transport 层读文件转 base64 内嵌
 * @param error 非空表示这条 assistant 消息是一次失败（按 [ChatError.category] 区分展示）
 * @param sources 联网搜索的引用来源（随消息持久化，尾部 SourcesRow 渲染）
 * @param searching 流式过程中「正在搜索」瞬态指示（不持久化）
 * @param generatingImage 流式过程中「正在生成图片」瞬态指示（不持久化）
 * @param model 生成本条消息的模型 id（气泡上标注用），可空
 * @param streaming 内容仍在流式追加（不持久化），渲染层据此处理半截内容
 * @param uiKey 显式指定的 UI 身份，为空时取 [id]，见 [key]
 */
data class ChatMessage(
    val id: Long,
    val role: Role,
    val content: String,
    val images: List<String> = emptyList(),
    val error: ChatError? = null,
    val sources: List<SourceRef> = emptyList(),
    val searching: Boolean = false,
    val generatingImage: Boolean = false,
    val model: String? = null,
    val streaming: Boolean = false,
    val uiKey: Long? = null,
) {
    /**
     * UI 身份（列表 key、查看器定位）。流式占位落库时 id 会换成 store 行 id，key 沿用占位的，
     * 同一条回复从流式到定稿始终是同一个列表项，不会被销毁重建。
     * 不做成带默认值的构造参数：`copy(id = …)` 不会重算默认值，key 会停在旧 id 上。
     */
    val key: Long get() = uiKey ?: id
}
