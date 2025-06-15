// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/local/WordDao.kt
package com.typosbro.multilevel.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(word: WordEntity)

    @Update
    suspend fun update(word: WordEntity)

    /**
     * Gets all words that are due for review (their review time is in the past).
     * Returns a Flow so the UI can react to changes automatically.
     */
    @Query("SELECT * FROM words WHERE nextReviewTimestamp <= :currentTime ORDER BY nextReviewTimestamp ASC")
    fun getDueWords(currentTime: Long): Flow<List<WordEntity>>

    /**
     * Gets a count of all words due for review.
     */
    @Query("SELECT COUNT(id) FROM words WHERE nextReviewTimestamp <= :currentTime")
    fun getDueWordsCount(currentTime: Long): Flow<Int>
}