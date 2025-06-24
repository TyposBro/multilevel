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
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(words: List<WordEntity>)

    @Update
    suspend fun update(word: WordEntity)

    // --- REVISED: Get due words with optional filters ---
    @Query(
        "SELECT * FROM words WHERE nextReviewTimestamp <= :currentTime " +
                "AND (:level IS NULL OR cefrLevel = :level) " +
                "AND (:topic IS NULL OR topic = :topic) " +
                "ORDER BY nextReviewTimestamp ASC"
    )
    fun getDueWords(
        currentTime: Long,
        level: String? = null,
        topic: String? = null
    ): Flow<List<WordEntity>>

    // --- Count queries for the global stats ---
    @Query("SELECT COUNT(id) FROM words WHERE nextReviewTimestamp <= :currentTime")
    fun getDueWordsCount(currentTime: Long): Flow<Int>

    @Query("SELECT COUNT(id) FROM words WHERE repetitions = 0")
    fun getNewWordsCount(): Flow<Int>

    @Query("SELECT COUNT(id) FROM words")
    fun getTotalWordsCount(): Flow<Int>

    // --- Count queries for specific levels ---
    
    @Query("SELECT COUNT(id) FROM words WHERE cefrLevel = :level AND nextReviewTimestamp <= :currentTime")
    suspend fun countDueWordsInLevel(level: String, currentTime: Long): Int

    @Query("SELECT COUNT(id) FROM words WHERE cefrLevel = :level AND repetitions = 0")
    suspend fun countNewWordsInLevel(level: String): Int

    @Query("SELECT COUNT(id) FROM words WHERE cefrLevel = :level")
    suspend fun countTotalWordsInLevel(level: String): Int

    // --- Count queries for specific topics ---
    @Query("SELECT COUNT(id) FROM words WHERE cefrLevel = :level AND topic = :topic AND nextReviewTimestamp <= :currentTime")
    suspend fun countDueWordsInTopic(level: String, topic: String, currentTime: Long): Int

    @Query("SELECT COUNT(id) FROM words WHERE cefrLevel = :level AND topic = :topic AND repetitions = 0")
    suspend fun countNewWordsInTopic(level: String, topic: String): Int

    @Query("SELECT COUNT(id) FROM words WHERE cefrLevel = :level AND topic = :topic")
    suspend fun countTotalWordsInTopic(level: String, topic: String): Int

    // Other functions (delete, etc.) remain the same
    @Query("DELETE FROM words WHERE cefrLevel = :level AND topic = :topic")
    suspend fun deleteByTopic(level: String, topic: String)

    @Query("DELETE FROM words WHERE cefrLevel = :level")
    suspend fun deleteByLevel(level: String)
}