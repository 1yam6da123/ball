package com.lyambda.ball

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

internal val progressScoreKeys = listOf("high_classic", "high_tilt", "high_hearts", "high_multiball")
internal val progressAchievementIds = setOf(
    "mode_tilt_master", "mode_tilt_nohands", "mode_hearts_survivor", "mode_hearts_risky",
    "mode_hearts_secondwind", "global_customizer", "global_individual", "global_explorer", "global_collector"
)

internal data class ProgressSnapshot(
    val device: String, val baseOrbs: Int, val earned: Long,
    val scores: List<Int>, val achievements: Set<String>, val played: Set<String>,
    val epoch: String, val pendingReset: String
)

internal data class ProgressResult(
    val orbs: Int, val acknowledgedEarned: Long, val scores: List<Int>,
    val achievements: Set<String>, val played: Set<String>, val epoch: String,
    val acknowledgedReset: String
)

internal class ProgressManager {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun sync(uid: String, local: ProgressSnapshot, done: (ProgressResult?) -> Unit) {
        if (auth.currentUser?.uid != uid) { done(null); return }
        val ref = db.collection("progress_v6").document(uid)
        val deviceRef = ref.collection("devices").document(local.device)
        db.runTransaction { tx ->
            // Read both documents before writing. The transaction can be retried safely.
            val cloud = tx.get(ref)
            val device = tx.get(deviceRef)
            val credited = device.getLong("earned") ?: 0L
            val earned = maxOf(credited, local.earned)
            val previousTotal = (cloud.getLong("orbs") ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong())
            // Pre-update balances are imported once per installation, by maximum rather than addition.
            val base = if (device.exists()) previousTotal else maxOf(previousTotal, local.baseOrbs.toLong())
            val total = (base + (earned - credited).coerceAtMost(Int.MAX_VALUE.toLong())).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val cloudScores = cloud.get("scores") as? List<*> ?: emptyList<Any>()
            val scores = progressScoreKeys.indices.map { i ->
                maxOf(local.scores[i], ((cloudScores.getOrNull(i) as? Number)?.toLong() ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
            }
            val oldEpoch = cloud.getString("epoch").orEmpty()
            val oldReset = device.getString("lastReset").orEmpty()
            val reset = local.pendingReset.isNotEmpty() && local.pendingReset != oldReset
            val epoch = if (reset) local.pendingReset else oldEpoch
            val lastReset = if (reset) local.pendingReset else oldReset
            val allowedPlayed = setOf("0", "1", "2", "3")
            val cloudAchievements = (cloud.get("achievements") as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty().intersect(progressAchievementIds)
            val cloudPlayed = (cloud.get("played") as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty().intersect(allowedPlayed)
            val mergeLocal = reset || local.epoch == oldEpoch || (!cloud.exists()) || (local.pendingReset.isNotEmpty() && local.pendingReset == oldEpoch)
            val achievements = (if (reset) emptySet() else cloudAchievements) + (if (mergeLocal) local.achievements else emptySet())
            val played = (if (reset) emptySet() else cloudPlayed) + (if (mergeLocal) local.played else emptySet())
            if (!cloud.exists() || total.toLong() != previousTotal || scores.map { it.toLong() } != cloudScores || achievements != cloudAchievements || played != cloudPlayed || epoch != oldEpoch) {
                tx.set(ref, mapOf("orbs" to total, "scores" to scores, "achievements" to achievements.sorted(), "played" to played.sorted(), "epoch" to epoch))
            }
            if (!device.exists() || earned != credited || lastReset != oldReset) {
                tx.set(deviceRef, mapOf("earned" to earned, "lastReset" to lastReset))
            }
            ProgressResult(total, earned, scores, achievements, played, epoch, lastReset)
        }.addOnSuccessListener { done(it) }.addOnFailureListener { done(null) }
    }
}
