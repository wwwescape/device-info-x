package com.wwwescape.deviceinfox.console.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** The console DB's first real migration (Phase 10) — everything before this shipped at v1
 * with no installed users to migrate. Deliberately no `fallbackToDestructiveMigration()`
 * anywhere in [ConsoleDatabaseModule], so a future version bump without a matching entry here
 * fails loudly instead of silently deleting a couple's data. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vault_items ADD COLUMN caption TEXT DEFAULT NULL")
    }
}

/** Phase 11.8 — Safe Locker gained server-side ownership (shared-but-owner-edits-only, same
 * model as the Period Tracker's partner visibility). `ownerId` is left `NULL` for every
 * pre-existing row; [com.wwwescape.deviceinfox.console.data.vault.VaultRepository] treats that
 * the same as "mine", matching what was implicitly true before this migration. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vault_items ADD COLUMN ownerId TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE vault_albums ADD COLUMN ownerId TEXT DEFAULT NULL")
    }
}

/** Optimistic sending — every pre-existing row is already fully synced with the server, so it
 * defaults to `SENT`, same as [MessageDeliveryState]'s own default. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN deliveryState TEXT NOT NULL DEFAULT 'SENT'")
    }
}

/** "Delete for me" — a new table, not a column, deliberately never touched by message sync (see
 * [HiddenMessageEntity]'s doc comment). Nothing to backfill; starts empty. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS hidden_messages (messageId TEXT NOT NULL PRIMARY KEY)")
    }
}

/** First/last name became mandatory profile fields (collected at registration alongside
 * birthday) — every pre-existing row defaults to blank, same placeholder convention
 * [PartnerRepository.ensureSelfProfileExists] already uses for [PartnerEntity.displayName]. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE partners ADD COLUMN firstName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE partners ADD COLUMN lastName TEXT NOT NULL DEFAULT ''")
    }
}

/** Unifies the old ad-hoc "Planned Date" table with the now-retired `reminder_events` table —
 * every pre-existing row here was implicitly a Planned Date (the only thing this table ever
 * held), hence that default. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN type TEXT NOT NULL DEFAULT 'PLANNED_DATE'")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN location TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN recurrenceRule TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN reminderMinutesBefore TEXT NOT NULL DEFAULT ''")
        db.execSQL("DROP TABLE IF EXISTS reminder_events")
    }
}

/** The Intimacy Log fields — all-null/empty for every pre-existing row, matching "nothing logged
 * yet" (see [CalendarEventEntity]'s doc comment). */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN intimacyRating INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN intimacyDurationMinutes INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN intimacyInitiatedByPartnerId TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN intimacyProtectionUsed INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN intimacyPositions TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN intimacyLocations TEXT NOT NULL DEFAULT ''")
    }
}

/** Cancellation for Planned Date/Planned Drive/Custom entries — every pre-existing row defaults
 * to not-cancelled, matching [EventType.supportsCancellation]'s clamp for every other type. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN cancelled INTEGER NOT NULL DEFAULT 0")
    }
}

/** The optional free-text reason shown alongside the Cancelled toggle — null for every
 * pre-existing row, same "nothing entered yet" default as every other optional text column. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN cancellationReason TEXT DEFAULT NULL")
    }
}

/** `EventType.TRIP` was renamed to `PLANNED_TRIP` (now cancellable, same as Planned Date/Planned
 * Drive/Custom) — [Converters] stores an [EventType] by its Kotlin enum `.name`, so every
 * pre-existing row still holding the old `'TRIP'` string must be rewritten or
 * `EventType.valueOf` throws the next time Room reads it back. Mirrors the server's own
 * `ALTER TYPE event_type RENAME VALUE 'trip' TO 'planned_trip'` migration. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE calendar_events SET type = 'PLANNED_TRIP' WHERE type = 'TRIP'")
    }
}

/** Mood/Rounds/Orgasmed — the same "Intimacy Log" expansion as [MIGRATION_7_8], all-null/empty
 * for every pre-existing row. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN intimacyMoods TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN intimacyRounds INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN intimacyOrgasmedByPartnerId TEXT DEFAULT NULL")
    }
}

/** Link previews — all-null for every pre-existing row, same "nothing fetched yet" default as
 * every other optional column added this way. */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN linkPreviewUrl TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE messages ADD COLUMN linkPreviewTitle TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE messages ADD COLUMN linkPreviewDescription TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE messages ADD COLUMN linkPreviewImagePath TEXT DEFAULT NULL")
    }
}

