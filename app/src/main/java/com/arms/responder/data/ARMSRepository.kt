package com.arms.responder.data

import android.os.Build
import com.arms.responder.BuildConfig
import com.arms.responder.data.supabase.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class ProfileRecord(
    val id: String,
    val employee_id: String? = null,
    val full_name: String,
    val phone: String? = null,
    val role: String,
    val station_id: String? = null,
    val position_title: String? = null,
    val is_active: Boolean = true
)

@Serializable
private data class StationRecord(
    val id: String,
    val code: String,
    val name: String
)

@Serializable
private data class AmbulanceRecord(
    val id: String,
    @SerialName("vehicle_number")
    val vehicleNumber: String,
    @SerialName("registration_number")
    val registrationNumber: String? = null,
    val status: String,
    @SerialName("is_active")
    val isActive: Boolean = true
)

@Serializable
private data class DispatchRecord(
    val id: String,
    @SerialName("incident_id")
    val incidentId: String,
    @SerialName("ambulance_id")
    val ambulanceId: String,
    @SerialName("primary_responder_id")
    val primaryResponderId: String? = null,
    @SerialName("dispatched_at")
    val dispatchedAt: String
)

@Serializable
private data class IncidentRecord(
    val id: String,
    @SerialName("incident_number")
    val incidentNumber: String,
    val priority: String,
    @SerialName("incident_type")
    val incidentType: String,
    val status: String,
    @SerialName("caller_name")
    val callerName: String? = null,
    @SerialName("caller_phone")
    val callerPhone: String? = null,
    @SerialName("incident_address")
    val incidentAddress: String? = null,
    @SerialName("incident_landmark")
    val incidentLandmark: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("chief_complaint")
    val chiefComplaint: String? = null,
    val notes: String? = null
)

class ARMSRepository {

    private val supabase = SupabaseClientProvider.client

