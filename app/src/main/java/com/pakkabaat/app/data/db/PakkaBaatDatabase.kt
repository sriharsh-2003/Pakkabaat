package com.pakkabaat.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        UserEntity::class,
        SessionEntity::class,
        ConsentEventEntity::class,
        AudioRecordingEntity::class,
        TranscriptEntity::class,
        StructuredDocumentEntity::class,
        CertificateEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PakkaBaatDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun sessionDao(): SessionDao
    abstract fun consentEventDao(): ConsentEventDao
    abstract fun audioRecordingDao(): AudioRecordingDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun structuredDocumentDao(): StructuredDocumentDao
    abstract fun certificateDao(): CertificateDao

    companion object {
        @Volatile private var INSTANCE: PakkaBaatDatabase? = null

        fun getInstance(context: Context): PakkaBaatDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PakkaBaatDatabase::class.java,
                    // Local-only DB file, lives entirely on this device (spec section 7.1)
                    "pakkabaat.db"
                ).build().also { INSTANCE = it }
            }
    }
}
