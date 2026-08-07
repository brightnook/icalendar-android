package com.victor2319.ioscalendar.auth

interface CredentialsStore {
    suspend fun save(credentials: Credentials)

    suspend fun load(): Credentials?

    suspend fun clear()
}
