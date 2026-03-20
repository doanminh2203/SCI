package com.example.sci.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun createUserProfileIfNeeded() {
        val user = auth.currentUser ?: return
        val docRef = db.collection("users").document(user.uid)

        val snapshot = docRef.get().await()
        if (!snapshot.exists()) {
            val data = mapOf(
                "displayName" to (user.email?.substringBefore("@").orEmpty()),
                "email" to (user.email ?: ""),
                "createdAt" to System.currentTimeMillis()
            )
            docRef.set(data).await()
        }
    }
}