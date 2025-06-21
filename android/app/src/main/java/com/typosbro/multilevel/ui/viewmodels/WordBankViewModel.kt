// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/WordBankViewModel.kt
package com.typosbro.multilevel.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.local.WordDao
import com.typosbro.multilevel.data.local.WordEntity
import com.typosbro.multilevel.data.remote.models.ApiWord
import com.typosbro.multilevel.data.remote.models.RepositoryResult
import com.typosbro.multilevel.data.repositories.WordBankRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject


data class WordBankUiState(
    // Review Session State
    val dueWords: List<WordEntity> = emptyList(),
    val currentReviewIndex: Int = 0,
    val isSessionActive: Boolean = false,
    val isSessionFinished: Boolean = false,

    // Word Discovery State
    val levels: List<String> = emptyList(),
    val topics: List<String> = emptyList(),
    val discoverableWords: List<ApiWord> = emptyList(),
    val bookmarkedWords: Set<String> = emptySet(), // Set of bookmarked word strings

    // Common State
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val currentWord: WordEntity?
        get() = dueWords.getOrNull(currentReviewIndex)
}

@HiltViewModel
class WordBankViewModel @Inject constructor(
    private val wordDao: WordDao,
    private val wordBankRepository: WordBankRepository, // Inject the real repository
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(WordBankUiState())
    val uiState = _uiState.asStateFlow()

    val dueWordsCount: StateFlow<Int> =
        wordDao.getDueWordsCount(System.currentTimeMillis())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val srsIntervalsHours = mapOf(
        1 to 4, 2 to 8, 3 to 24, 4 to 72, 5 to 168, 6 to 336, 7 to 720, 8 to 2160
    )

    init {
        // Listen to arguments passed via navigation for the word list screen
        val level: String? = savedStateHandle["level"]
        val topic: String? = savedStateHandle["topic"]
        if (level != null && topic != null) {
            fetchWordsForDiscovery(level, topic)
        }
    }

    // --- Review Session Functions ---
    fun startReviewSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isSessionActive = true) }
            // --- CHANGE: Use .first() to get the list from the Flow ---
            val dueList = wordDao.getDueWords(System.currentTimeMillis()).first()
            _uiState.update {
                it.copy(
                    dueWords = dueList.shuffled(), // Now works
                    currentReviewIndex = 0,
                    isSessionActive = dueList.isNotEmpty(), // Now works
                    isSessionFinished = false,
                    isLoading = false
                )
            }
        }
    }

    fun handleReview(word: WordEntity, knewIt: Boolean) {
        viewModelScope.launch {
            val currentStage = word.srsStage
            val nextStage = if (knewIt) {
                (currentStage + 1).coerceAtMost(srsIntervalsHours.keys.maxOrNull() ?: 8)
            } else {
                1
            }
            val hoursUntilNextReview = srsIntervalsHours[nextStage] ?: (24 * 90)
            val nextReviewTime =
                System.currentTimeMillis() + TimeUnit.HOURS.toMillis(hoursUntilNextReview.toLong())
            val updatedWord = word.copy(srsStage = nextStage, nextReviewTimestamp = nextReviewTime)
            wordDao.update(updatedWord)

            val nextIndex = _uiState.value.currentReviewIndex + 1
            if (nextIndex >= _uiState.value.dueWords.size) {
                _uiState.update { it.copy(isSessionFinished = true, isSessionActive = false) }
            } else {
                _uiState.update { it.copy(currentReviewIndex = nextIndex) }
            }
        }
    }

    // --- Word Discovery Functions (Now fully functional) ---

    fun fetchLevels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = wordBankRepository.getLevels()) {
                is RepositoryResult.Success -> {
                    _uiState.update { it.copy(levels = result.data, isLoading = false) }
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
                    _uiState.update { it.copy(topics = result.data, isLoading = false) }
                }

                is RepositoryResult.Error -> {
                    _uiState.update { it.copy(error = result.message, isLoading = false) }
                }
            }
        }
    }

    private fun fetchWordsForDiscovery(level: String, topic: String) {
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
                    // --- This line now works because the DAO function exists ---
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

    fun bookmarkWord(apiWord: ApiWord) {
        viewModelScope.launch {
            // --- This block now works because WordEntity's constructor matches ---
            val newEntity = WordEntity(
                word = apiWord.word,
                translation = apiWord.translation,
                example1 = apiWord.example1,
                example1Translation = apiWord.example1Translation,
                example2 = apiWord.example2,
                example2Translation = apiWord.example2Translation,
                cefrLevel = apiWord.cefrLevel,
                topic = apiWord.topic,
                srsStage = 1,
                nextReviewTimestamp = System.currentTimeMillis()
            )
            wordDao.insert(newEntity)

            _uiState.update {
                it.copy(bookmarkedWords = it.bookmarkedWords + apiWord.word)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}