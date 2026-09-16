package com.arms.responder.data

import kotlinx.serialization.Serializable

enum class DutyStatus { OFFLINE, AVAILABLE, EN_ROUTE, AT_SCENE, TRANSPORTING, AT_HOSPITAL }

enum class IncidentStatus {
    DISPATCHED, ACCEPTED, EN_ROUTE, ARRIVED, TRANSPORTING, AT_HOSPITAL, COMPLETED
}

enum class Priority { RED, YELLOW, GREEN }

@Serializable
data class User(
    val id: String,
    val name: String,
    val role: String = "RESPONDER",
    val station: String = "Baling"
)

@Serializable
data class Incident(
    val id: String,
    val priority: Priority,
    val emergencyType: String,
    val callerName: String,
    val phone: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val notes: String,
    val status: IncidentStatus
)

@Serializable
data class Ambulance(
    val id: String,
    val callSign: String,
    val status: DutyStatus,
    val crew: List<String>
)

@Serializable
data class LocationUpdate(
    val ambulanceId: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)
