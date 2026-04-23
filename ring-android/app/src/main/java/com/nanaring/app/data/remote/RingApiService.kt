package com.nanaring.app.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// Matches backend HeartRateIn schema exactly.
data class HeartRateDto(
    val device_timestamp: Long,
    val bpm: Int,
    val rri: Int?,
    val source: String,
    val sync_id: String?,
    val firmware_version: String?,
    val hardware_version: String?,
)

// Matches backend SpO2In schema exactly.
data class SpO2Dto(
    val device_timestamp: Long,
    val percent: Int,
    val reading_type: String,       // "manual" | "hourly" | "interval"
    val sync_id: String?,
    val firmware_version: String?,
    val hardware_version: String?,
)

// Matches backend HRVIn schema exactly.
data class HRVDto(
    val device_timestamp: Long,
    val ms: Int,
    val source: String,             // "manual" | "continuous"
    val sync_id: String?,
    val firmware_version: String?,
    val hardware_version: String?,
)

// Matches backend StressIn schema exactly.
data class StressDto(
    val device_timestamp: Long,
    val level: Int,
    val source: String,             // "manual" | "continuous"
    val sync_id: String?,
    val firmware_version: String?,
    val hardware_version: String?,
)

// Matches backend TemperatureIn schema exactly.
data class TemperatureDto(
    val device_timestamp: Long,
    val celsius_primary: Float,
    val measurement_mode: String,   // "manual_once" | "auto_series" | ...
    val sync_id: String?,
    val firmware_version: String?,
    val hardware_version: String?,
)

data class BatchUploadRequest(
    val heart_rate: List<HeartRateDto> = emptyList(),
    val spo2: List<SpO2Dto> = emptyList(),
    val blood_pressure: List<Map<String, Any>> = emptyList(),
    val temperature: List<TemperatureDto> = emptyList(),
    val hrv: List<HRVDto> = emptyList(),
    val stress: List<StressDto> = emptyList(),
    val activity: List<Map<String, Any>> = emptyList(),
    val activity_detail: List<Map<String, Any>> = emptyList(),
    val sleep: List<Map<String, Any>> = emptyList(),
)

data class BatchUploadResponse(
    val received: Int,
    val inserted: Int,
    val skipped: Int,
    val errors: List<String>,
)

interface RingApiService {
    @POST("api/v1/data/batch")
    suspend fun batchUpload(
        @Header("Authorization") bearerToken: String,
        @Body request: BatchUploadRequest,
    ): BatchUploadResponse
}
