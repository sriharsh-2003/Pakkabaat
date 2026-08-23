package com.pakkabaat.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    @Query("SELECT * FROM users WHERE userId = :id")
    suspend fun getById(id: String): UserEntity?

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getFirstUser(): UserEntity?
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE sessionId = :id")
    suspend fun getById(id: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE sessionId = :id")
    fun observeById(id: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("DELETE FROM sessions WHERE sessionId = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ConsentEventDao {
    @Insert
    suspend fun insert(event: ConsentEventEntity)

    @Query("SELECT * FROM consent_events WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun forSession(sessionId: String): List<ConsentEventEntity>

    @Query("DELETE FROM consent_events WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}

@Dao
interface AudioRecordingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recording: AudioRecordingEntity)

    @Query("SELECT * FROM audio_recordings WHERE sessionId = :sessionId LIMIT 1")
    suspend fun forSession(sessionId: String): AudioRecordingEntity?

    @Query("SELECT * FROM audio_recordings WHERE sessionId = :sessionId")
    suspend fun allForSession(sessionId: String): List<AudioRecordingEntity>

    @Query("SELECT * FROM audio_recordings WHERE uploadStatus = 'QUEUED' OR uploadStatus = 'LOCAL_ONLY'")
    suspend fun pendingUploads(): List<AudioRecordingEntity>

    @Query("UPDATE audio_recordings SET uploadStatus = :status WHERE recordingId = :id")
    suspend fun setUploadStatus(id: String, status: com.pakkabaat.app.data.model.UploadStatus)

    @Query("DELETE FROM audio_recordings WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}

@Dao
interface TranscriptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transcript: TranscriptEntity)

    @Query("SELECT * FROM transcripts WHERE recordingId = :recordingId")
    suspend fun forRecording(recordingId: String): List<TranscriptEntity>

    @Query("DELETE FROM transcripts WHERE recordingId = :recordingId")
    suspend fun deleteForRecording(recordingId: String)
}

@Dao
interface StructuredDocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(doc: StructuredDocumentEntity)

    @Query("SELECT * FROM structured_documents WHERE sessionId = :sessionId LIMIT 1")
    suspend fun forSession(sessionId: String): StructuredDocumentEntity?

    @Query("SELECT * FROM structured_documents WHERE sessionId = :sessionId LIMIT 1")
    fun observeForSession(sessionId: String): Flow<StructuredDocumentEntity?>

    @Query("DELETE FROM structured_documents WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}

@Dao
interface CertificateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cert: CertificateEntity)

    @Query("SELECT * FROM certificates WHERE documentId = :documentId LIMIT 1")
    suspend fun forDocument(documentId: String): CertificateEntity?

    @Query("DELETE FROM certificates WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: String)
}
