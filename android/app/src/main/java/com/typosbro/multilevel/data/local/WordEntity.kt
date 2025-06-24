// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/local/WordEntity.kt
package com.typosbro.multilevel.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "words",
    indices = [Index(value = ["word"], unique = true)]
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val word: String,
    val translation: String,
    val example1: String?,
    val example1Translation: String?,
    val example2: String?,
    val example2Translation: String?,
    val cefrLevel: String,
    val topic: String,

    // --- REVISED: Spaced Repetition System (SRS) Fields for SM-2 ---
    var repetitions: Int = 0,           // n: Number of successful (q >= 3) repetitions.
    var easinessFactor: Float = 2.5f,   // EF: The easiness factor, starts at 2.5.
    var interval: Int = 0,              // The last calculated interval in days.
    var nextReviewTimestamp: Long = 0L
)