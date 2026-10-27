package uk.co.asmcoach.data.model

enum class ModuleTint { Amber, Sage, Slate, Rose }

data class Masterclass(
    val id: String,
    val title: String,
    val subtitle: String,
    val minutes: Int,
    val tint: ModuleTint,
    val lessons: List<Lesson>,
)

data class Lesson(
    val id: String,
    val title: String,
    val points: List<String>,
    /** A spoken-style summary for the read-out-loud box. */
    val readAloud: String,
    val keyTerms: List<String> = emptyList(),
)
