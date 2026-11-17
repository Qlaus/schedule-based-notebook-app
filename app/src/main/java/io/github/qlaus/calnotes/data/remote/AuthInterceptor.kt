package io.github.qlaus.calnotes.data.remote

import io.github.qlaus.calnotes.auth.AuthManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/** Adds a fresh Bearer token; on 401 forces one token refresh and retries once. */
class AuthInterceptor(private val auth: AuthManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { auth.freshAccessToken() }
        val response = chain.proceed(chain.request().withToken(token))
        if (response.code != 401) return response
        response.close()
        val retryToken = runBlocking { auth.freshAccessToken(forceRefresh = true) }
        return chain.proceed(chain.request().withToken(retryToken))
    }

    private fun okhttp3.Request.withToken(token: String) =
        newBuilder().header("Authorization", "Bearer $token").build()
}
