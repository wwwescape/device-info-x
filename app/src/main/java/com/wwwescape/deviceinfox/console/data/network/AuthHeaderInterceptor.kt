package com.wwwescape.deviceinfox.console.data.network

import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

/** Attaches `Authorization: Bearer <access_token>` to every request when a session exists.
 * Auth endpoints themselves (`/auth/login`, `/auth/register`, `/auth/refresh`) don't need one
 * and the server ignores it if present, so this doesn't need to special-case them. */
class AuthHeaderInterceptor @Inject constructor(
    private val tokenStore: ConsoleAuthTokenStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val accessToken = tokenStore.accessToken ?: return chain.proceed(original)
        val authorized = original.newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .build()
        return chain.proceed(authorized)
    }
}
