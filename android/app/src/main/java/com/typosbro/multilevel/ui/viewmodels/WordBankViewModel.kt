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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeckInfo(
    val name: String,
    val level: String,
    val topic: String?,
    val dueCount: Int,
    val newCount: Int,
    val totalCount: Int,
    val subDecks: List<DeckInfo> = emptyList()
)

data class WordBankUiState(
    // Global stats
    val totalDue: Int = 0,
    val totalNew: Int = 0,
    val totalWords: Int = 0,

    // For hierarchical deck screen
    val deckHierarchy: List<DeckInfo> = emptyList(),

    // State for the "Explore" flow
    val exploreLevels: List<String> = emptyList(),
    val exploreTopics: List<String> = emptyList(),
    val exploreLevelsAddedStatus: Map<String, Boolean> = emptyMap(),
    val exploreTopicAddedStatus: Map<String, Boolean> = emptyMap(),
    val loadingItems: Set<String> = emptySet(),

    // Review Session State
    val reviewWords: List<WordEntity> = emptyList(),
    val currentReviewIndex: Int = 0,
    val isSessionActive: Boolean = false,
    val isSessionFinished: Boolean = false,

    // Common State
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val currentWord: WordEntity?
        get() = reviewWords.getOrNull(currentReviewIndex)
}

@HiltViewModel
class WordBankViewModel @Inject constructor(
    private val wordDao: WordDao,
    private val wordBankRepository: WordBankRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(WordBankUiState())
    val uiState: StateFlow<WordBankUiState> = _uiState.asStateFlow()

    init {
        // This Flow combines the global counts and keeps them updated reactively.
        viewModelScope.launch {
            combine(
                wordDao.getDueWordsCount(System.currentTimeMillis()),
                wordDao.getNewWordsCount(),
                wordDao.getTotalWordsCount()
            ) { due, new, total ->
                _uiState.update { it.copy(totalDue = due, totalNew = new, totalWords = total) }
            }.collect()
        }
    }

    // --- Deck Hierarchy Logic (for main WordBankScreen) ---
    fun loadDeckHierarchy() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val levelsResult = wordBankRepository.getLevels()
            if (levelsResult !is RepositoryResult.Success) {
                _uiState.update { it.copy(isLoading = false, error = "Could not load levels.") }
                return@launch
            }
            val levels = levelsResult.data

            val hierarchy = coroutineScope {
                levels.map { level ->
                    async {
                        val now = System.currentTimeMillis()
                        val topicsResult = wordBankRepository.getTopics(level)
                        val topics =
                            if (topicsResult is RepositoryResult.Success) topicsResult.data else emptyList()

                        val subDecks = topics.map { topic ->
                            DeckInfo(
                                name = topic, level = level, topic = topic,
                                dueCount = wordDao.countDueWordsInTopic(level, topic, now),
                                newCount = wordDao.countNewWordsInTopic(level, topic),
                                totalCount = wordDao.countTotalWordsInTopic(level, topic)
                            )
                        }

                        DeckInfo(
                            name = level, level = level, topic = null,
                            dueCount = subDecks.sumOf { it.dueCount },
                            newCount = subDecks.sumOf { it.newCount },
                            totalCount = subDecks.sumOf { it.totalCount },
                            subDecks = subDecks.filter { it.totalCount > 0 }.sortedBy { it.name }
                        )
                    }
                }.awaitAll()
            }

            _uiState.update {
                it.copy(
                    deckHierarchy = hierarchy.filter { it.totalCount > 0 }.sortedBy { it.name },
                    isLoading = false
                )
            }
        }
    }

    // --- Review Session Logic ---
    fun startReviewSession(level: String? = null, topic: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isSessionActive = true) }
            val dueList = wordDao.getDueWords(System.currentTimeMillis(), level, topic).first()
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
            val updatedWord = SM2.calculate(word, quality)
            wordDao.update(updatedWord)
            val nextIndex = _uiState.value.currentReviewIndex + 1
            if (nextIndex >= _uiState.value.reviewWords.size) {
                _uiState.update { it.copy(isSessionFinished = true, isSessionActive = false) }
            } else {
                _uiState.update { it.copy(currentReviewIndex = nextIndex) }
            }
        }
    }

    // --- Functions for the "Explore" word discovery flow ---
    fun fetchExploreLevels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = wordBankRepository.getLevels()
            if (result is RepositoryResult.Success) {
                val levels = result.data
                // Also fetch whether each level has been added
                val statusMap = levels.associateWith { level ->
                    wordDao.countTotalWordsInLevel(level) > 0
                }
                _uiState.update {
                    it.copy(
                        exploreLevels = levels,
                        exploreLevelsAddedStatus = statusMap,
                        isLoading = false
                    )
                }
            } else if (result is RepositoryResult.Error) {
                _uiState.update { it.copy(error = result.message, isLoading = false) }
            }
        }
    }

    fun fetchExploreTopics(level: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, exploreTopics = emptyList()) }
            val result = wordBankRepository.getTopics(level)
            if (result is RepositoryResult.Success) {
                val topics = result.data
                val statusMap = topics.associateWith { topic ->
                    wordDao.countTotalWordsInTopic(level, topic) > 0
                }
                _uiState.update {
                    it.copy(
                        exploreTopics = topics,
                        exploreTopicAddedStatus = statusMap,
                        isLoading = false
                    )
                }
            } else if (result is RepositoryResult.Error) {
                _uiState.update { it.copy(error = result.message, isLoading = false) }
            }
        }
    }

    fun addWordsByLevel(level: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingItems = it.loadingItems + level) }
            val result = wordBankRepository.getAllWordsForLevel(level)
            if (result is RepositoryResult.Success) {
                wordDao.insertAll(wordsToEntities(result.data))
                // Refresh the status and the main decks view
                fetchExploreLevels()
                loadDeckHierarchy()
            }
            _uiState.update { it.copy(loadingItems = it.loadingItems - level) }
        }
    }

    fun removeWordsByLevel(level: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingItems = it.loadingItems + level) }
            wordDao.deleteByLevel(level)
            // Refresh the status and the main decks view
            fetchExploreLevels()
            loadDeckHierarchy()
            _uiState.update { it.copy(loadingItems = it.loadingItems - level) }
        }
    }

    fun addWordsByTopic(level: String, topic: String) {
        viewModelScope.launch {
            val loadingKey = "${level}_$topic"
            _uiState.update { it.copy(loadingItems = it.loadingItems + loadingKey) }
            val result = wordBankRepository.getWords(level, topic)
            if (result is RepositoryResult.Success) {
                wordDao.insertAll(wordsToEntities(result.data))
                fetchExploreTopics(level)
                loadDeckHierarchy()
            }
            _uiState.update { it.copy(loadingItems = it.loadingItems - loadingKey) }
        }
    }

    fun removeWordsByTopic(level: String, topic: String) {
        viewModelScope.launch {
            val loadingKey = "${level}_$topic"
            _uiState.update { it.copy(loadingItems = it.loadingItems + loadingKey) }
            wordDao.deleteByTopic(level, topic)
            fetchExploreTopics(level)
            loadDeckHierarchy()
            _uiState.update { it.copy(loadingItems = it.loadingItems - loadingKey) }
        }
    }

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
                repetitions = 0,
                easinessFactor = 2.5f,
                interval = 0,
                nextReviewTimestamp = System.currentTimeMillis()
            )
        }
    }
}