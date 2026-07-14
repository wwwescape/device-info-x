package com.wwwescape.deviceinfox.console.data.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Module
@InstallIn(SingletonComponent::class)
object ConsoleDatabaseModule {

    @Provides
    @Singleton
    fun provideConsoleDatabase(
        @ApplicationContext context: Context,
        keyProvider: ConsoleDatabaseKeyProvider,
    ): ConsoleDatabase {
        // Required by net.zetetic:android-database-sqlcipher to load its native libsqlcipher.so
        // before any database is opened.
        SQLiteDatabase.loadLibs(context)
        val supportFactory = SupportFactory(keyProvider.getOrCreatePassphrase())
        return Room.databaseBuilder(context, ConsoleDatabase::class.java, ConsoleDatabase.FILE_NAME)
            .openHelperFactory(supportFactory)
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
                MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
                MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25,
                MIGRATION_25_26,
            )
            .build()
    }

    @Provides
    fun providePartnerDao(database: ConsoleDatabase): PartnerDao = database.partnerDao()

    @Provides
    fun provideMessageDao(database: ConsoleDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideHiddenMessageDao(database: ConsoleDatabase): HiddenMessageDao = database.hiddenMessageDao()

    @Provides
    fun provideReactionDao(database: ConsoleDatabase): ReactionDao = database.reactionDao()

    @Provides
    fun provideAttachmentDao(database: ConsoleDatabase): AttachmentDao = database.attachmentDao()

    @Provides
    fun provideVoiceNoteDao(database: ConsoleDatabase): VoiceNoteDao = database.voiceNoteDao()

    @Provides
    fun providePollOptionDao(database: ConsoleDatabase): PollOptionDao = database.pollOptionDao()

    @Provides
    fun provideNotepadEntryDao(database: ConsoleDatabase): NotepadEntryDao = database.notepadEntryDao()

    @Provides
    fun provideScheduledMessageDao(database: ConsoleDatabase): ScheduledMessageDao = database.scheduledMessageDao()

    @Provides
    fun provideCalendarDao(database: ConsoleDatabase): CalendarDao = database.calendarDao()

    @Provides
    fun provideCalendarAttachmentDao(database: ConsoleDatabase): CalendarAttachmentDao = database.calendarAttachmentDao()

    @Provides
    fun providePeriodDayLogDao(database: ConsoleDatabase): PeriodDayLogDao = database.periodDayLogDao()

    @Provides
    fun provideVaultItemDao(database: ConsoleDatabase): VaultItemDao = database.vaultItemDao()

    @Provides
    fun provideVaultAlbumDao(database: ConsoleDatabase): VaultAlbumDao = database.vaultAlbumDao()
}