/** Video message support — all-null for every pre-existing (image) row, same "nothing to add"
 * default as every other optional column added this way. [AttachmentEntity.mediaId]/`durationMs`
 * are populated at insert time for video rows; `localVideoFilePath` stays null until the full
 * video is lazily downloaded (immediately, for the sender's own row — see `MessageRepository.sendVideo`). */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE attachments ADD COLUMN mediaId TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE attachments ADD COLUMN durationMs INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE attachments ADD COLUMN localVideoFilePath TEXT DEFAULT NULL")
    }
}

/** Document message support, plus replacing the old `mimeType.startsWith("video/")` sniff with
 * an explicit, persisted kind — every pre-existing row predates documents, so the two-way
 * backfill below (video vs. everything-else-is-image) is exhaustive for historical data; every
 * new row going forward sets `attachmentKind` explicitly at insert time instead. */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE attachments ADD COLUMN attachmentKind TEXT NOT NULL DEFAULT 'IMAGE'")
        db.execSQL("ALTER TABLE attachments ADD COLUMN originalFilename TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE attachments ADD COLUMN localDocumentFilePath TEXT DEFAULT NULL")
        db.execSQL("UPDATE attachments SET attachmentKind = 'VIDEO' WHERE mimeType LIKE 'video/%'")
    }
}

/** Calendar event attachments — a brand new table (the first genuinely new-table migration in
 * this file, vs. every prior one being an `ALTER TABLE ADD COLUMN`), same column shape as
 * `attachments` (see `CalendarAttachmentEntity`'s own doc comment for why it's a separate table
 * rather than reusing that one). */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS calendar_attachments (
                id TEXT NOT NULL PRIMARY KEY,
                eventId TEXT NOT NULL,
                attachmentKind TEXT NOT NULL DEFAULT 'IMAGE',
                filePath TEXT NOT NULL,
                mimeType TEXT NOT NULL,
                widthPx INTEGER,
                heightPx INTEGER,
                sizeBytes INTEGER,
                mediaId TEXT,
                durationMs INTEGER,
                localVideoFilePath TEXT,
                originalFilename TEXT,
                localDocumentFilePath TEXT,
                FOREIGN KEY(eventId) REFERENCES calendar_events(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_attachments_eventId ON calendar_attachments(eventId)")
    }
}

/** Safe Locker video/document items — every pre-existing row is unambiguously a photo already
 * (Safe Locker was photo-only before this), so the `attachmentKind` default of `'IMAGE'` needs
 * no backfill query, unlike `MIGRATION_14_15`'s mime-sniff backfill for messages. */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vault_items ADD COLUMN attachmentKind TEXT NOT NULL DEFAULT 'IMAGE'")
        db.execSQL("ALTER TABLE vault_items ADD COLUMN mediaId TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE vault_items ADD COLUMN durationMs INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE vault_items ADD COLUMN localVideoFilePath TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE vault_items ADD COLUMN originalFilename TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE vault_items ADD COLUMN localDocumentFilePath TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE vault_items ADD COLUMN sizeBytes INTEGER DEFAULT NULL")
    }
}

/** "Save to Vault" for a calendar event's attachment — mirrors [VaultItemEntity.originalMessageId]'s
 * shape, plus [VaultItemEntity.originalAttachmentMediaId] since (unlike a message) an event can
 * have several attachments, so dedupe needs both columns together. */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vault_items ADD COLUMN originalEventId TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE vault_items ADD COLUMN originalAttachmentMediaId TEXT DEFAULT NULL")
    }
}

