package com.arms.responder.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arms.responder.data.Incident
import com.arms.responder.data.IncidentStatus

@Composable
fun IncidentScreen(
    incident: Incident?,
    loading: Boolean,
    onStatus: (IncidentStatus) -> Unit
) {
    if (incident == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("No active incident")
        }
        return
    }

    val context = LocalContext.current

    fun navigate() {
        val uri = Uri.parse(
            "google.navigation:q=${incident.latitude},${incident.longitude}"
        )
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        })
    }

    val next = when (incident.status) {
        IncidentStatus.DISPATCHED -> IncidentStatus.ACCEPTED
        IncidentStatus.ACCEPTED -> IncidentStatus.EN_ROUTE
        IncidentStatus.EN_ROUTE -> IncidentStatus.ARRIVED
        IncidentStatus.ARRIVED -> IncidentStatus.TRANSPORTING
        IncidentStatus.TRANSPORTING -> IncidentStatus.AT_HOSPITAL
        IncidentStatus.AT_HOSPITAL -> IncidentStatus.COMPLETED
        IncidentStatus.COMPLETED -> null
    }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Incident ${incident.id}", style = MaterialTheme.typography.headlineSmall)

        AssistChip(
            onClick = {},
            label = { Text("STATUS: ${incident.status}") }
        )

        Text("Priority: ${incident.priority}")
        Text("Emergency: ${incident.emergencyType}")
        Text("Caller: ${incident.callerName}")
        Text("Phone: ${incident.phone}")
        Text("Location: ${incident.address}")
        Text("Coordinates: ${incident.latitude}, ${incident.longitude}")

        Card(Modifier.fillMaxWidth()) {
            Text(incident.notes, Modifier.padding(16.dp))
        }

        Button(onClick = ::navigate, modifier = Modifier.fillMaxWidth()) {
            Text("OPEN NAVIGATION")
        }

        if (next != null) {
            Button(
                onClick = { onStatus(next) },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("UPDATING...")
                } else {
                    Text(
                        when (next) {
                            IncidentStatus.ACCEPTED -> "ACCEPT DISPATCH"
                            IncidentStatus.EN_ROUTE -> "START RESPONSE"
                            IncidentStatus.ARRIVED -> "MARK ARRIVED"
                            IncidentStatus.TRANSPORTING -> "START TRANSPORT"
                            IncidentStatus.AT_HOSPITAL -> "ARRIVED HOSPITAL"
                            IncidentStatus.COMPLETED -> "COMPLETE INCIDENT"
                            else -> next.name
                        }
                    )
                }
            }
        } else {
            Text("Incident completed.")
        }
    }
}
