package com.komprisma.imo.core

/** Bounded conversational/task context. Secrets are redacted before storage. */
class IMOContextMemory(private val maxEvents: Int = 32) {
    data class Event(val timestamp: Long, val kind: String, val summary: String)
    private val events = ArrayDeque<Event>()
    @Synchronized fun add(kind: String, summary: String) {
        val clean = summary.replace(Regex("(?i)\\b(pin|otp|password|passcode|kode verifikasi)\\b[^,.!?]*"), "[REDACTED]")
        events.addLast(Event(System.currentTimeMillis(), kind.take(32), clean.take(300)))
        while (events.size > maxEvents.coerceIn(4, 128)) events.removeFirst()
    }
    @Synchronized fun recent(limit: Int = 8) = events.takeLast(limit.coerceIn(1, 16))
    @Synchronized fun clear() = events.clear()
}