/** The birthday custom-message feature — authored by one partner, shown on the *other* partner's
 * birthday popup (see [PartnerEntity.birthdayMessage]'s doc comment), so it lives on `partners`
 * rather than a settings table. Null for every pre-existing row, same "nothing entered yet"
 * default as every other optional text column added this way. */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE partners ADD COLUMN birthdayMessage TEXT DEFAULT NULL")
    }
}

/** Period Tracker: cycle-level → day-level logging — the old `cycle_entries` table (one row per
 * whole period) is replaced by `period_day_logs` (one row per logged day; see
 * [PeriodDayLogEntity]'s own doc comment). Every existing `cycle_entries` row is expanded into
 * one `period_day_logs` row per day in `[startDateEpochMillis, endDateEpochMillis ?? start]`,
 * carrying that cycle's flow/symptoms/notes onto every expanded day (the best available signal
 * from range-level data, since per-day granularity never existed before this migration) — same
 * backfill shape as the server's own `0029_period_day_logs` Alembic migration. The expansion is
 * capped at [MAX_BACKFILL_DAYS] per source row purely as a guard against a corrupt/huge legacy
 * range turning into an unbounded insert loop; `INSERT OR IGNORE` absorbs any date collision from
 * two overlapping legacy cycles (first one wins). */
val MIGRATION_19_20 = object : Migration(19, 20) {
    private val MAX_BACKFILL_DAYS = 60
    private val DAY_MILLIS = 24 * 60 * 60 * 1000L

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS period_day_logs (
                id TEXT NOT NULL PRIMARY KEY,
                partnerId TEXT NOT NULL,
                dateEpochMillis INTEGER NOT NULL,
                flowIntensity TEXT,
                symptoms TEXT,
                notes TEXT,
                FOREIGN KEY(partnerId) REFERENCES partners(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_period_day_logs_partnerId ON period_day_logs(partnerId)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_period_day_logs_partnerId_dateEpochMillis " +
                "ON period_day_logs(partnerId, dateEpochMillis)",
        )

        val cursor = db.query(
            "SELECT partnerId, startDateEpochMillis, endDateEpochMillis, flowIntensity, symptoms, notes " +
                "FROM cycle_entries",
        )
        cursor.use {
            while (it.moveToNext()) {
                val partnerId = it.getString(0)
                val start = it.getLong(1)
                val end = if (it.isNull(2)) start else it.getLong(2).coerceAtLeast(start)
                val flowIntensity = if (it.isNull(3)) null else it.getString(3)
                val symptoms = if (it.isNull(4)) null else it.getString(4)
                val notes = if (it.isNull(5)) null else it.getString(5)

                val spanDays = (minOf(end, start + (MAX_BACKFILL_DAYS - 1) * DAY_MILLIS) - start) / DAY_MILLIS
                for (offset in 0..spanDays) {
                    val dateEpochMillis = start + offset * DAY_MILLIS
                    db.execSQL(
                        "INSERT OR IGNORE INTO period_day_logs " +
                            "(id, partnerId, dateEpochMillis, flowIntensity, symptoms, notes) VALUES (?, ?, ?, ?, ?, ?)",
                        arrayOf<Any?>(java.util.UUID.randomUUID().toString(), partnerId, dateEpochMillis, flowIntensity, symptoms, notes),
                    )
                }
            }
        }

        db.execSQL("DROP TABLE cycle_entries")
    }
}

/** "Message info" screen — the raw read/delivered timestamps behind the existing `isRead`/
 * `deliveryState` columns, which this app already receives from the server on every message
 * payload and simply discarded until now (see `MessageOutDto.readAt`/`deliveredAt`, already
 * deserialized, just never persisted). Null for every pre-existing row regardless of its current
 * `isRead`/`deliveryState` — there's no historical timestamp to backfill from locally, only the
 * boolean/enum outcome; the exact "when" for anything already read/delivered before this
 * migration is gone. Known, accepted gap rather than something worth a dedicated backfill sync:
 * `syncConversation()`/`loadOlderMessages()` only ever fetch messages Room doesn't already have,
 * so an old, already-read message already sitting in Room won't get its timestamp filled in just
 * by opening the app again — only a later full-payload re-apply for that *specific* message (e.g.
 * editing it) would. Every message sent/received/read/delivered from this point on gets the real
 * timestamp, which is what the feature is actually for. */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN readAtEpochMillis INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE messages ADD COLUMN deliveredAtEpochMillis INTEGER DEFAULT NULL")
    }
}

