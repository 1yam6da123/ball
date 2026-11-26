package com.lyambda.ball

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

data class LeaderboardEntry(
    val name: String = "",
    val score: Long = 0,
    val mode: String = "",
    val timestamp: Any? = null
)

class LeaderboardManager {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val modes = listOf("Classic", "Tilt", "Hearts", "MultiBall")
    private fun profile(uid: String) = db.collection("accounts_v6").document(uid)
    private fun scoreRef(uid: String, mode: String) = db.collection("leaderboard").document("${uid}_$mode")

    fun loadProfile(onResult: (String?, List<Int>, String?) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { onResult(null, emptyList(), "Auth required"); return }
        db.runTransaction { tx ->
            val p = tx.get(profile(uid))
            val scores = modes.map { mode ->
                val s = tx.get(scoreRef(uid, mode))
                if (s.getString("ownerUid") == uid) (s.getLong("score") ?: 0L).coerceIn(0, Int.MAX_VALUE.toLong()).toInt() else 0
            }
            Pair(p.getString("name"), scores)
        }.addOnSuccessListener { result -> onResult(result.first, result.second, null) }
            .addOnFailureListener { onResult(null, emptyList(), "Profile unavailable") }
    }

    @Suppress("UNUSED_PARAMETER")
    fun checkAndSaveNickname(playerId: String, oldName: String, newName: String, onResult: (Boolean, String?) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null || uid != playerId) { onResult(false, "Не удалось сохранить ник"); return }
        val name = newName.trim()
        if (name.isEmpty()) { onResult(false, "Введите ник!"); return }
        if (name.length > 24 || '/' in name || name == "." || name == ".." ||
            (name.startsWith("__") && name.endsWith("__")) || name.any { it.isISOControl() }) {
            onResult(false, "Ник: 1–24 символа, без /"); return
        }
        val names = db.collection("nicknames")
        val newRef = names.document(name)
        val profileRef = profile(uid)
        db.runTransaction { tx ->
            val p = tx.get(profileRef)
            val previous = p.getString("name")
            val oldRef = previous?.takeIf { it != name }?.let { names.document(it) }
            val newDoc = tx.get(newRef)
            val oldDoc = oldRef?.let { tx.get(it) }
            val scores = modes.map { mode -> tx.get(scoreRef(uid, mode)) }
            if (newDoc.exists() && newDoc.getString("ownerUid") != uid) throw IllegalStateException("nickname_taken")
            if (oldDoc != null && oldDoc.exists() && oldDoc.getString("ownerUid") != uid) throw IllegalStateException("owner_mismatch")
            if (!newDoc.exists()) tx.set(newRef, mapOf("playerId" to uid, "ownerUid" to uid))
            tx.set(profileRef, mapOf("name" to name))
            if (oldRef != null && oldDoc?.exists() == true) tx.delete(oldRef)
            for (score in scores) {
                if (score.exists() && score.getString("ownerUid") == uid && score.getString("name") != name) {
                    tx.update(score.reference, "name", name)
                }
            }
            true
        }.addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e ->
                val taken = generateSequence<Throwable>(e) { it.cause }.any { it.message == "nickname_taken" }
                onResult(false, if (taken) "Этот ник уже занят!" else "Не удалось сохранить ник")
            }
    }

    @Suppress("UNUSED_PARAMETER")
    fun submitScore(playerId: String, playerName: String, score: Int, mode: String, onResult: (Boolean, String?) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null || uid != playerId || mode !in modes || score < 0) { onResult(false, "Invalid score"); return }
        val ref = scoreRef(uid, mode)
        db.runTransaction { tx ->
            val p = tx.get(profile(uid))
            val previous = tx.get(ref)
            val name = p.getString("name") ?: throw IllegalStateException("Nickname required")
            if (previous.exists() && previous.getString("ownerUid") != uid) throw IllegalStateException("Owner mismatch")
            val old = previous.getLong("score") ?: 0L
            if (!previous.exists() || score.toLong() > old) {
                tx.set(ref, mapOf("name" to name, "score" to maxOf(old, score.toLong()), "mode" to mode,
                    "ownerUid" to uid, "timestamp" to FieldValue.serverTimestamp()))
            }
            true
        }.addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { onResult(false, "Score not saved") }
    }

    fun fetchTopScores(mode: String, onResult: (List<LeaderboardEntry>, String?) -> Unit) {
        if (mode !in modes) { onResult(emptyList(), "Invalid mode"); return }
        db.collection("leaderboard").whereEqualTo("mode", mode)
            .orderBy("score", Query.Direction.DESCENDING).limit(50).get()
            .addOnSuccessListener { documents ->
                val list = documents.mapNotNull { doc ->
                    try { doc.toObject(LeaderboardEntry::class.java) } catch (_: Exception) { null }
                }
                onResult(list, null)
            }.addOnFailureListener { onResult(emptyList(), it.message) }
    }
}
