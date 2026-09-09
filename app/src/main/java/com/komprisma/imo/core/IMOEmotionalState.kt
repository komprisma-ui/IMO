package com.komprisma.imo.core

/** Lightweight deterministic affect state used only to shape dialogue and strategy. */
data class IMOEmotionalState(
    val mood: Mood = Mood.CALM,
    val focus: Float = 0.65f,
    val confidence: Float = 0.75f,
    val concern: Float = 0.0f,
    val frustration: Float = 0.0f,
    val energy: Float = 0.8f
) {
    enum class Mood { CALM, FOCUSED, CURIOUS, CONCERNED, ALERT, RELIEVED, HAPPY, FRUSTRATED }

    fun normalized() = copy(
        focus = focus.coerceIn(0f, 1f), confidence = confidence.coerceIn(0f, 1f),
        concern = concern.coerceIn(0f, 1f), frustration = frustration.coerceIn(0f, 1f),
        energy = energy.coerceIn(0f, 1f)
    )
}

class IMOEmotionEngine {
    private var state = IMOEmotionalState()
    @Synchronized fun snapshot() = state
    @Synchronized fun onUserInput(urgent: Boolean, ambiguous: Boolean) { state = state.copy(
        mood = when { urgent -> IMOEmotionalState.Mood.ALERT; ambiguous -> IMOEmotionalState.Mood.CURIOUS; else -> IMOEmotionalState.Mood.FOCUSED },
        focus = if (urgent) 0.95f else 0.82f,
        concern = if (ambiguous) 0.35f else state.concern * 0.7f
    ).normalized() }
    @Synchronized fun onTaskSuccess() { state = state.copy(mood=IMOEmotionalState.Mood.RELIEVED, confidence=(state.confidence+.06f).coerceAtMost(1f), frustration=(state.frustration-.2f).coerceAtLeast(0f), concern=(state.concern-.15f).coerceAtLeast(0f)).normalized() }
    @Synchronized fun onTaskFailure() { state = state.copy(mood=IMOEmotionalState.Mood.CONCERNED, confidence=(state.confidence-.05f).coerceAtLeast(0f), frustration=(state.frustration+.18f).coerceAtMost(1f), concern=(state.concern+.2f).coerceAtMost(1f)).normalized() }
    @Synchronized fun onRecoverySuccess() { state = state.copy(mood=IMOEmotionalState.Mood.RELIEVED, confidence=(state.confidence+.03f).coerceAtMost(1f), frustration=(state.frustration-.1f).coerceAtLeast(0f)).normalized() }
}
