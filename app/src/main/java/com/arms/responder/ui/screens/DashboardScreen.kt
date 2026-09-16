package com.arms.responder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arms.responder.data.*

@Composable
fun DashboardScreen(
    user: User?,
    ambulance: Ambulance?,
    incident: Incident?,
    onOpenIncident: () -> Unit,
    onSetAvailable: () -> Unit,
    onSetOffline: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Good day, ${user?.name ?: "Responder"}",
                style = MaterialTheme.typography.headlineSmall
            )
            Text("Station: ${user?.station ?: "-"}")
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {

                    Text(
                        "Ambulance",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(ambulance?.callSign ?: "Not assigned")

                    Spacer(Modifier.height(6.dp))

                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                ambulance?.status?.name ?: "UNKNOWN"
                            )
                        }
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {

                        Button(
                            onClick = onSetAvailable,
                            modifier = Modifier.weight(1f),
                            enabled = ambulance?.status != DutyStatus.AVAILABLE
                        ) {
                            Text("AVAILABLE")
                        }

                        OutlinedButton(
                            onClick = onSetOffline,
                            modifier = Modifier.weight(1f),
                            enabled = ambulance?.status != DutyStatus.OFFLINE
                        ) {
                            Text("OFFLINE")
                        }
                    }
                }
            }
        }

        item {
            if (incident != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {

                        Text(
                            "ACTIVE EMERGENCY",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Text(incident.id)
                        Text("Priority: ${incident.priority}")
                        Text(incident.emergencyType)
                        Text(incident.address)

                        Spacer(Modifier.height(12.dp))

                        Button(onClick = onOpenIncident) {
                            Text("OPEN INCIDENT")
                        }
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "No active incident",
                        Modifier.padding(18.dp)
                    )
                }
            }
        }
    }
}
