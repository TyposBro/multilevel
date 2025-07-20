package org.milliytechnology.spiko.data.remote.models

import com.google.gson.annotations.SerializedName

/**
 * Represents a single word entry fetched from the backend API.
 * This is used for the word discovery feature.
 */
data class ApiWord(
    @SerializedName("_id") val id: String,
    @SerializedName("word") val word: String,
    @SerializedName("cefrLevel") val cefrLevel: String,
    @SerializedName("topic") val topic: String,
    @SerializedName("translation") val translation: String,
    @SerializedName("example1") val example1: String?,
    @SerializedName("example1Translation") val example1Translation: String?,
    @SerializedName("example2") val example2: String?,
    @SerializedName("example2Translation") val example2Translation: String?
)