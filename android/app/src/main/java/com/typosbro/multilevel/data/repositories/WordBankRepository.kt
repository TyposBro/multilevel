// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/data/repositories/WordBankRepository.kt
package com.typosbro.multilevel.data.repositories

import com.typosbro.multilevel.data.remote.ApiService
import com.typosbro.multilevel.data.remote.models.ApiWord
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import com.typosbro.multilevel.data.remote.models.safeApiCall
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordBankRepository @Inject constructor(
    private val apiService: ApiService
) {

    suspend fun getLevels(): RepositoryResult<List<String>> =
        safeApiCall { apiService.getWordLevels() }

    suspend fun getTopics(level: String): RepositoryResult<List<String>> =
        safeApiCall { apiService.getWordTopics(level) }

    suspend fun getWords(level: String, topic: String): RepositoryResult<List<ApiWord>> =
        safeApiCall { apiService.getWords(level, topic) }

    /**
     * --- NEW: Fetches all words for an entire level ---
     * This is done by first getting all topics for the level, then fetching words for each topic concurrently.
     */
    suspend fun getAllWordsForLevel(level: String): RepositoryResult<List<ApiWord>> {
        // First, get all topics for the given level.
        val topicsResult = getTopics(level)
        if (topicsResult is RepositoryResult.Error) {
            return topicsResult // Propagate the error if we can't get topics.
        }
        val topics = (topicsResult as RepositoryResult.Success).data

        // Concurrently fetch words for each topic.
        return try {
            coroutineScope {
                val deferredWords = topics.map { topic ->
                    async { getWords(level, topic) }
                }
                val results = deferredWords.awaitAll()

                val allWords = mutableListOf<ApiWord>()
                // Check if any of the calls failed.
                results.forEach { result ->
                    when (result) {
                        is RepositoryResult.Success -> allWords.addAll(result.data)
                        is RepositoryResult.Error -> {
                            // If any single topic fails, we return the first error found.
                            return@coroutineScope RepositoryResult.Error("Failed to fetch words for one or more topics: ${result.message}")
                        }
                    }
                }
                RepositoryResult.Success(allWords)
            }
        } catch (e: Exception) {
            RepositoryResult.Error("An unexpected error occurred while fetching all words for level $level: ${e.message}")
        }
    }
}