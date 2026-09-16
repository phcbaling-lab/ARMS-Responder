package com.arms.responder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arms.responder.data.User

@Composable
fun ProfileScreen(user: User?, onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Responder Profile", style = MaterialTheme.typography.headlineSmall)
        Text("Name: ${user?.name ?: "-"}")
        Text("Role: ${user?.role ?: "-"}")
        Text("Station: ${user?.station ?: "-"}")
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("LOG OUT")
        }
    }
}
