package com.veyra.notifmonitor.data.remote

/**
 * Representation of the backend POST /api/v1/notifications/batch request.
 */
data class BatchSyncRequest(
    val events: List<SyncEventPayload>
)

data class SyncEventPayload(
    val localId: Long, // Sent as clientId to server to map responses back
    val packageName: String,
    val notificationKey: String,
    val postedAt: Long,
    val title: String?,
    val text: String?,
    val rawExtras: String?,
    val fingerprint: String
)

/**
 * Representation of the backend response.
 */
data class BatchSyncResponse(
    val accepted: List<Long>, // List of clientIds
    val duplicates: List<Long>,
    val failed: List<FailedEvent>
)

data class FailedEvent(
    val clientId: Long,
    val reason: String
)

sealed class ApiResponse<out T> {
    data class Success<out T>(val data: T) : ApiResponse<T>()
    data class HttpError(val code: Int, val message: String) : ApiResponse<Nothing>()
    data class NetworkError(val exception: Exception) : ApiResponse<Nothing>()
    data class ProtocolError(val message: String) : ApiResponse<Nothing>()
}

interface ApiClient {
    suspend fun postNotificationBatch(token: String, request: BatchSyncRequest): ApiResponse<BatchSyncResponse>
    suspend fun fetchDeviceConfig(token: String): ApiResponse<String> // Returns JSON string for parsing
}
