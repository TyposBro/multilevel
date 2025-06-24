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

    @Query("SELECT * FROM words WHERE nextReviewTimestamp <= :currentTime ORDER BY nextReviewTimestamp ASC")
    fun getDueWords(currentTime: Long): Flow<List<WordEntity>>

    @Query("SELECT COUNT(id) FROM words WHERE nextReviewTimestamp <= :currentTime")
    fun getDueWordsCount(currentTime: Long): Flow<Int>

    @Query("SELECT * FROM words WHERE cefrLevel = :level AND topic = :topic")
    suspend fun getWordsByTopicAndLevel(level: String, topic: String): List<WordEntity>

    @Query("DELETE FROM words WHERE cefrLevel = :level AND topic = :topic")
    suspend fun deleteByTopic(level: String, topic: String)

    @Query("DELETE FROM words WHERE cefrLevel = :level")
    suspend fun deleteByLevel(level: String)

    // --- NEW: Methods to check if items are already added ---
    @Query("SELECT COUNT(id) FROM words WHERE cefrLevel = :level")
    suspend fun countWordsInLevel(level: String): Int

    @Query("SELECT COUNT(id) FROM words WHERE cefrLevel = :level AND topic = :topic")
    suspend fun countWordsInTopic(level: String, topic: String): Int
}