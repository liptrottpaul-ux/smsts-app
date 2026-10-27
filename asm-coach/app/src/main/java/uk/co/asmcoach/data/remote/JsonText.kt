package uk.co.asmcoach.data.remote

/** Pulls the JSON object or array out of a model reply that may be wrapped in prose or code fences. */
object JsonText {
    fun extract(raw: String): String {
        val text = raw.trim()
            .removePrefix("﻿")
            .replace(Regex("^```(?:json|JSON)?\\s*"), "")
            .replace(Regex("\\s*```\\s*$"), "")
            .trim()
        val start = text.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) return text
        val close = if (text[start] == '{') '}' else ']'
        val end = text.lastIndexOf(close)
        return if (end > start) text.substring(start, end + 1) else text.substring(start)
    }
}
