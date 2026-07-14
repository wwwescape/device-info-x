package com.wwwescape.deviceinfox.console.data.network

import com.wwwescape.deviceinfox.console.data.network.dto.PairRequestDto
import com.wwwescape.deviceinfox.console.data.network.dto.PairingStatusResponseDto
import com.wwwescape.deviceinfox.console.data.network.dto.PartnerCodeCreateResponseDto
import com.wwwescape.deviceinfox.console.data.network.dto.PartnerCodeStatusResponseDto
import com.wwwescape.deviceinfox.console.data.network.dto.PartnerPublicDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST

/** Mirrors `app/api/v1/routers/pairing.py` and `users.py`'s `/partner` route. */
interface PairingApi {
    @POST("pairing/code")
    suspend fun generateCode(): PartnerCodeCreateResponseDto

    @GET("pairing/code")
    suspend fun codeStatus(): PartnerCodeStatusResponseDto

    @POST("pairing/pair")
    suspend fun pair(@Body body: PairRequestDto): PartnerPublicDto

    @GET("pairing/status")
    suspend fun status(): PairingStatusResponseDto

    @DELETE("pairing")
    suspend fun unpair()
}
