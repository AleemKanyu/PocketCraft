package com.pocketcraft.server.broadcast

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FeedbackPromptPayload(
    val title: String,
    val body: String,
    val ctaLabel: String = "Send feedback"
)

object FeedbackPromptCenter {
    private val _pendingPrompt = MutableStateFlow<FeedbackPromptPayload?>(null)
    val pendingPrompt: StateFlow<FeedbackPromptPayload?> = _pendingPrompt.asStateFlow()

    fun show(prompt: FeedbackPromptPayload) {
        _pendingPrompt.value = prompt
    }

    fun clear() {
        _pendingPrompt.value = null
    }
}
