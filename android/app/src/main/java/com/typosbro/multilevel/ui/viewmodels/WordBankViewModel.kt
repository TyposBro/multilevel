package com.typosbro.multilevel.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.local.WordDao
import com.typosbro.multilevel.data.local.WordEntity
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

/**
 * Represents a single deck or sub-deck in the review hierarchy.
 * @param topic Is null for a top-level (level) deck.
 */
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
    // Global stats for the top bar of the decks screen
    val totalDue: Int = 0,
    val totalNew: Int = 0,
    val totalWords: Int = 0,

    // The hierarchical deck structure for the main list
    val deckHierarchy: List<DeckInfo> = emptyList(),

    // State for the actual review session
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
                // This will update the top stats bar whenever the database changes.
                _uiState.update { it.copy(totalDue = due, totalNew = new, totalWords = total) }
            }.collect()
        }
    }

    /**
     * Builds the entire hierarchical structure of decks and sub-decks with their respective counts.
     * This is the main function called by the UI to populate the "Decks" screen.
     */
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

    /**
     * Starts a review session, optionally filtered by level and/or topic.
     */
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

    /**
     * Processes a user's review of a single word using the SM-2 algorithm.
     */
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
}