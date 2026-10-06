package com.gecko.core.model.chat


/** A null conversation ID belongs to the new-chat composer. */
data class ChatDraft(
    val text: String = "",
    val imageBase64: String? = null,
    val document: DocumentAttachment? = null,
) {
    val isEmpty: Boolean get() = text.isEmpty() && imageBase64 == null && document == null
}

data class DocumentAttachment(val name: String, val text: String, val pageCount: Int?)
