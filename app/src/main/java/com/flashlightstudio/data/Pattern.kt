package com.flashlightstudio.data

data class PatternSegment(val on: Boolean, val durationMs: Long)
data class FlashPattern(val name: String, val segments: List<PatternSegment>, val repeat: Boolean = true)
