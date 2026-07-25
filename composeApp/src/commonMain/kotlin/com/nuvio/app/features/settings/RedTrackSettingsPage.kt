package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.redtrack.RedTrackSettingsRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_redtrack_api_key
import nuvio.composeapp.generated.resources.settings_redtrack_base_url
import nuvio.composeapp.generated.resources.settings_redtrack_caption
import nuvio.composeapp.generated.resources.settings_redtrack_save
import nuvio.composeapp.generated.resources.settings_redtrack_saved
import nuvio.composeapp.generated.resources.settings_redtrack_title
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.redTrackSettingsContent(
    isTablet: Boolean,
) {
    item {
        SettingsGroup(isTablet = isTablet) {
            RedTrackSettingsForm(isTablet = isTablet)
        }
    }
}

@Composable
private fun RedTrackSettingsForm(isTablet: Boolean) {
    val uiState by RedTrackSettingsRepository.uiState.collectAsState()
    var baseUrl by rememberSaveable { mutableStateOf(uiState.baseUrl.orEmpty()) }
    var apiKey by rememberSaveable { mutableStateOf(uiState.apiKey.orEmpty()) }
    var savedMessage by rememberSaveable { mutableStateOf(false) }
    val horizontalPadding = if (isTablet) 20.dp else 16.dp
    val verticalPadding = if (isTablet) 18.dp else 16.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.settings_redtrack_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = stringResource(Res.string.settings_redtrack_caption),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(0.dp))

        androidx.compose.material3.OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it; savedMessage = false },
            label = { Text(stringResource(Res.string.settings_redtrack_base_url)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        SettingsSecretTextField(
            value = apiKey,
            onValueChange = { apiKey = it; savedMessage = false },
            label = stringResource(Res.string.settings_redtrack_api_key),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                RedTrackSettingsRepository.updateBaseUrl(baseUrl)
                RedTrackSettingsRepository.updateApiKey(apiKey)
                savedMessage = true
            },
        ) {
            Text(stringResource(Res.string.settings_redtrack_save))
        }

        if (savedMessage) {
            Text(
                text = stringResource(Res.string.settings_redtrack_saved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
