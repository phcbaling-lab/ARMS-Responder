package com.arms.responder

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arms.responder.ui.ARMSApp
import com.arms.responder.ui.ARMSViewModel
import com.arms.responder.ui.theme.ARMSTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_INCIDENT_ID = "incident_id"
    }

    private var notificationIncidentId: String? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            // Permission result is handled by Android.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        notificationIncidentId =
            intent.getStringExtra(EXTRA_INCIDENT_ID)

        setContent {
            ARMSTheme {
                val vm: ARMSViewModel = viewModel()
                ARMSApp(
                    vm = vm,
                    initialIncidentId = notificationIncidentId
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        notificationIncidentId =
            intent.getStringExtra(EXTRA_INCIDENT_ID)
    }
}
