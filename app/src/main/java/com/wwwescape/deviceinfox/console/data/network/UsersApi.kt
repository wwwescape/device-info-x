package com.wwwescape.deviceinfox.console.data.network

import com.wwwescape.deviceinfox.console.data.network.dto.ChangePasswordRequestDto
import com.wwwescape.deviceinfox.console.data.network.dto.PartnerPublicDto
import com.wwwescape.deviceinfox.console.data.network.dto.SeenToursDto
import com.wwwescape.deviceinfox.console.data.network.dto.SeenWhatsNewDto
import com.wwwescape.deviceinfox.console.data.network.dto.UnseenInsightsDto
import com.wwwescape.deviceinfox.console.data.network.dto.UpdateProfileRequestDto
import com.wwwescape.deviceinfox.console.data.network.dto.UserPublicDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/** Mirrors `app/api/v1/routers/users.py`. Avatar upload (`POST /users/me/avatar`, multipart)
 * isn't wired yet — the profile photo picker stays local-only display until the general media
 * upload plumbing lands (Phase 11.4). */
interface UsersApi {
    @GET("users/me")
    suspend fun me(): UserPublicDto

    @PATCH("users/me")
    suspend fun updateMe(@Body body: UpdateProfileRequestDto): UserPublicDto

    @POST("users/me/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequestDto): UserPublicDto

    @GET("users/partner")
    suspend fun partner(): PartnerPublicDto

    @DELETE("users/me")
    suspend fun deleteMe()

    /** Guided feature-tour "seen" state — see
     * [com.wwwescape.deviceinfox.console.data.tour.FeatureTourRepository] for why this lives
     * server-side rather than in local `DataStore` like every other "seen this" flag. */
    @GET("users/me/seen-tours")
    suspend fun seenTours(): SeenToursDto

    @POST("users/me/seen-tours/{tourKey}")
    suspend fun markTourSeen(@Path("tourKey") tourKey: String)

    /** "What's New" seen state — see
     * [com.wwwescape.deviceinfox.console.data.whatsnew.WhatsNewRepository]. Exact mirror of
     * `seenTours`/`markTourSeen` above — the entry copy lives client-side, only the seen `tag`s
     * are server-tracked. */
    @GET("users/me/seen-whats-new")
    suspend fun seenWhatsNew(): SeenWhatsNewDto

    @POST("users/me/seen-whats-new/{tag}")
    suspend fun markWhatsNewSeen(@Path("tag") tag: String)

    /** "DIX AI" insight seen-state — see
     * [com.wwwescape.deviceinfox.console.data.insights.InsightsRepository]. Unlike
     * [seenWhatsNew], the insight's own text is fetched here too (manually authored server-side
     * via the CLI, not shipped in the client), so this returns the still-unseen entries directly
     * rather than just a set of already-seen keys to diff against a local catalog. */
    @GET("users/me/unseen-insights")
    suspend fun unseenInsights(): UnseenInsightsDto

    @POST("users/me/seen-insights/{insightId}")
    suspend fun markInsightSeen(@Path("insightId") insightId: String)
}
