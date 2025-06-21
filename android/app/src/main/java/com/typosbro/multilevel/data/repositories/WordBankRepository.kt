package com.typosbro.multilevel.data.repositories

import com.typosbro.multilevel.data.remote.ApiService
import com.typosbro.multilevel.data.remote.models.ApiWord
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import com.typosbro.multilevel.data.remote.models.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordBankRepository @Inject constructor(
    private val apiService: ApiService
) {

    /**
     * Fetches the list of available CEFR levels (A1, A2, etc.).
     */
    suspend fun getLevels(): RepositoryResult<List<String>> =
        safeApiCall { apiService.getWordLevels() }

    /**
     * Fetches the list of available topics for a specific CEFR level.
     * @param level The CEFR level (e.g., "B2") to get topics for.
     */
    suspend fun getTopics(level: String): RepositoryResult<List<String>> =
        safeApiCall { apiService.getWordTopics(level) }

    /**
     * Fetches the list of words for a specific level and topic.
     * @param level The CEFR level (e.g., "C1").
     * @param topic The topic (e.g., "Technology").
     */
    suspend fun getWords(level: String, topic: String): RepositoryResult<List<ApiWord>> =
        safeApiCall { apiService.getWords(level, topic) }
}