package com.probo.app.features.ping

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.probo.app.R
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PingScreen(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState,
    viewModel: PingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { messageRes ->
            snackbarHostState.showSnackbar(context.getString(messageRes))
            viewModel.onSnackbarConsumed()
        }
    }

    PingContent(
        modifier = modifier,
        uiState = uiState,
        onHostChanged = viewModel::onHostChanged,
        onSubmitPing = viewModel::submitPing
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PingContent(
    modifier: Modifier = Modifier,
    uiState: PingUiState,
    onHostChanged: (String) -> Unit,
    onSubmitPing: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val timeFormatter = rememberTimeFormatter()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(id = R.string.ping),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(id = R.string.ping_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = uiState.hostInput,
            onValueChange = onHostChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(text = stringResource(id = R.string.host_hint)) },
            leadingIcon = {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Outlined.Wifi,
                    contentDescription = null
                )
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(
                onGo = {
                    focusManager.clearFocus()
                    onSubmitPing()
                }
            )
        )

        Button(
            onClick = {
                focusManager.clearFocus()
                onSubmitPing()
            },
            enabled = !uiState.isPinging,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (uiState.isPinging) stringResource(id = R.string.pinging) else stringResource(id = R.string.ping_action))
        }

        AnimatedVisibility(visible = uiState.isPinging) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(id = R.string.ping_in_progress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center
                )
            }
        }

        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.ping_last_result),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                uiState.lastResult?.let { result ->
                    Text(
                        text = stringResource(id = R.string.ping_checked_at, timeFormatter.format(result.timestamp.atZone(ZoneId.systemDefault()))),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    ResultStatistics(result)

                    Divider()

                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = if (result.output.isNotBlank()) result.output else stringResource(id = R.string.ping_output_unavailable),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } ?: run {
                    Text(
                        text = stringResource(id = R.string.ping_last_result_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Divider(modifier = Modifier.fillMaxWidth())

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.material3.Icon(
                painter = painterResource(id = R.drawable.ic_wave),
                contentDescription = stringResource(id = R.string.ping_in_history_label),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(id = R.string.ping_history),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            val historicalResults = uiState.history.drop(1)
            if (historicalResults.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.ping_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    historicalResults.forEach { historyResult ->
                        HistoryResultCard(
                            result = historyResult,
                            timeFormatter = timeFormatter
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ResultStatistics(result: PingResult) {
    result.statistics?.let { stats ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            stats.averageLatencyMs?.let { avg ->
                Text(
                    text = stringResource(id = R.string.ping_latency_avg, avg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (stats.packetsTransmitted != null && stats.packetsReceived != null) {
                Text(
                    text = stringResource(
                        id = R.string.ping_packet_summary,
                        stats.packetsTransmitted,
                        stats.packetsReceived
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            stats.packetLossPercent?.let { loss ->
                Text(
                    text = stringResource(id = R.string.ping_packet_loss, loss),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HistoryResultCard(
    result: PingResult,
    timeFormatter: DateTimeFormatter
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = result.host,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = timeFormatter.format(result.timestamp.atZone(ZoneId.systemDefault())),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ResultStatistics(result)
        }
    }
}

@Composable
private fun rememberTimeFormatter(): DateTimeFormatter {
    return remember { DateTimeFormatter.ofPattern("MMM d • HH:mm:ss") }
}
