package uk.co.asmcoach.voice

/**
 * Turns written text into what a person would actually say: industry abbreviations spoken the
 * way site people say them, money and symbols in words, markdown removed. Then splits it into
 * sentences so each one gets a natural pause.
 */
object SpeechText {
    /** Said letter by letter on site ("N H B C"), which engines otherwise mangle or read as words. */
    private val spelled = listOf(
        "NHBC", "ASM", "SMSTS", "SSSTS", "CSCS", "CPCS", "HSE", "CDM", "PPE", "NVQ", "HND", "HNC",
        "MEP", "QS", "RFI", "KPI", "CPD", "LABC", "DLP", "QA", "FRA", "CIOB", "IPAF", "HGV", "CCTV",
    )

    /** Acronyms that are said as words ("rams", "cosh"); lower-cased so they aren't spelled out. */
    private val spokenAsWords = mapOf(
        "RAMS" to "rams", "COSHH" to "cosh", "RIDDOR" to "riddor", "LOLER" to "lowler",
        "PUWER" to "pewer", "PASMA" to "pasma", "BIM" to "bim", "CITB" to "C I T B",
    )

    private fun rule(regex: Regex, replace: (MatchResult) -> String) = regex to replace

    private val rules: List<Pair<Regex, (MatchResult) -> String>> = listOf(
        // Markdown and list furniture.
        rule(Regex("\\*\\*|__|`|#+\\s")) { _ -> "" },
        rule(Regex("(?m)^[ \\t]*[-•*][ \\t]+")) { _ -> "" },
        rule(Regex("\\bH&S\\b", RegexOption.IGNORE_CASE)) { _ -> "health and safety" },
        rule(Regex("\\b([A-Z])&([A-Z])\\b")) { m -> "${m.groupValues[1]} and ${m.groupValues[2]}" },
        rule(Regex("\\be\\.g\\.,?", RegexOption.IGNORE_CASE)) { _ -> "for example," },
        rule(Regex("\\bi\\.e\\.,?", RegexOption.IGNORE_CASE)) { _ -> "that is," },
        rule(Regex("\\betc\\b", RegexOption.IGNORE_CASE)) { _ -> "and so on" },
        rule(Regex("\\bvs\\.?(?=\\s)", RegexOption.IGNORE_CASE)) { _ -> "versus" },
        rule(Regex("£\\s?(\\d+(?:\\.\\d+)?)\\s?[kK]\\b")) { m -> "${m.groupValues[1]} thousand pounds" },
        rule(Regex("£\\s?(\\d+(?:\\.\\d+)?)\\s?(?:m|M|mn)\\b")) { m -> "${m.groupValues[1]} million pounds" },
        rule(Regex("\\b(\\d+)\\s?[kK]\\b")) { m -> "${m.groupValues[1]} thousand" },
        // "30-60-90 day plan" → "30, 60, 90 day plan".
        rule(Regex("\\b(\\d+)-(\\d+)-(\\d+)\\b")) { m -> m.groupValues.drop(1).joinToString(", ") },
        rule(Regex("(\\d)\\s?%")) { m -> "${m.groupValues[1]} percent" },
        rule(Regex("\\s&\\s")) { _ -> " and " },
        rule(Regex("(?<=[A-Za-z])/(?=[A-Za-z])")) { _ -> " or " },
        // Brackets and dashes read as pauses.
        rule(Regex("\\s*[()]\\s*")) { _ -> ", " },
        rule(Regex("\\s+[–—-]\\s+")) { _ -> ", " },
        rule(Regex("…|\\.\\.\\.")) { _ -> ", " },
    )

    fun forSpeech(text: String): String {
        var out = text
        rules.forEach { (regex, replace) -> out = regex.replace(out, replace) }
        spokenAsWords.forEach { (k, v) -> out = Regex("\\b$k\\b").replace(out, v) }
        spelled.forEach { a -> out = Regex("\\b${a}s?\\b").replace(out) { m -> a.toList().joinToString(" ") + if (m.value.length > a.length) "'s" else "" } }
        return out
            .replace(Regex(",\\s*,+"), ",")
            .replace(Regex("\\s+([,.!?])"), "$1")
            .replace(Regex(",([.!?])"), "$1")
            .replace(Regex("[ \\t]+"), " ")
            .trim()
            .trimStart(',', ' ')
    }

    /** Sentences, with list lines and paragraphs as their own sentences. */
    fun sentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.any(Char::isLetterOrDigit) }
}
