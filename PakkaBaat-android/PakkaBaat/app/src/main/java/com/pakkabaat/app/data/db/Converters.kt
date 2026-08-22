package com.pakkabaat.app.data.db

import androidx.room.TypeConverter
import com.pakkabaat.app.data.model.*

class Converters {
    @TypeConverter fun fromSessionMode(v: SessionMode): String = v.name
    @TypeConverter fun toSessionMode(v: String): SessionMode = SessionMode.valueOf(v)

    @TypeConverter fun fromSessionStatus(v: SessionStatus): String = v.name
    @TypeConverter fun toSessionStatus(v: String): SessionStatus = SessionStatus.valueOf(v)

    @TypeConverter fun fromConsentAction(v: ConsentAction): String = v.name
    @TypeConverter fun toConsentAction(v: String): ConsentAction = ConsentAction.valueOf(v)

    @TypeConverter fun fromUploadStatus(v: UploadStatus): String = v.name
    @TypeConverter fun toUploadStatus(v: String): UploadStatus = UploadStatus.valueOf(v)

    @TypeConverter fun fromTranscriptSource(v: TranscriptSource): String = v.name
    @TypeConverter fun toTranscriptSource(v: String): TranscriptSource = TranscriptSource.valueOf(v)

    @TypeConverter fun fromAgreementType(v: AgreementType): String = v.name
    @TypeConverter fun toAgreementType(v: String): AgreementType = AgreementType.valueOf(v)

    @TypeConverter fun fromPartyRole(v: PartyRole): String = v.name
    @TypeConverter fun toPartyRole(v: String): PartyRole = PartyRole.valueOf(v)
}
