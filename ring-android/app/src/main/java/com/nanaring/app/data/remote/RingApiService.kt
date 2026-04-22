package com.nanaring.app.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class HeartRateDto(
    val device_timestamp: Long,
    val bpm: Int,
    val rri: Int?,
    val sp_o2: Int?,
    val systolic: Int?,
    val diastolic: Int?,
    val hrv: Int?,
    val stress: Int?,
    val temperature: Float?,
    val source: String,
    val sync_id: String,
    val firmware_version: String?,
    val hardware_version: String?,
)

data class BatchUploadRequest(
    val heart_rate: List<HeartRateDto> = emptyList(),
    val spo2: List<Map<String, Any>> = emptyList(),
    val blood_pressure: List<Map<String, Any>> = emptyList(),
    val temperature: List<Map<String, Any>> = emptyList(),
    val hrv: List<Map<String, Any>> = emptyList(),
    val stress: List<Map<String, Any>> = emptyList(),
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
