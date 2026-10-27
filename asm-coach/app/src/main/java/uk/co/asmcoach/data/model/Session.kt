package uk.co.asmcoach.data.model

import kotlinx.serialization.Serializable

enum class SessionKind { StrategyBrief, Interview }

/** A saved brief or interview result, stored on-device and synced to Firestore when configured. */
@Serializable
data class SessionRecord(
    val id: String,
    val kind: SessionKind,
    val title: String,
    val subtitle: String,
    val score: Int,
    val createdAt: Long,
    val payload: String,
)
