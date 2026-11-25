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

    fun signInAnonymously(onComplete: (Boolean) -> Unit) {
        if (auth.currentUser != null) {
            onComplete(true)
            return
        }
        auth.signInAnonymously().addOnCompleteListener { task ->
            onComplete(task.isSuccessful)
        }
    }

    fun checkAndSaveNickname(
        playerId: String,
        oldName: String,
        newName: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val cleanName = newName.trim()

        if (cleanName.isBlank()) {
            onResult(false, "Введите ник!")
            return
        }
        if (cleanName.contains("/")) {
            onResult(false, "Ник не может содержать /")
            return
        }

        val names = db.collection("nicknames")
        val newRef = names.document(cleanName)
        val oldRef = if (oldName.isNotBlank() && oldName != cleanName) {
            names.document(oldName)
        } else null

        db.runTransaction { transaction ->
            // Все чтения должны выполняться до любых записей в транзакции.
            val newSnapshot = transaction.get(newRef)
            val oldSnapshot = oldRef?.let { transaction.get(it) }
            val ownerId = newSnapshot.getString("playerId")

            if (newSnapshot.exists() && ownerId != playerId) {
                throw IllegalStateException("Этот ник уже занят!")
            }

            transaction.set(newRef, mapOf("playerId" to playerId))
            if (oldRef != null && oldSnapshot?.getString("playerId") == playerId) {
                transaction.delete(oldRef)
            }
            true
        }.addOnSuccessListener {
            // Обновляем отображаемый ник в существующих записях рейтинга.
            for (mode in listOf("Classic", "Tilt", "Hearts", "MultiBall")) {
                val docId = "${playerId}_$mode"
                db.collection("leaderboard").document(docId).update("name", cleanName)
            }
            onResult(true, null)
        }.addOnFailureListener { e ->
            val message = if (e is IllegalStateException && e.message == "Этот ник уже занят!") {
                "Этот ник уже занят!"
            } else {
                "Не удалось проверить ник. Проверь интернет и правила Firestore."
            }
            onResult(false, message)
        }
    }

    fun submitScore(
        playerId: String,
        playerName: String,
        score: Int,
        mode: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val docId = "${playerId}_$mode"
        val docRef = db.collection("leaderboard").document(docId)

        docRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val oldScore = document.getLong("score") ?: 0L
                val oldName = document.getString("name") ?: ""
                if (score.toLong() > oldScore || playerName != oldName) {
                    val updates = hashMapOf<String, Any>(
                        "name" to playerName,
                        "score" to maxOf(score.toLong(), oldScore),
                        "timestamp" to FieldValue.serverTimestamp()
                    )
                    docRef.update(updates)
                        .addOnSuccessListener { onResult(true, null) }
                        .addOnFailureListener { e -> onResult(false, e.message) }
                } else {
                    onResult(true, null)
                }
            } else {
                val entry = hashMapOf(
                    "name" to playerName,
                    "score" to score.toLong(),
                    "mode" to mode,
                    "timestamp" to FieldValue.serverTimestamp()
                )
                docRef.set(entry)
                    .addOnSuccessListener { onResult(true, null) }
                    .addOnFailureListener { e -> onResult(false, e.message) }
            }
        }.addOnFailureListener { e ->
            onResult(false, e.message)
        }
    }

    fun fetchTopScores(mode: String, onResult: (List<LeaderboardEntry>, String?) -> Unit) {
        db.collection("leaderboard")
            .whereEqualTo("mode", mode)
            .orderBy("score", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { documents ->
                val list = documents.mapNotNull { it.toObject(LeaderboardEntry::class.java) }
                onResult(list, null)
            }
            .addOnFailureListener { e ->
                onResult(emptyList(), e.message)
            }
    }
}
