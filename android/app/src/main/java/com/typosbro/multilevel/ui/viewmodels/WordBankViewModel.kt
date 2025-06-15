// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/ui/viewmodels/WordBankViewModel.kt
package com.typosbro.multilevel.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.typosbro.multilevel.data.local.WordEntity
import com.typosbro.multilevel.data.local.WordDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class WordBankUiState(
    val dueWords: List<WordEntity> = emptyList(),
    val currentReviewIndex: Int = 0,
    val isSessionActive: Boolean = false,
    val isSessionFinished: Boolean = false
) {
    val currentWord: WordEntity?
        get() = dueWords.getOrNull(currentReviewIndex)
}
@HiltViewModel
class WordBankViewModel @Inject constructor(
    private val wordDao: WordDao
) : ViewModel(){

    private val _uiState = MutableStateFlow(WordBankUiState())
    val uiState = _uiState.asStateFlow()

    val dueWordsCount: StateFlow<Int> =
        wordDao.getDueWordsCount(System.currentTimeMillis())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Spaced Repetition intervals in hours. Stage 0 is "wrong", so it's reviewed soon.
    private val srsIntervalsHours = mapOf(
        0 to 0,             // Wrong answer -> review again in this session (or very soon)
        1 to 4,             // 4 hours
        2 to 8,             // 8 hours
        3 to 24,            // 1 day
        4 to 24 * 3,        // 3 days
        5 to 24 * 7,        // 1 week
        6 to 24 * 14,       // 2 weeks
        7 to 24 * 30,       // 1 month
        8 to 24 * 90        // 3 months (considered "mastered")
    )
    private val REPEAT_SOON_MINUTES = 10L

    init {
        // For demonstration, let's add a few sample words if the DB is empty.
        // In a real app, these would come from the user adding them.
        viewModelScope.launch {
            if (dueWordsCount.value == 0) {
                addSampleWords()
            }
        }
    }

    fun startReviewSession() {
        viewModelScope.launch {
            wordDao.getDueWords(System.currentTimeMillis())
                .map { dueList ->
                    _uiState.update {
                        it.copy(
                            dueWords = dueList,
                            currentReviewIndex = 0,
                            isSessionActive = dueList.isNotEmpty(),
                            isSessionFinished = false
                        )
                    }
                }.stateIn(viewModelScope) // Collect the flow
        }
    }

    fun handleReview(word: WordEntity, knewIt: Boolean) {
        val currentStage = word.srsStage
        val nextStage = if (knewIt) {
            currentStage + 1
        } else {
            // Be strict: if they get it wrong, reset progress.
            1
        }

        val hoursUntilNextReview = srsIntervalsHours[nextStage]
            ?: srsIntervalsHours.values.last() // If stage > max, use max interval

        val nextReviewTime = if (nextStage == 0) {
            // If they got it wrong, review again in 10 minutes.
            System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(REPEAT_SOON_MINUTES)
        } else {
            System.currentTimeMillis() + TimeUnit.HOURS.toMillis(hoursUntilNextReview.toLong())
        }

        val updatedWord = word.copy(
            srsStage = nextStage,
            nextReviewTimestamp = nextReviewTime
        )

        viewModelScope.launch {
            wordDao.update(updatedWord)
        }

        // Move to the next card
        val nextIndex = _uiState.value.currentReviewIndex + 1
        if (nextIndex >= _uiState.value.dueWords.size) {
            // Session is finished
            _uiState.update { it.copy(isSessionFinished = true, isSessionActive = false) }
        } else {
            _uiState.update { it.copy(currentReviewIndex = nextIndex) }
        }
    }

    fun endReviewSession() {
        _uiState.update { it.copy(isSessionActive = false, isSessionFinished = false, dueWords = emptyList()) }
    }

    // Example function to add words. You'll call this from your Results Screen.
    private suspend fun addSampleWords() {
        val now = System.currentTimeMillis()
        wordDao.insert(WordEntity(text = "Ubiquitous", definition = "Present, appearing, or found everywhere.", example = "Smartphones have become ubiquitous in modern society.", cefrLevel = "C1", topic = "Technology", nextReviewTimestamp = now))
        wordDao.insert(WordEntity(text = "Ephemeral", definition = "Lasting for a very short time.", example = "The beauty of the cherry blossoms is ephemeral.", cefrLevel = "C1", topic = "Nature", nextReviewTimestamp = now))
        wordDao.insert(WordEntity(text = "Pragmatic", definition = "Dealing with things sensibly and realistically in a practical way.", example = "She took a pragmatic approach to solving the problem.", cefrLevel = "B2", topic = "General", nextReviewTimestamp = now))
    }
}