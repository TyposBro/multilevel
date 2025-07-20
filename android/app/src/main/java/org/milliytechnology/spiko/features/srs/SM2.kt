// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/features/srs/SM2.kt
package org.milliytechnology.spiko.features.srs

import com.typosbro.multilevel.data.local.WordEntity
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/**
 * Represents the quality of a user's response during a review session.
 * These map to the q-values used in the SM-2 algorithm.
 */
enum class ReviewQuality {
    AGAIN, // q < 3 (Incorrect response, needs to be repeated soon)
    HARD,  // q = 3 (Correct, but with serious difficulty)
    GOOD,  // q = 4 (Correct, but with some hesitation)
    EASY   // q = 5 (Perfect response)
}

/**
 * An object that implements the SM-2 algorithm for spaced repetition scheduling.
 * Based on the paper by P.A. Wozniak.
 */
object SM2 {

    private const val MIN_EASINESS_FACTOR = 1.3f

    /**
     * Calculates the next review state for a word based on the user's recall quality.
     *
     * @param word The word entity being reviewed.
     * @param quality The user's assessment of the recall quality.
     * @return The updated WordEntity with new SRS parameters.
     */
    fun calculate(word: WordEntity, quality: ReviewQuality): WordEntity {
        // Map the UI quality to the numeric q-value used in the SM-2 formula.
        val q = when (quality) {
            ReviewQuality.AGAIN -> 2 // Any value < 3 will reset the card.
            ReviewQuality.HARD -> 3
            ReviewQuality.GOOD -> 4
            ReviewQuality.EASY -> 5
        }

        // If the quality is below 3, the user failed to recall the item.
        // Reset the repetition sequence for this word.
        if (q < 3) {
            return word.copy(
                repetitions = 0, // Reset the count of successful repetitions.
                interval = 0,    // Reset the interval.
                // Schedule the word for review again in 1 day.
                nextReviewTimestamp = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1)
            )
        }

        // If the quality is 3 or higher, the user recalled the item correctly.
        // 1. Calculate the new easiness factor (EF).
        val oldEF = word.easinessFactor
        val newEF = (oldEF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))).toFloat()
        val correctedEF = newEF.coerceAtLeast(MIN_EASINESS_FACTOR)

        // 2. Calculate the new interval based on the number of successful repetitions.
        val newRepetitions = word.repetitions + 1
        val newInterval = when (newRepetitions) {
            1 -> 1
            2 -> 6
            else -> {
                // I(n) = I(n-1) * EF
                // Here, `word.interval` is the previous interval, I(n-1).
                ceil(word.interval * correctedEF).toInt()
            }
        }

        // 3. Calculate the next review date in milliseconds.
        val nextReviewTime =
            System.currentTimeMillis() + TimeUnit.DAYS.toMillis(newInterval.toLong())

        // 4. Return the updated word with new SRS values.
        return word.copy(
            repetitions = newRepetitions,
            easinessFactor = correctedEF,
            interval = newInterval,
            nextReviewTimestamp = nextReviewTime
        )
    }
}