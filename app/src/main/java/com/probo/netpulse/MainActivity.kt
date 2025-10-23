package com.probo.netpulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.probo.netpulse.ui.theme.NetPulseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NetPulseTheme {
                NetPulseApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetPulseApp() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedItem = rememberSaveable { mutableStateOf(0) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "NetPulse",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                NavigationDrawerItem(
                    label = { Text(text = "Ping Tool") },
                    selected = selectedItem.value == 0,
                    onClick = {
                        selectedItem.value = 0
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(text = "NetPulse") },
                    navigationIcon = {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                if (drawerState.isClosed) drawerState.open() else drawerState.close()
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { innerPadding ->
            when (selectedItem.value) {
                0 -> PingScreen(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    snackbarHostState = snackbarHostState
                )
            }
        }
    }
}

@Composable
private fun PingScreen(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState
) {
    val host = rememberSaveable { mutableStateOf("8.8.8.8") }
    val isPinging = remember { mutableStateOf(false) }
    val result = remember { mutableStateOf("Ready to ping") }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Ping",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Check reachability of an address",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = host.value,
                    onValueChange = { host.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(text = "Hostname or IP") }
                )
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isPinging.value = true
                            val output = performPing(host.value)
                            result.value = output
                            if (!output.contains("bytes from")) {
                                snackbarHostState.showSnackbar("Ping failed")
                            }
                            isPinging.value = false
                        }
                    },
                    enabled = !isPinging.value,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = if (isPinging.value) "Pinging..." else "Send Ping")
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "Last result", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = result.value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private suspend fun performPing(host: String): String = withContext(Dispatchers.IO) {
    runCatching {
        val process = Runtime.getRuntime().exec("/system/bin/ping -c 1 $host")
        val exitCode = process.waitFor()
        val output = buildString {
            BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                lines.forEach { appendLine(it) }
            }
            BufferedReader(InputStreamReader(process.errorStream)).useLines { lines ->
                lines.forEach { appendLine(it) }
            }
        }
        "Exit code: $exitCode\n$output"
    }.getOrElse { throwable ->
        "Ping failed: ${throwable.localizedMessage}"
    }
}
