package com.wwwescape.deviceinfox.console.data.network

import com.wwwescape.deviceinfox.console.data.network.dto.LoginRequestDto
import com.wwwescape.deviceinfox.console.data.network.dto.LogoutRequestDto
import com.wwwescape.deviceinfox.console.data.network.dto.RefreshRequestDto
import com.wwwescape.deviceinfox.console.data.network.dto.RegisterRequestDto
import com.wwwescape.deviceinfox.console.data.network.dto.TokenPairDto
import com.wwwescape.deviceinfox.console.data.network.dto.UserPublicDto
import com.wwwescape.deviceinfox.console.data.network.dto.VerifyPasswordRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Mirrors `app/api/v1/routers/auth.py`. Paths are relative (no leading `/`) so Retrofit
 * resolves them against [ConsoleServerConfig.PLACEHOLDER_BASE_URL]'s `api/v1/` suffix — see
 * that constant's doc comment. */
interface AuthApi {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequestDto): UserPublicDto

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): TokenPairDto

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequestDto): TokenPairDto

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequestDto)

    @GET("auth/me")
    suspend fun me(): UserPublicDto

    @POST("auth/verify-password")
    suspend fun verifyPassword(@Body body: VerifyPasswordRequestDto)
}
