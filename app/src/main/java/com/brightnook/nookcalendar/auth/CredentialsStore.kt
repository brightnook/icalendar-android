package com.brightnook.nookcalendar.auth

interface CredentialsStore {
    suspend fun save(credentials: Credentials)

    suspend fun load(): Credentials?

    suspend fun clear()
}
