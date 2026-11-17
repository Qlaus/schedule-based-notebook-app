package io.github.qlaus.calnotes.data.remote

import io.github.qlaus.calnotes.auth.AuthManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** Adds a fresh Bearer token; on 401 forces one token refresh and retries once. */
class AuthInterceptor(private val auth: AuthManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = token(force = false)
        val response = chain.proceed(chain.request().withToken(token))
        if (response.code != 401) return response
        response.close()
        val retryToken = token(force = true)
        return chain.proceed(chain.request().withToken(retryToken))
    }

    // OkHttp turns any non-IOException thrown here into a process crash, so wrap everything.
    private fun token(force: Boolean): String = try {
        runBlocking { auth.freshAccessToken(forceRefresh = force) }
    } catch (e: IOException) {
        throw e
    } catch (e: Exception) {
        throw IOException("Token error: ${e.javaClass.simpleName}", e)
    }

    private fun okhttp3.Request.withToken(token: String) =
        newBuilder().header("Authorization", "Bearer $token").build()
}
