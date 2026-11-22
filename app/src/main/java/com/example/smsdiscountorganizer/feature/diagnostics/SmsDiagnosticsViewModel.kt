package com.example.smsdiscountorganizer.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsdiscountorganizer.core.permission.SmsPermissionStatus
import com.example.smsdiscountorganizer.domain.sms.ScanSmsInbox
import com.example.smsdiscountorganizer.domain.sms.SmsInboxScanResult
import com.example.smsdiscountorganizer.domain.sms.SmsReadError
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SmsDiagnosticsUiState(
    val permissionStatus: SmsPermissionStatus = SmsPermissionStatus.Unknown,
    val inboxMessageCount: Int? = null,
    val lastSuccessfulScanAt: Instant? = null,
    val readError: String? = null,
    val isScanning: Boolean = false,
)

@HiltViewModel
class SmsDiagnosticsViewModel @Inject constructor(
    private val scanSmsInbox: ScanSmsInbox,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SmsDiagnosticsUiState())
    val uiState: StateFlow<SmsDiagnosticsUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    fun updatePermissionStatus(status: SmsPermissionStatus) {
        val wasGranted = _uiState.value.permissionStatus == SmsPermissionStatus.Granted
        _uiState.update { it.copy(permissionStatus = status) }
        if (status == SmsPermissionStatus.Granted && !wasGranted) {
            scanInbox()
        }
    }

    fun scanInbox() {
        if (_uiState.value.permissionStatus != SmsPermissionStatus.Granted || scanJob?.isActive == true) {
            return
        }
        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, readError = null) }
            when (val result = scanSmsInbox.scan()) {
                is SmsInboxScanResult.Success -> _uiState.update {
                    it.copy(
                        inboxMessageCount = result.messageCount,
                        lastSuccessfulScanAt = result.scannedAt,
                        readError = null,
                        isScanning = false,
                    )
                }

                is SmsInboxScanResult.Failure -> _uiState.update {
                    it.copy(readError = result.error.toDiagnosticMessage(), isScanning = false)
                }
            }
        }
    }

    private fun SmsReadError.toDiagnosticMessage(): String = when (this) {
        SmsReadError.PermissionDenied -> "SMS access was denied or revoked before the scan completed."
        SmsReadError.InboxUnavailable -> "The Inbox could not be opened on this device."
        SmsReadError.ReadFailed -> "The Inbox could not be read. Try scanning again."
    }
}
