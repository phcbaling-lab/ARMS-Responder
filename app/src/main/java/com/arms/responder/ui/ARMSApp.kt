package com.arms.responder.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.*
import com.arms.responder.data.IncidentStatus
import com.arms.responder.ui.screens.*

@Composable
fun ARMSApp(
    vm: ARMSViewModel,
    initialIncidentId: String? = null
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted) {
                val intent = Intent(
                    context,
                    com.arms.responder.location.LocationTrackingService::class.java
                )

                androidx.core.content.ContextCompat.startForegroundService(
                    context,
                    intent
                )
            }
        }

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) {
            val fineGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val coarseGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!fineGranted && !coarseGranted) {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } else {
                val intent = Intent(
                    context,
                    com.arms.responder.location.LocationTrackingService::class.java
                )

                ContextCompat.startForegroundService(
                    context,
                    intent
                )
            }
        }
    }

    if (!state.loggedIn) {
        LoginScreen(
            loading = state.loading,
            error = state.error,
            onLogin = vm::login
        )
        return
    }

    val nav = rememberNavController()

    LaunchedEffect(state.loggedIn, initialIncidentId) {
        if (
            state.loggedIn &&
            !initialIncidentId.isNullOrBlank()
        ) {
            nav.navigate("incident") {
                popUpTo("home") {
                    inclusive = false
                }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val current by nav.currentBackStackEntryAsState()
                val route = current?.destination?.route

                NavigationBarItem(
                    selected = route == "home",
                    onClick = { nav.navigate("home") },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Home") }
                )

                NavigationBarItem(
                    selected = route == "incident",
                    onClick = { nav.navigate("incident") },
                    icon = { Icon(Icons.Default.Warning, null) },
                    label = { Text("Incident") }
                )

                NavigationBarItem(
                    selected = route == "profile",
                    onClick = { nav.navigate("profile") },
                    icon = { Icon(Icons.Default.Person, null) },
                    label = { Text("Profile") }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                DashboardScreen(
                    user = state.user,
                    ambulance = state.ambulance,
                    incident = state.incident,
                    onOpenIncident = { nav.navigate("incident") },
                    onSetAvailable = {
                        vm.setDutyStatus(
                            com.arms.responder.data.DutyStatus.AVAILABLE
                        )
                    },
                    onSetOffline = {
                        vm.setDutyStatus(
                            com.arms.responder.data.DutyStatus.OFFLINE
                        )
                    }
                )
            }

            composable("incident") {
                IncidentScreen(
                    incident = state.incident,
                    loading = state.loading,
                    onStatus = vm::updateIncidentStatus
                )
            }

            composable("profile") {
                ProfileScreen(
                    user = state.user,
                    onLogout = {
                        val intent = Intent(
                            context,
                            com.arms.responder.location.LocationTrackingService::class.java
                        )

                        context.stopService(intent)
                        vm.logout()
                    }
                )
            }
        }
    }
}
