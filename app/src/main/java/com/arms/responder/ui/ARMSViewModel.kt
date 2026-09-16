package com.arms.responder.ui

import com.google.firebase.messaging.FirebaseMessaging

import kotlinx.coroutines.tasks.await
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arms.responder.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ARMSUiState(
    val loading: Boolean = false,
    val loggedIn: Boolean = false,
    val user: User? = null,
    val ambulance: Ambulance? = null,
    val incident: Incident? = null,
    val error: String? = null
)

class ARMSViewModel : ViewModel() {

    private val repo = ARMSRepository()

    private val _state = MutableStateFlow(ARMSUiState())
    val state: StateFlow<ARMSUiState> = _state.asStateFlow()

    init {
        restoreSession()
    }

    private fun restoreSession() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                error = null
            )

            repo.restoreSession()
                .onSuccess { user ->
                    if (user == null) {
                        _state.value = ARMSUiState()
                    } else {
                        try {
                            val fcmToken = FirebaseMessaging
                                .getInstance()
                                .token
                                .await()

                            repo.registerFcmToken(fcmToken)
                                .getOrThrow()

                            val ambulance = repo.getAmbulance()
                            val incident = repo.getActiveIncident()

                            _state.value = ARMSUiState(
                                loading = false,
                                loggedIn = true,
                                user = user,
                                ambulance = ambulance,
                                incident = incident,
                                error = null
                            )
                        } catch (e: Exception) {
                            _state.value = ARMSUiState(
                                loading = false,
                                loggedIn = false,
                                user = null,
                                ambulance = null,
                                incident = null,
                                error = "Session restore error: ${e::class.simpleName}: ${e.message ?: "Unknown error"}"
                            )
                        }
                    }
                }
                .onFailure { e ->
                    _state.value = ARMSUiState(
                        loading = false,
                        loggedIn = false,
                        error = "Session restore error: ${e.message ?: "Unknown error"}"
                    )
                }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                error = null
            )

            repo.login(email, password)
               .onSuccess { user ->
    try {
        val fcmToken = FirebaseMessaging
            .getInstance()
            .token
            .await()

        repo.registerFcmToken(fcmToken)
    .getOrThrow()

val ambulance = repo.getAmbulance()
        val incident = repo.getActiveIncident()
                        _state.value = ARMSUiState(
                            loading = false,
                            loggedIn = true,
                            user = user,
                            ambulance = ambulance,
                            incident = incident,
                            error = null
                        )
                    } catch (e: Exception) {
                        _state.value = ARMSUiState(
                            loading = false,
                            loggedIn = false,
                            user = user,
                            ambulance = null,
                            incident = null,
                            error = "Post-login error: ${e::class.simpleName}: ${e.message ?: "Unknown error"}"
                        )
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "Login failed"
                    )
                }
        }
    }

    fun refreshIncident() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                incident = repo.getActiveIncident()
            )
        }
    }

    fun setDutyStatus(status: DutyStatus) {
        viewModelScope.launch {
            _state.value = _state.value.copy(error = null)

            repo.updateAmbulanceStatus(status)
                .onSuccess {
                    _state.value = _state.value.copy(
                        ambulance = _state.value.ambulance?.copy(
                            status = status
                        )
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        error = it.message
                            ?: "Unable to update ambulance duty status"
                    )
                }
        }
    }

    fun updateIncidentStatus(status: IncidentStatus) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                error = null
            )

            repo.updateIncidentStatus(status)
                .onSuccess {

                    val ambulanceStatus = when (status) {
                        IncidentStatus.ACCEPTED -> DutyStatus.EN_ROUTE
                        IncidentStatus.EN_ROUTE -> DutyStatus.EN_ROUTE
                        IncidentStatus.ARRIVED -> DutyStatus.AT_SCENE
                        IncidentStatus.TRANSPORTING -> DutyStatus.TRANSPORTING
                        IncidentStatus.AT_HOSPITAL -> DutyStatus.AT_HOSPITAL
                        IncidentStatus.COMPLETED -> DutyStatus.AVAILABLE
                        IncidentStatus.DISPATCHED -> DutyStatus.AVAILABLE
                    }

                    repo.updateAmbulanceStatus(ambulanceStatus)
                        .onSuccess {

                            val updatedIncident =
                                try {
                                    repo.getActiveIncident()
                                } catch (_: Exception) {
                                    _state.value.incident
                                }

                            _state.value = _state.value.copy(
                                loading = false,
                                incident = updatedIncident,
                                ambulance = _state.value.ambulance?.copy(
                                    status = ambulanceStatus
                                )
                            )
                        }
                        .onFailure {
                            _state.value = _state.value.copy(
                                loading = false,
                                error = it.message
                                    ?: "Incident updated, but ambulance status could not be synchronized"
                            )
                        }
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "Unable to update incident"
                    )
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.logout()
            _state.value = ARMSUiState()
        }
    }
}
