package com.rel.adbhelper.connect

sealed class ConnectUiState {
    data object Idle : ConnectUiState()
    data object Connecting : ConnectUiState()
    data class Connected(val target: String) : ConnectUiState()
    data class Failed(val reason: String) : ConnectUiState()
}
