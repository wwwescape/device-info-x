package com.wwwescape.deviceinfox.console.data.db

import com.wwwescape.deviceinfox.console.data.network.UsersApi
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import com.wwwescape.deviceinfox.console.data.network.dto.PartnerPublicDto
import com.wwwescape.deviceinfox.console.data.network.dto.UpdateProfileRequestDto
import com.wwwescape.deviceinfox.console.data.network.dto.UserPublicDto
import com.wwwescape.deviceinfox.console.data.network.dto.isoDateStringToEpochMillis
import com.wwwescape.deviceinfox.console.data.network.dto.toGenderDto
import com.wwwescape.deviceinfox.console.data.network.dto.toIsoDateString
import com.wwwescape.deviceinfox.console.data.network.dto.toPartnerGender
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Manages the local user's own [PartnerEntity] row ([PartnerEntity.isSelf] = true) and, once
 * paired, the partner's ([PartnerEntity.isSelf] = false) — both synced against the server's
 * user-profile endpoints, with Room staying what the rest of the app observes. */
@Singleton
class PartnerRepository @Inject constructor(
    private val partnerDao: PartnerDao,
    private val usersApi: UsersApi,
) {
    val selfProfile: Flow<PartnerEntity?> = partnerDao.observeSelf()
    val partnerProfile: Flow<PartnerEntity?> = partnerDao.observePartner()

    /** Pushes to the server first, writes Room only on success — avatar isn't included (no
     * profile field for it; that's the separate `/users/me/avatar` upload, not wired until the
     * general media upload plumbing exists), so [photoUri] stays a local-only display value. */
    suspend fun saveSelfProfile(
        displayName: String,
        firstName: String,
        lastName: String,
        photoUri: String?,
        birthdayEpochMillis: Long?,
        gender: PartnerGender,
    ) {
        val updated = consoleApiCall {
            usersApi.updateMe(
                UpdateProfileRequestDto(
                    displayName = displayName,
                    firstName = firstName,
                    lastName = lastName,
                    gender = gender.toGenderDto(),
                    birthdayDate = birthdayEpochMillis?.toIsoDateString(),
                ),
            )
        }
        val existing = partnerDao.getSelf()
        partnerDao.upsert(
            PartnerEntity(
                id = updated.id,
                displayName = updated.displayName,
                firstName = updated.firstName,
                lastName = updated.lastName,
                photoUri = photoUri,
                birthdayEpochMillis = updated.birthdayDate.isoDateStringToEpochMillis(),
                gender = updated.gender.toPartnerGender(),
                isSelf = true,
                pairedAtEpochMillis = existing?.pairedAtEpochMillis,
                birthdayMessage = updated.birthdayMessage,
            ),
        )
    }

    /** Settings' Birthday Message section — deliberately its own narrow call rather than folded
     * into [saveSelfProfile], since it's edited from a different screen and doesn't need any of
     * that method's other fields in scope. [message] is sent as-is, including empty string
     * (clears it server-side — see `user_service.update_profile`'s doc comment). */
    suspend fun setBirthdayMessage(message: String) {
        val updated = consoleApiCall {
            usersApi.updateMe(UpdateProfileRequestDto(birthdayMessage = message))
        }
        val existing = partnerDao.getSelf() ?: return
        partnerDao.upsert(existing.copy(birthdayMessage = updated.birthdayMessage))
    }

    /** Called right after login/registration succeeds — the server's real user UUID becomes
     * this device's self [PartnerEntity.id] from this point on. Everything the console writes
     * (messages, calendar events, cycle entries, ...) stamps [PartnerEntity.id] as the author,
     * and the server itself derives `sender_id`/`created_by`/`user_id` from the authenticated
     * JWT — those two have to agree, or every message/event this device creates would render as
     * if it came from the partner instead of "me" once it round-trips through the server. */
    suspend fun syncSelfProfileFromServer(dto: UserPublicDto) {
        val existing = partnerDao.getSelf()
        partnerDao.upsert(
            PartnerEntity(
                id = dto.id,
                displayName = dto.displayName,
                firstName = dto.firstName,
                lastName = dto.lastName,
                photoUri = existing?.photoUri,
                birthdayEpochMillis = dto.birthdayDate.isoDateStringToEpochMillis(),
                gender = dto.gender.toPartnerGender(),
                isSelf = true,
                pairedAtEpochMillis = existing?.pairedAtEpochMillis,
                birthdayMessage = dto.birthdayMessage,
            ),
        )
    }

    /** Called once paired (`ServerPairingRepository`) — [dto] has no username (see
     * `PartnerPublic`'s doc comment server-side), so the partner's id doubles as a stable
     * identity the rest of the console keys off (message senderId, calendar createdBy, etc).
     * [PartnerPublicDto.birthdayMessage] here is what *the partner* wrote — the message
     * [com.wwwescape.deviceinfox.console.ui.ConsoleTabsViewModel.checkBirthday] shows on the
     * local user's own birthday popup. */
    suspend fun syncPartnerProfileFromServer(dto: PartnerPublicDto) {
        partnerDao.upsert(
            PartnerEntity(
                id = dto.id,
                displayName = dto.displayName,
                firstName = dto.firstName,
                lastName = dto.lastName,
                photoUri = null,
                birthdayEpochMillis = dto.birthdayDate.isoDateStringToEpochMillis(),
                gender = dto.gender.toPartnerGender(),
                isSelf = false,
                pairedAtEpochMillis = System.currentTimeMillis(),
                birthdayMessage = dto.birthdayMessage,
            ),
        )
    }

    /** Messaging needs a stable self [PartnerEntity.id] to stamp as a message's senderId even
     * before the user has ever visited Private Settings to fill in a display name/photo — this
     * provisions a minimal row (blank name, unspecified gender) the first time it's needed
     * rather than blocking messaging on profile setup. */
    suspend fun ensureSelfProfileExists(): String {
        partnerDao.getSelf()?.let { return it.id }
        val id = UUID.randomUUID().toString()
        partnerDao.upsert(
            PartnerEntity(
                id = id,
                displayName = "",
                firstName = "",
                lastName = "",
                photoUri = null,
                birthdayEpochMillis = null,
                gender = PartnerGender.UNSPECIFIED,
                isSelf = true,
                pairedAtEpochMillis = null,
                birthdayMessage = null,
            ),
        )
        return id
    }
}
