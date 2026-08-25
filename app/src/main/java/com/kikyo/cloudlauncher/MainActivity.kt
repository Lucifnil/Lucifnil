package com.kikyo.cloudlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val launcherViewModel: LauncherViewModel = viewModel()
            val state by launcherViewModel.uiState.collectAsState()
            CloudLauncherTheme {
                CloudLauncherApp(
                    state = state,
                    onSelectTab = launcherViewModel::selectTab,
                    onLaunch = launcherViewModel::launchProgram,
                    onRefreshRoot = launcherViewModel::refreshRoot,
                    onRefreshSoFiles = launcherViewModel::refreshSoFiles,
                    onSelectSoFile = launcherViewModel::selectSoFile,
                    onSaveCardKey = launcherViewModel::saveCardKey,
                    onClearCardKey = launcherViewModel::clearCardKey
                )
            }
        }
    }
}
