package org.milliytechnology.spiko.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.milliytechnology.spiko.data.remote.models.Criterion
import org.milliytechnology.spiko.data.remote.models.FeedbackBreakdown
import org.milliytechnology.spiko.data.remote.models.TranscriptEntry

class Converters {
    private val gson = Gson()

    // --- For IeltsExamResultEntity ---

    @TypeConverter
    fun fromCriterionList(value: List<Criterion>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toCriterionList(value: String): List<Criterion>? {
        return try {
            val listType = object : TypeToken<List<Criterion>>() {}.type
            gson.fromJson(value, listType)
        } catch (e: Exception) {
            null
        }
    }

    @TypeConverter
    fun fromTranscriptEntryList(value: List<TranscriptEntry>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toTranscriptEntryList(value: String): List<TranscriptEntry>? {
        return try {
            val listType = object : TypeToken<List<TranscriptEntry>>() {}.type
            gson.fromJson(value, listType)
        } catch (e: Exception) {
            null
        }
    }

    // --- For MultilevelExamResultEntity ---

    @TypeConverter
    fun fromFeedbackBreakdownList(value: List<FeedbackBreakdown>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toFeedbackBreakdownList(value: String): List<FeedbackBreakdown>? {
        return try {
            val listType = object : TypeToken<List<FeedbackBreakdown>>() {}.type
            gson.fromJson(value, listType)
        } catch (e: Exception) {
            null
        }
    }

    // --- A generic converter for ExamContentIds-like structures ---

    @TypeConverter
    fun fromStringMap(value: Map<String, Any>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringMap(value: String): Map<String, Any>? {
        return try {
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            gson.fromJson(value, mapType)
        } catch (e: Exception) {
            null
        }
    }
}