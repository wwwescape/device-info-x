package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The private console's own SQLCipher-encrypted database — entirely separate from any storage
 * the public Device Info X app uses. DAOs are added here as each feature phase (Pairing,
 * Messaging, Calendar, Period Tracker, Safe Locker) needs them — [partnerDao] is the first,
 * added for the self-profile row.
 *
 * Future schema changes must be added as an explicit [androidx.room.migration.Migration] passed
 * to `Room.databaseBuilder(...).addMigrations(...)` in [ConsoleDatabaseModule] — deliberately no
 * `fallbackToDestructiveMigration()`, so a version bump without a migration fails loudly instead
 * of silently deleting a couple's data.
 */
@Database(
    entities = [
        PartnerEntity::class,
        MessageEntity::class,
        ReactionEntity::class,
        VoiceNoteEntity::class,
        AttachmentEntity::class,
        CalendarEventEntity::class,
        CalendarAttachmentEntity::class,
        PeriodDayLogEntity::class,
        VaultItemEntity::class,
        VaultAlbumEntity::class,
        HiddenMessageEntity::class,
        PollOptionEntity::class,
        NotepadEntryEntity::class,
        ScheduledMessageEntity::class,
    ],
    version = 26,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ConsoleDatabase : RoomDatabase() {
    abstract fun partnerDao(): PartnerDao
    abstract fun messageDao(): MessageDao
    abstract fun hiddenMessageDao(): HiddenMessageDao
    abstract fun reactionDao(): ReactionDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun voiceNoteDao(): VoiceNoteDao
    abstract fun pollOptionDao(): PollOptionDao
    abstract fun notepadEntryDao(): NotepadEntryDao
    abstract fun scheduledMessageDao(): ScheduledMessageDao
    abstract fun calendarDao(): CalendarDao
    abstract fun calendarAttachmentDao(): CalendarAttachmentDao
    abstract fun periodDayLogDao(): PeriodDayLogDao
    abstract fun vaultItemDao(): VaultItemDao
    abstract fun vaultAlbumDao(): VaultAlbumDao

    companion object {
        const val FILE_NAME = "console.db"
    }
}
