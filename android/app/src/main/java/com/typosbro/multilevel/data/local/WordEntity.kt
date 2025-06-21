// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/local/WordEntity.kt
package com.typosbro.multilevel.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// --- CHANGE: The entire data class is updated to match ApiWord structure ---
@Entity(
    tableName = "words",
    // Add an index to prevent adding the same word multiple times
    indices = [Index(value = ["word"], unique = true)]
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val word: String, // Was 'text'
    val translation: String, // New field, replaces 'definition'
    val example1: String?, // Was 'example', now nullable to match API
    val example1Translation: String?, // New field
    val example2: String?, // New field
    val example2Translation: String?, // New field
    val cefrLevel: String,
    val topic: String,

    // --- Spaced Repetition System (SRS) Fields ---
    var srsStage: Int = 0,
    var nextReviewTimestamp: Long = 0L
)