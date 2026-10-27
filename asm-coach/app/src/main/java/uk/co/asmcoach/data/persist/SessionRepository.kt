package uk.co.asmcoach.data.persist

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.builtins.ListSerializer
import uk.co.asmcoach.data.model.SessionRecord
import uk.co.asmcoach.data.remote.AppJson

private val Context.historyStore by preferencesDataStore(name = "history")

/**
 * Saves briefs and interview results. The device copy is the source of truth so the app works
 * offline; when Firebase is configured, each record is also written to Firestore (free Spark plan)
 * under the user's anonymous-auth uid.
 */
class SessionRepository(
    private val context: Context,
    private val cloud: FirestoreSync?,
) {
    private val key = stringPreferencesKey("records")
    private val listSerializer = ListSerializer(SessionRecord.serializer())

    val cloudEnabled: Boolean get() = cloud != null

    val recent: Flow<List<SessionRecord>> = context.historyStore.data.map { prefs ->
        prefs[key]?.let { runCatching { AppJson.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty()
    }

    suspend fun save(record: SessionRecord) {
        context.historyStore.edit { prefs ->
            val existing = prefs[key]
                ?.let { runCatching { AppJson.decodeFromString(listSerializer, it) }.getOrNull() }
                .orEmpty()
            val updated = (listOf(record) + existing.filterNot { it.id == record.id }).take(MAX_RECORDS)
            prefs[key] = AppJson.encodeToString(listSerializer, updated)
        }
        cloud?.let { sync ->
            runCatching { sync.push(record) }
                .onFailure { Log.w(TAG, "Firestore sync failed; kept on device", it) }
        }
    }

    private companion object {
        const val MAX_RECORDS = 60
        const val TAG = "SessionRepository"
    }
}

class FirestoreSync {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private suspend fun uid(): String =
        auth.currentUser?.uid ?: auth.signInAnonymously().await().user?.uid
            ?: error("Anonymous sign-in returned no user")

    suspend fun push(record: SessionRecord) {
        val doc = mapOf(
            "kind" to record.kind.name,
            "title" to record.title,
            "subtitle" to record.subtitle,
            "score" to record.score,
            "createdAt" to record.createdAt,
            "payload" to record.payload,
        )
        db.collection("users").document(uid())
            .collection("sessions").document(record.id)
            .set(doc)
            .await()
    }

    companion object {
        /** Null unless google-services.json was present at build time and Firebase initialised. */
        fun createIfAvailable(context: Context): FirestoreSync? =
            if (FirebaseApp.getApps(context).isNotEmpty()) FirestoreSync() else null
    }
}
