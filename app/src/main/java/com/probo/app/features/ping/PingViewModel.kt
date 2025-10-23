package com.probo.app.features.ping

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.probo.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.time.Instant
import java.util.concurrent.TimeUnit

private const val DEFAULT_HOST = "8.8.8.8"
private const val MAX_HISTORY = 5
private const val TIMEOUT_SECONDS = 10L

class PingViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PingUiState(hostInput = DEFAULT_HOST))
    val uiState: StateFlow<PingUiState> = _uiState.asStateFlow()

    fun onHostChanged(host: String) {
        _uiState.update { it.copy(hostInput = host) }
    }

    fun submitPing() {
        if (_uiState.value.isPinging) return

        val host = _uiState.value.hostInput.trim()
        if (host.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = R.string.ping_enter_host) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPinging = true, snackbarMessage = null) }
            val result = withContext(Dispatchers.IO) { executePing(host) }
            _uiState.update { state ->
                val updatedHistory = (listOf(result) + state.history).distinctBy { it.timestamp }.take(MAX_HISTORY)
                state.copy(
                    isPinging = false,
                    lastResult = result,
                    history = updatedHistory,
                    snackbarMessage = result.snackbarMessage
                )
            }
        }
    }

    fun onSnackbarConsumed() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun executePing(host: String): PingResult {
        val timestamp = Instant.now()
        return try {
            runPingProcess(host, timestamp)
        } catch (ioException: IOException) {
            PingResult(
                host = host,
                timestamp = timestamp,
                success = false,
                output = "",
                statistics = null,
                failure = PingFailure.CommandUnavailable
            )
        } catch (interruption: InterruptedException) {
            Thread.currentThread().interrupt()
            PingResult(
                host = host,
                timestamp = timestamp,
                success = false,
                output = "",
                statistics = null,
                failure = PingFailure.Generic
            )
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun runPingProcess(host: String, timestamp: Instant): PingResult {
        val commands = listOf(
            listOf("/system/bin/ping", "-c", "4", host),
            listOf("/system/bin/ping6", "-c", "4", host),
            listOf("ping", "-c", "4", host)
        )

        var lastError: IOException? = null
        for (command in commands) {
            try {
                return runPingCommand(command, host, timestamp)
            } catch (exception: IOException) {
                lastError = exception
            }
        }

        throw lastError ?: IOException("Ping command unavailable")
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun runPingCommand(command: List<String>, host: String, timestamp: Instant): PingResult {
        var process: Process? = null
        return try {
            process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = readProcessOutput(process)
            val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
            }

            val success = finished && process.exitValue() == 0
            val failure = when {
                !finished -> PingFailure.Timeout
                success -> null
                else -> PingFailure.Generic
            }

            PingResult(
                host = host,
                timestamp = timestamp,
                success = success,
                output = output,
                statistics = parseStatistics(output),
                failure = failure
            )
        } finally {
            process?.destroy()
        }
    }

    private fun readProcessOutput(process: Process): String {
        val outputBuilder = StringBuilder()
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                outputBuilder.appendLine(line)
                line = reader.readLine()
            }
        }
        return outputBuilder.toString().trim()
    }
}

data class PingUiState(
    val hostInput: String = DEFAULT_HOST,
    val isPinging: Boolean = false,
    val lastResult: PingResult? = null,
    val history: List<PingResult> = emptyList(),
    @StringRes val snackbarMessage: Int? = null
)

data class PingResult(
    val host: String,
    val timestamp: Instant,
    val success: Boolean,
    val output: String,
    val statistics: PingStatistics?,
    val failure: PingFailure?
) {
    val snackbarMessage: Int?
        @StringRes get() = when (failure) {
            PingFailure.Timeout -> R.string.ping_timeout
            PingFailure.CommandUnavailable -> R.string.ping_unavailable
            PingFailure.Generic -> R.string.ping_failed
            null -> null
        }
}

data class PingStatistics(
    val packetsTransmitted: Int?,
    val packetsReceived: Int?,
    val packetLossPercent: Double?,
    val averageLatencyMs: Double?
)

enum class PingFailure {
    Timeout,
    CommandUnavailable,
    Generic
}

private val PACKET_REGEX = Regex("""(\d+)\s+packets transmitted,\s+(\d+)\s+(?:packets )?received,\s+([\d.]+)% packet loss""")
private val RTT_REGEX = Regex("""=\s*([\d.]+)/([\d.]+)/([\d.]+)/([\d.]+) ms""")

private fun parseStatistics(output: String): PingStatistics? {
    if (output.isBlank()) return null

    val packetsLine = output.lineSequence()
        .firstOrNull { it.contains("packets transmitted") || it.contains("packets sent") }
    val rttLine = output.lineSequence()
        .firstOrNull { it.contains("min/avg/max") || it.contains("round-trip") || it.contains("rtt") }

    val packetMatch = packetsLine?.let { PACKET_REGEX.find(it) }
    val transmitted = packetMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
    val received = packetMatch?.groupValues?.getOrNull(2)?.toIntOrNull()
    val lossPercent = packetMatch?.groupValues?.getOrNull(3)?.toDoubleOrNull()
    val avgLatency = rttLine?.let { RTT_REGEX.find(it)?.groupValues?.getOrNull(2)?.toDoubleOrNull() }

    if (transmitted == null && received == null && lossPercent == null && avgLatency == null) {
        return null
    }

    return PingStatistics(
        packetsTransmitted = transmitted,
        packetsReceived = received,
        packetLossPercent = lossPercent,
        averageLatencyMs = avgLatency
    )
}
