package com.komprisma.imo.core;

/** Lightweight deterministic affect state used only to shape dialogue and strategy. */
public final class IMOEmotionalState {
    public enum Mood { CALM, FOCUSED, CURIOUS, CONCERNED, ALERT, RELIEVED, HAPPY, FRUSTRATED }
    public final Mood mood;
    public final float focus, confidence, concern, frustration, energy;

    public IMOEmotionalState() { this(Mood.CALM, .65f, .75f, 0f, 0f, .8f); }
    public IMOEmotionalState(Mood mood, float focus, float confidence, float concern, float frustration, float energy) {
        this.mood = mood; this.focus = clamp(focus); this.confidence = clamp(confidence);
        this.concern = clamp(concern); this.frustration = clamp(frustration); this.energy = clamp(energy);
    }
    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
    @Override public String toString() {
        return "mood=" + mood + ", focus=" + focus + ", confidence=" + confidence + ", concern=" + concern + ", frustration=" + frustration + ", energy=" + energy;
    }
}
