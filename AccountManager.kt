package com.lyambda.ball

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class AccountManager {
    private val auth = FirebaseAuth.getInstance()
    val hasSession: Boolean get() = auth.currentUser != null
    val email: String get() = auth.currentUser?.email.orEmpty()

    fun ensureGuest(done: (Boolean) -> Unit) {
        if (hasSession) { done(true); return }
        auth.signInAnonymously().addOnCompleteListener { done(it.isSuccessful && auth.currentUser != null) }
    }

    fun register(email: String, password: String, done: (Boolean) -> Unit) {
        val user = auth.currentUser
        if (user == null || !user.isAnonymous) { done(false); return }
        val credential = EmailAuthProvider.getCredential(email.trim(), password)
        user.linkWithCredential(credential).addOnCompleteListener { task ->
            done(task.isSuccessful)
        }
    }

    fun login(email: String, password: String, done: (Boolean) -> Unit) {
        auth.signInWithEmailAndPassword(email.trim(), password).addOnCompleteListener { done(it.isSuccessful) }
    }

    fun resetPassword(email: String, done: (Boolean) -> Unit) {
        auth.sendPasswordResetEmail(email.trim()).addOnCompleteListener { done(it.isSuccessful) }
    }

    fun setLanguage(language: String) { auth.setLanguageCode(if (language == "en") "en" else "ru") }
    fun logout() { auth.signOut() }
}