    suspend fun login(email: String, password: String): Result<User> {
        return try {
            if (email.isBlank() || password.isBlank()) {
                return Result.failure(
                    IllegalArgumentException(
                        "Email and password are required"
                    )
                )
            }

            supabase.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }

            val authUser = supabase.auth.currentUserOrNull()
                ?: return Result.failure(
                    IllegalStateException(
                        "Login succeeded but user session was not found"
                    )
                )

            val profile = supabase
                .from("profiles")
                .select {
                    filter {
                        eq("id", authUser.id)
                    }
                }
                .decodeSingle<ProfileRecord>()

            if (!profile.is_active) {
                supabase.auth.signOut()

                return Result.failure(
                    IllegalStateException(
                        "Your ARMS account is inactive. Please contact the administrator."
                    )
                )
            }

            val stationName = if (profile.station_id != null) {
                try {
                    supabase
                        .from("stations")
                        .select {
                            filter {
                                eq("id", profile.station_id)
                            }
                        }
                        .decodeSingle<StationRecord>()
                        .name
                } catch (_: Exception) {
                    "Unknown Station"
                }
            } else {
                "Not Assigned"
            }

            Result.success(
                User(
                    id = profile.id,
                    name = profile.full_name,
                    role = profile.role,
                    station = stationName
                )
            )

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreSession(): Result<User?> {
        return try {
            supabase.auth.awaitInitialization()

            val authUser = supabase.auth.currentUserOrNull()
                ?: return Result.success(null)

            val profile = supabase
                .from("profiles")
                .select {
                    filter {
                        eq("id", authUser.id)
                    }
                }
                .decodeSingle<ProfileRecord>()

            if (!profile.is_active) {
                supabase.auth.signOut()
                return Result.success(null)
            }

            val stationName = if (profile.station_id != null) {
                try {
                    supabase
                        .from("stations")
                        .select {
                            filter {
                                eq("id", profile.station_id)
                            }
                        }
                        .decodeSingle<StationRecord>()
                        .name
                } catch (_: Exception) {
                    "Unknown Station"
                }
            } else {
                "Not Assigned"
            }

            Result.success(
                User(
                    id = profile.id,
                    name = profile.full_name,
                    role = profile.role,
                    station = stationName
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        supabase.auth.signOut()
    }

    suspend fun getAmbulance(): Ambulance {
        val record = supabase
            .from("ambulances")
            .select {
                filter {
                    eq("vehicle_number", "BALING-01")
                    eq("is_active", true)
                }
            }
            .decodeSingle<AmbulanceRecord>()

        val dutyStatus = when (record.status.uppercase()) {
            "AVAILABLE" -> DutyStatus.AVAILABLE
            "EN_ROUTE" -> DutyStatus.EN_ROUTE
            "AT_SCENE" -> DutyStatus.AT_SCENE
            "ON_SCENE" -> DutyStatus.AT_SCENE
            "TRANSPORTING" -> DutyStatus.TRANSPORTING
            "AT_HOSPITAL" -> DutyStatus.AT_HOSPITAL
            else -> DutyStatus.OFFLINE
        }

        return Ambulance(
            id = record.id,
            callSign = record.vehicleNumber,
            status = dutyStatus,
            crew = emptyList()
        )
    }

    suspend fun updateAmbulanceStatus(status: DutyStatus): Result<Unit> {
        return try {
            val ambulance = getAmbulance()

            val databaseStatus = when (status) {
                DutyStatus.OFFLINE -> "OFFLINE"
                DutyStatus.AVAILABLE -> "AVAILABLE"
                DutyStatus.EN_ROUTE -> "EN_ROUTE"
                DutyStatus.AT_SCENE -> "ON_SCENE"
                DutyStatus.TRANSPORTING -> "TRANSPORTING"
                DutyStatus.AT_HOSPITAL -> "AT_HOSPITAL"
            }

            supabase
                .from("ambulances")
                .update(
                    {
                        set("status", databaseStatus)
                    }
                ) {
                    filter {
                        eq("id", ambulance.id)
                    }
                }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getActiveIncident(): Incident? {
        return try {
            val authUser = supabase.auth.currentUserOrNull()
                ?: return null

            val ambulance = getAmbulance()

            /*
             * RLS on dispatches ensures a responder can only
             * see dispatches they are assigned to.
             *
             * We additionally restrict this query to BALING-01
             * and dispatches that have not been rejected/completed.
             */
            val dispatches = supabase
                .from("dispatches")
                .select {
                    filter {
                        eq("ambulance_id", ambulance.id)
                        eq("primary_responder_id", authUser.id)
                    }
                }
                .decodeList<DispatchRecord>()

            val dispatch = dispatches
                .sortedByDescending { it.dispatchedAt }
                .firstOrNull()
                ?: return null

            val record = supabase
                .from("incidents")
                .select {
                    filter {
                        eq("id", dispatch.incidentId)
                        neq("status", "COMPLETED")
                    }
                }
                .decodeSingle<IncidentRecord>()

            Incident(
                id = record.incidentNumber,
                priority = when (record.priority.uppercase()) {
                    "RED" -> Priority.RED
                    "YELLOW" -> Priority.YELLOW
                    else -> Priority.GREEN
                },
                emergencyType = record.incidentType,
                callerName = record.callerName ?: "Unknown Caller",
                phone = record.callerPhone ?: "-",
                address = record.incidentAddress ?: "-",
                latitude = record.latitude ?: 0.0,
                longitude = record.longitude ?: 0.0,
                notes = listOfNotNull(
                    record.chiefComplaint?.takeIf { it.isNotBlank() },
                    record.incidentLandmark?.takeIf { it.isNotBlank() },
                    record.notes?.takeIf { it.isNotBlank() }
                ).joinToString("\n"),
                status = when (record.status.uppercase()) {
                    "DISPATCHED" -> IncidentStatus.DISPATCHED
                    "ACCEPTED" -> IncidentStatus.ACCEPTED
                    "EN_ROUTE" -> IncidentStatus.EN_ROUTE
                    "ARRIVED" -> IncidentStatus.ARRIVED
                    "TRANSPORTING" -> IncidentStatus.TRANSPORTING
                    "AT_HOSPITAL" -> IncidentStatus.AT_HOSPITAL
                    else -> IncidentStatus.COMPLETED
                }
            )

        } catch (_: Exception) {
            null
        }
    }

    suspend fun updateIncidentStatus(
        status: IncidentStatus
    ): Result<Unit> {
        return try {
            val incident = getActiveIncident()
                ?: return Result.failure(
                    IllegalStateException("No active incident")
                )

            supabase
                .from("incidents")
                .update(
                    {
                        set("status", status.name)
                    }
                ) {
                    filter {
                        eq("incident_number", incident.id)
                    }
                }

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Serializable
private data class DeviceRecord(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("device_token")
    val deviceToken: String,
    @SerialName("device_name")
    val deviceName: String? = null,
    val platform: String? = null,
    @SerialName("app_version")
    val appVersion: String? = null,
    @SerialName("is_active")
    val isActive: Boolean = true
)

@Serializable
private data class DeviceInsertRecord(
    @SerialName("user_id")
    val userId: String,
    @SerialName("device_token")
    val deviceToken: String,
    @SerialName("device_name")
    val deviceName: String,
    val platform: String,
    @SerialName("app_version")
    val appVersion: String,
    @SerialName("is_active")
    val isActive: Boolean = true
)
    @Serializable
    private data class AmbulanceLocationInsert(
        @SerialName("ambulance_id")
        val ambulanceId: String,
        @SerialName("responder_id")
        val responderId: String,
        val latitude: Double,
        val longitude: Double,
        @SerialName("accuracy_meters")
        val accuracyMeters: Double? = null,
        @SerialName("speed_kmh")
        val speedKmh: Double? = null,
        val heading: Double? = null
    )

    suspend fun registerFcmToken(token: String): Result<Unit> {
    return try {
        val authUser = supabase.auth.currentUserOrNull()
            ?: return Result.failure(
                IllegalStateException("No authenticated responder")
            )

        if (token.isBlank()) {
            return Result.failure(
                IllegalArgumentException("FCM token is empty")
            )
        }

        val existingDevices = supabase
            .from("devices")
            .select {
                filter {
                    eq("user_id", authUser.id)
                    eq("device_token", token)
                }
            }
            .decodeList<DeviceRecord>()

        val deviceName =
            "${Build.MANUFACTURER} ${Build.MODEL}"
                .trim()

        val appVersion =
            BuildConfig.VERSION_NAME

        if (existingDevices.isNotEmpty()) {

            val device = existingDevices.first()

            supabase
                .from("devices")
                .update(
                    {
                        set("device_name", deviceName)
                        set("platform", "ANDROID")
                        set("app_version", appVersion)
                        set("is_active", true)
                    }
                ) {
                    filter {
                        eq("id", device.id)
                    }
                }

        } else {

            supabase
                .from("devices")
                .insert(
                    DeviceInsertRecord(
                        userId = authUser.id,
                        deviceToken = token,
                        deviceName = deviceName,
                        platform = "ANDROID",
                        appVersion = appVersion,
                        isActive = true
                    )
                )
        }

        Result.success(Unit)

    } catch (e: Exception) {
        Result.failure(e)
    }
}

suspend fun sendLocation(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double? = null,
        speedKmh: Double? = null,
        heading: Double? = null
    ): Result<Unit> {
        return try {
            val authUser = supabase.auth.currentUserOrNull()
                ?: return Result.failure(
                    IllegalStateException("No authenticated responder")
                )

            val ambulance = getAmbulance()

            supabase
                .from("ambulance_locations")
                .insert(
                    AmbulanceLocationInsert(
                        ambulanceId = ambulance.id,
                        responderId = authUser.id,
                        latitude = latitude,
                        longitude = longitude,
                        accuracyMeters = accuracyMeters,
                        speedKmh = speedKmh,
                        heading = heading
                    )
                )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
