package com.typosbro.multilevel.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IeltsExamResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: IeltsExamResultEntity)

    @Query("SELECT * FROM ielts_exam_results ORDER BY createdAt DESC")
    fun getHistorySummary(): Flow<List<IeltsExamResultEntity>>

    @Query("SELECT * FROM ielts_exam_results WHERE id = :id")
    fun getResultById(id: String): Flow<IeltsExamResultEntity?>
}