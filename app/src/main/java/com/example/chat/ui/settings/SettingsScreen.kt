package com.example.chat.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import com.example.chat.R
import com.example.chat.model.ApiConfig
import com.example.chat.data.repository.SettingsManager
import com.example.chat.ui.theme.AccentOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit = {},
) {
    BackHandler {
        onBack()
    }
    val scope = rememberCoroutineScope()
    val currentConfig by settingsManager.configFlow.collectAsState(initial = null)
    
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var hasInitialized by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(currentConfig) {
        currentConfig?.let { config ->
            if (!hasInitialized) {
                baseUrl = config.baseUrl
                apiKey = config.apiKey
                model = config.model
                hasInitialized = true
            }
        }
    }

    SettingsContent(
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        showApiKey = showApiKey,
        saved = saved,
        onBaseUrlChange = { baseUrl = it; saved = false },
        onApiKeyChange = { apiKey = it; saved = false },
        onModelChange = { model = it; saved = false },
        onToggleApiKey = { showApiKey = !showApiKey },
        onSave = {
            scope.launch {
                settingsManager.saveConfig(
                    ApiConfig(
                        baseUrl = baseUrl.trim().trimEnd('/'),
                        apiKey = apiKey.trim(),
                        model = model.trim()
                    )
                )
                saved = true
            }
        },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    baseUrl: String,
    apiKey: String,
    model: String,
    showApiKey: Boolean,
    saved: Boolean,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onToggleApiKey: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_api_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text(stringResource(R.string.settings_base_url)) },
                placeholder = { Text("https://api.deepseek.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentOrange,
                    focusedLabelColor = AccentOrange,
                    cursorColor = AccentOrange,
                )
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text(stringResource(R.string.settings_api_key)) },
                placeholder = { Text("sk-...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None
                    else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = onToggleApiKey) {
                        Text(
                            if (showApiKey) stringResource(R.string.settings_hide)
                            else stringResource(R.string.settings_show),
                            color = AccentOrange
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentOrange,
                    focusedLabelColor = AccentOrange,
                    cursorColor = AccentOrange,
                )
            )

            OutlinedTextField(
                value = model,
                onValueChange = onModelChange,
                label = { Text(stringResource(R.string.settings_model)) },
                placeholder = { Text("deepseek-v4-pro") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentOrange,
                    focusedLabelColor = AccentOrange,
                    cursorColor = AccentOrange,
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                enabled = apiKey.isNotBlank()
            ) {
                Text(
                    if (saved) stringResource(R.string.settings_saved)
                    else stringResource(R.string.settings_save),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (saved) {
                Text(
                    text = stringResource(R.string.settings_save_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MaterialTheme {
        SettingsContent(
            baseUrl = "https://api.openai.com",
            apiKey = "sk-abc123",
            model = "deepseek-v4-pro",
            showApiKey = false,
            saved = false,
            onBaseUrlChange = {},
            onApiKeyChange = {},
            onModelChange = {},
            onToggleApiKey = {},
            onSave = {},
            onBack = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "Saved state")
@Composable
private fun SettingsScreenSavedPreview() {
    MaterialTheme {
        SettingsContent(
            baseUrl = "https://api.openai.com",
            apiKey = "sk-abc123",
            model = "deepseek-v4-pro",
            showApiKey = true,
            saved = true,
            onBaseUrlChange = {},
            onApiKeyChange = {},
            onModelChange = {},
            onToggleApiKey = {},
            onSave = {},
            onBack = {},
        )
    }
}
