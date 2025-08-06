// Create this file at: android/app/src/main/java/uz/click/mobilesdk/core/data/InitialResponse.kt
package uz.click.mobilesdk.core.data

import com.squareup.moshi.Json

data class InitialResponse(
    @field:Json(name = "error_code")
    val errorCode: Int?,
    @field:Json(name = "error_note")
    val errorNote: String?,
    @field:Json(name = "request_id")
    val requestId: String?
)