/** Calendar event cover photos — a plain pair of columns on `calendar_events` rather than a new
 * table, since there's exactly one (or none) per event, unlike the general attachments list
 * (`calendar_attachments`, a genuinely separate table). Null for every pre-existing row, same
 * "nothing set yet" default as every other optional column added this way. */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN coverMediaId TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN coverFilePath TEXT DEFAULT NULL")
    }
}

/** Polls — a new message kind, following the same "no stored type column, inferred from a
 * related row's presence" pattern [AttachmentEntity]/[VoiceNoteEntity] already use (see
 * [MessageEntity]'s own doc comment). `pollAllowsMultiple`/`pollClosedAtEpochMillis` default to
 * "not a poll"'s own harmless values for every pre-existing row; [PollOptionEntity] is a brand
 * new table, same "first genuinely new table" shape [MIGRATION_15_16] introduced for calendar
 * attachments — starts empty, nothing to backfill. */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN pollAllowsMultiple INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE messages ADD COLUMN pollClosedAtEpochMillis INTEGER DEFAULT NULL")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS poll_options (
                id TEXT NOT NULL PRIMARY KEY,
                messageId TEXT NOT NULL,
                optionText TEXT NOT NULL,
                orderIndex INTEGER NOT NULL,
                votedByPartnerIds TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(messageId) REFERENCES messages(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_poll_options_messageId ON poll_options(messageId)")
    }
}

/** Notepad — a brand new table, same "first genuinely new table" shape [MIGRATION_15_16]/
 * [MIGRATION_22_23] already used; starts empty, nothing to backfill. [NotepadEntryEntity.type] is
 * stored as its enum name (`Converters.fromNotepadEntryType`) matching every other `TEXT`-backed
 * enum column in this database. */
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS notepad_entries (
                id TEXT NOT NULL PRIMARY KEY,
                type TEXT NOT NULL,
                ownerId TEXT DEFAULT NULL,
                body TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL,
                updatedAtRaw TEXT NOT NULL,
                updatedBy TEXT DEFAULT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notepad_entries_type ON notepad_entries(type)")
    }
}

/** Notepad titles — a plain new column, blank for every pre-existing row (matching [MIGRATION_23_24]'s
 * own "starts blank, filled in by the editor's autosave" default for `body`). The list row falls
 * back to [com.wwwescape.deviceinfox.console.data.notepad.NotepadEntry.preview] (first line of
 * `body`) whenever `title` is blank, so a pre-existing untitled note still shows something
 * meaningful in the list rather than a blank row. */
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notepad_entries ADD COLUMN title TEXT NOT NULL DEFAULT ''")
    }
}

/** Scheduled Messages — a brand new table, same "first genuinely new table" shape
 * [MIGRATION_15_16]/[MIGRATION_22_23]/[MIGRATION_23_24] already used; starts empty, nothing to
 * backfill. No `type`/`ownerId` columns — every row here is always this device's own (see
 * [ScheduledMessageEntity]'s own doc comment). */
val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS scheduled_messages (
                id TEXT NOT NULL PRIMARY KEY,
                body TEXT NOT NULL,
                scheduledAtEpochMillis INTEGER NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

/** Location messages — a plain pair of new columns, null for every pre-existing row (matching
 * [MIGRATION_24_25]'s own "starts blank" treatment), same "unused/null on every other type"
 * shape the poll* columns already have on this table. */
val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN locationLat REAL")
        db.execSQL("ALTER TABLE messages ADD COLUMN locationLng REAL")
    }
}

/** Which of the pair cancelled an event (a real partner id or [CANCELLED_BY_BOTH]) — null for
 * every pre-existing row, same "starts blank" treatment [MIGRATION_9_10]'s cancellationReason
 * column already has on this exact table. */
val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN cancelledByPartnerId TEXT DEFAULT NULL")
    }
}
