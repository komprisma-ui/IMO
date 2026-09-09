package com.komprisma.imo.core;

/** Deterministic simulated affect; not consciousness or a claim of real emotion. */
public final class IMOEmotionEngine {
    private IMOEmotionalState state = new IMOEmotionalState();
    public synchronized IMOEmotionalState snapshot() { return state; }
    public synchronized void onUserInput(boolean urgent, boolean ambiguous) {
        state = new IMOEmotionalState(
            urgent ? IMOEmotionalState.Mood.ALERT : (ambiguous ? IMOEmotionalState.Mood.CURIOUS : IMOEmotionalState.Mood.FOCUSED),
            urgent ? .95f : .82f, state.confidence,
            ambiguous ? .35f : state.concern * .7f, state.frustration, state.energy);
    }
    public synchronized void onTaskSuccess() {
        state = new IMOEmotionalState(IMOEmotionalState.Mood.RELIEVED, state.focus,
            Math.min(1f, state.confidence + .06f), Math.max(0f, state.concern - .15f),
            Math.max(0f, state.frustration - .2f), state.energy);
    }
    public synchronized void onTaskFailure() {
        state = new IMOEmotionalState(IMOEmotionalState.Mood.CONCERNED, state.focus,
            Math.max(0f, state.confidence - .05f), Math.min(1f, state.concern + .2f),
            Math.min(1f, state.frustration + .18f), state.energy);
    }
    public synchronized void onRecoverySuccess() {
        state = new IMOEmotionalState(IMOEmotionalState.Mood.RELIEVED, state.focus,
            Math.min(1f, state.confidence + .03f), state.concern,
            Math.max(0f, state.frustration - .1f), state.energy);
    }
}
