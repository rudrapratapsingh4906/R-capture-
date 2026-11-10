package com.rcapture

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rcapture.data.CaptureEntity
import com.rcapture.ui.RcaptureUiState
import com.rcapture.ui.RcaptureViewModel
import com.rcapture.ui.navigation.MainAppScaffold
import com.rcapture.ui.navigation.MainAppScaffoldContent
import com.rcapture.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Android 13+ Notification Permission Request
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            MyApplicationTheme {
                val viewModel: RcaptureViewModel = viewModel()
                MainAppScaffold(viewModel = viewModel)
            }
        }
    }
}

@Preview(name = "Compact Dark Dashboard", showBackground = true)
@Preview(name = "Expanded Dark Dashboard", widthDp = 840, heightDp = 600, showBackground = true)
@Composable
fun MainActivityPreview() {
    MyApplicationTheme(darkTheme = true) {
        MainAppScaffoldContent(
            uiState = RcaptureUiState(
                statusMessage = "System Ready • Workstation Operational",
                savedCaptures = listOf(
                    CaptureEntity(
                        id = 1,
                        text = "Security Audit Diagnostic Log #8492\nEncrypted storage verified.",
                        timestamp = 1731170000000L,
                        source = "Floating Overlay"
                    ),
                    CaptureEntity(
                        id = 2,
                        text = "System Invoice Spec #10294 • Total Due: $1,420.00",
                        timestamp = 1731083600000L,
                        source = "Manual Save"
                    )
                )
            ),
            isAccessibilityRunning = true
        )
    }
}
