package com.typosbro.multilevel.data.local
// Create new file: data/local/Word.kt

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "words")
data class Word(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    val definition: String,
    val example: String,
    val cefrLevel: String,
    val topic: String,

    // --- Spaced Repetition System (SRS) Fields ---
    // Stage 0: New, 1: Learning (e.g., 1 day), 2: (e.g., 3 days), etc.
    var srsStage: Int = 0,
    // The exact time (in milliseconds) when this word should be reviewed next.
    var nextReviewTimestamp: Long = 0L
)