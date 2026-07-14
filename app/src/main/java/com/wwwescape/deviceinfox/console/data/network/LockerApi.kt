package com.wwwescape.deviceinfox.console.data.network

import com.wwwescape.deviceinfox.console.data.network.dto.LockerAlbumCreateDto
import com.wwwescape.deviceinfox.console.data.network.dto.LockerAlbumOutDto
import com.wwwescape.deviceinfox.console.data.network.dto.LockerAlbumUpdateDto
import com.wwwescape.deviceinfox.console.data.network.dto.LockerItemCreateDto
import com.wwwescape.deviceinfox.console.data.network.dto.LockerItemOutDto
import com.wwwescape.deviceinfox.console.data.network.dto.LockerItemUpdateDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/** Mirrors `app/api/v1/routers/locker.py`. Both albums and items push over the WebSocket
 * (`locker_service.py` calls `notification_service.notify_user` on every mutation), same as
 * `CalendarApi` — see `VaultRepository`'s doc comment. */
interface LockerApi {
    @GET("locker/albums")
    suspend fun listAlbums(): List<LockerAlbumOutDto>

    @POST("locker/albums")
    suspend fun createAlbum(@Body body: LockerAlbumCreateDto): LockerAlbumOutDto

    @PATCH("locker/albums/{id}")
    suspend fun renameAlbum(@Path("id") id: String, @Body body: LockerAlbumUpdateDto): LockerAlbumOutDto

    @DELETE("locker/albums/{id}")
    suspend fun deleteAlbum(@Path("id") id: String)

    @GET("locker/items")
    suspend fun listItems(): List<LockerItemOutDto>

    @POST("locker/items")
    suspend fun createItem(@Body body: LockerItemCreateDto): LockerItemOutDto

    @PATCH("locker/items/{id}")
    suspend fun updateItem(@Path("id") id: String, @Body body: LockerItemUpdateDto): LockerItemOutDto

    @DELETE("locker/items/{id}")
    suspend fun deleteItem(@Path("id") id: String)

    @DELETE("locker/items")
    suspend fun deleteAllMine()
}
