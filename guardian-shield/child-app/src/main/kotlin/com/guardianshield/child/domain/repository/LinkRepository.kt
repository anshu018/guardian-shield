package com.guardianshield.child.domain.repository

interface LinkRepository {
    suspend fun verifyAndLink(pin: String, name: String, age: Int): Result<String>
    suspend fun isAlreadyLinked(): Boolean
}
