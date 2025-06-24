package com.typosbro.multilevel.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.local.WordDao
import com.typosbro.multilevel.data.local.WordEntity
import com.typosbro.multilevel.data.remote.models.ApiWord
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import com.typosbro.multilevel.data.repositories.WordBankRepository
import com.typosbro.multilevel.features.srs.ReviewQuality
import com.typosbro.multilevel.features.srs.SM2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WordBankUiState(
    // Due words count is now part of the main UI state for on-demand refreshing.
    val dueWordsCount: Int = 0,

    // Review Session State
    val reviewWords: List<WordEntity> = emptyList(),
    val currentReviewIndex: Int = 0,
    val isSessionActive: Boolean = false,
    val isSessionFinished: Boolean = false,

    // Word Discovery State
    val levels: List<String> = emptyList(),
    val topics: List<String> = emptyList(),
    val discoverableWords: List<ApiWord> = emptyList(),
    val bookmarkedWords: Set<String> = emptySet(),
    val levelsAddedStatus: Map<String, Boolean> = emptyMap(),
    val topicsAddedStatus: Map<String, Boolean> = emptyMap(),
    val loadingItems: Set<String> = emptySet(), // Holds keys like "A2" or "A2_Family"

    // Common State
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val currentWord: WordEntity?
        get() = reviewWords.getOrNull(currentReviewIndex)

    val allWordsInCurrentTopicAreBookmarked: Boolean
        get() = discoverableWords.isNotEmpty() && bookmarkedWords.containsAll(discoverableWords.map { it.word })
}

@HiltViewModel
class WordBankViewModel @Inject constructor(
    private val wordDao: WordDao,
    private val wordBankRepository: WordBankRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(WordBankUiState())
    val uiState: StateFlow<WordBankUiState> = _uiState.asStateFlow()

    fun refreshDueWordsCount() {
        viewModelScope.launch {
            val count = wordDao.getDueWordsCount(System.currentTimeMillis()).first()
            _uiState.update { it.copy(dueWordsCount = count) }
        }
    }

    // --- Review Session Logic ---
    fun startReviewSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isSessionActive = true) }
            val dueList = wordDao.getDueWords(System.currentTimeMillis()).first()
            _uiState.update {
                it.copy(
                    reviewWords = dueList.shuffled(),
                    currentReviewIndex = 0,
                    isSessionActive = dueList.isNotEmpty(),
                    isSessionFinished = false,
                    isLoading = false
                )
            }
        }
    }

    fun handleReview(word: WordEntity, quality: ReviewQuality) {
        viewModelScope.launch {
            // Calculate the new SRS data using the SM-2 logic
            val updatedWord = SM2.calculate(word, quality)
            wordDao.update(updatedWord)

            // Advance to the next card or finish the session
            val nextIndex = _uiState.value.currentReviewIndex + 1
            if (nextIndex >= _uiState.value.reviewWords.size) {
                _uiState.update { it.copy(isSessionFinished = true, isSessionActive = false) }
            } else {
                _uiState.update { it.copy(currentReviewIndex = nextIndex) }
            }
        }
    }

    // --- Word Discovery Functions ---
    fun fetchLevels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = wordBankRepository.getLevels()) {
                is RepositoryResult.Success -> {
                    val levels = result.data
                    val statusMap = levels.associateWith { level ->
                        wordDao.countWordsInLevel(level) > 0
                    }
                    _uiState.update {
                        it.copy(
                            levels = levels,
                            levelsAddedStatus = statusMap,
                            isLoading = false
                        )
                    }
                }

                is RepositoryResult.Error -> {
                    _uiState.update { it.copy(error = result.message, isLoading = false) }
                }
            }
        }
    }

    fun fetchTopics(level: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, topics = emptyList()) }
            when (val result = wordBankRepository.getTopics(level)) {
                is RepositoryResult.Success -> {
                    val topics = result.data
                    val statusMap = topics.associateWith { topic ->
                        wordDao.countWordsInTopic(level, topic) > 0
                    }
                    _uiState.update {
                        it.copy(
                            topics = topics,
                            topicsAddedStatus = statusMap,
                            isLoading = false
                        )
                    }
                }

                is RepositoryResult.Error -> {
                    _uiState.update { it.copy(error = result.message, isLoading = false) }
                }
            }
        }
    }

    fun fetchWordsForDiscovery(level: String, topic: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    discoverableWords = emptyList()
                )
            }
            when (val result = wordBankRepository.getWords(level, topic)) {
                is RepositoryResult.Success -> {
                    val apiWords = result.data
                    val existingWords =
                        wordDao.getWordsByTopicAndLevel(level, topic).map { it.word }.toSet()
                    _uiState.update {
                        it.copy(
                            discoverableWords = apiWords,
                            bookmarkedWords = existingWords,
                            isLoading = false
                        )
                    }
                }

                is RepositoryResult.Error -> {
                    _uiState.update { it.copy(error = result.message, isLoading = false) }
                }
            }
        }
    }

    // --- Bulk Word Management ---
    private fun wordsToEntities(apiWords: List<ApiWord>): List<WordEntity> {
        return apiWords.map { apiWord ->
            WordEntity(
                word = apiWord.word,
                translation = apiWord.translation,
                example1 = apiWord.example1,
                example1Translation = apiWord.example1Translation,
                example2 = apiWord.example2,
                example2Translation = apiWord.example2Translation,
                cefrLevel = apiWord.cefrLevel,
                topic = apiWord.topic,
                // Initial SM-2 values for a new word
                repetitions = 0,
                easinessFactor = 2.5f,
                interval = 0,
                nextReviewTimestamp = System.currentTimeMillis() // Due for review immediately
            )
        }
    }

    fun addWordsByTopic(level: String, topic: String) {
        viewModelScope.launch {
            val loadingKey = "${level}_$topic"
            _uiState.update { it.copy(loadingItems = it.loadingItems + loadingKey) }
            val result = wordBankRepository.getWords(level, topic)
            if (result is RepositoryResult.Success) {
                wordDao.insertAll(wordsToEntities(result.data))
            }
            fetchTopics(level)
            _uiState.update { it.copy(loadingItems = it.loadingItems - loadingKey) }
        }
    }

    fun removeWordsByTopic(level: String, topic: String) {
        viewModelScope.launch {
            val loadingKey = "${level}_$topic"
            _uiState.update { it.copy(loadingItems = it.loadingItems + loadingKey) }
            wordDao.deleteByTopic(level, topic)
            fetchTopics(level)
            _uiState.update { it.copy(loadingItems = it.loadingItems - loadingKey) }
        }
    }

    fun addWordsByLevel(level: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingItems = it.loadingItems + level) }
            val result = wordBankRepository.getAllWordsForLevel(level)
            if (result is RepositoryResult.Success) {
                wordDao.insertAll(wordsToEntities(result.data))
            }
            fetchLevels()
            _uiState.update { it.copy(loadingItems = it.loadingItems - level) }
        }
    }

    fun removeWordsByLevel(level: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingItems = it.loadingItems + level) }
            wordDao.deleteByLevel(level)
            fetchLevels()
            _uiState.update { it.copy(loadingItems = it.loadingItems - level) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}