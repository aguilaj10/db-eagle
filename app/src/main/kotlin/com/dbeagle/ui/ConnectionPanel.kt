package com.dbeagle.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dbeagle.driver.DataDrivers
import com.dbeagle.session.SessionViewModel
import com.dbeagle.viewmodel.ConnectionListViewModel
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf

@Composable
fun ConnectionPanel(
    masterPassword: String,
    sessionViewModel: SessionViewModel,
    isCollapsed: Boolean,
    onCollapseToggle: () -> Unit,
    onNewConnection: () -> Unit,
    onStatusTextChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ConnectionListViewModel = remember(masterPassword) {
        GlobalContext.get().get { parametersOf(masterPassword) }
    }

    val uiState by viewModel.uiState.collectAsState()
    val connectedProfileIds by sessionViewModel.connectedProfileIds.collectAsState()
    val connectingProfileId by sessionViewModel.connectingProfileId.collectAsState()

    LaunchedEffect(Unit) {
        DataDrivers.registerAll()
        viewModel.refreshProfiles()
    }

    Row(modifier = modifier.fillMaxHeight()) {
        IconButton(
            onClick = onCollapseToggle,
            modifier = Modifier.align(Alignment.Top).padding(top = 8.dp),
        ) {
            Icon(
                imageVector = if (isCollapsed) {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft
                },
                contentDescription = if (isCollapsed) "Expand sidebar" else "Collapse sidebar",
            )
        }

        AnimatedVisibility(
            visible = !isCollapsed,
            enter = expandHorizontally(),
            exit = shrinkHorizontally(),
        ) {
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
            ) {
                Button(
                    onClick = onNewConnection,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Connection", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Connections",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        uiState.isLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center).size(24.dp),
                            )
                        }
                        uiState.error != null -> {
                            Text(
                                text = uiState.error ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        uiState.profiles.isEmpty() -> {
                            Text(
                                text = "No connections.\nClick + to add one.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(uiState.profiles) { profile ->
                                    val isConnected = connectedProfileIds.contains(profile.id)
                                    val isConnecting = connectingProfileId == profile.id

                                    ConnectionPanelRow(
                                        profileName = profile.name,
                                        isConnected = isConnected,
                                        isConnecting = isConnecting,
                                        onClick = {
                                            if (isConnecting) return@ConnectionPanelRow
                                            if (isConnected) {
                                                viewModel.disconnect(
                                                    profileId = profile.id,
                                                    profileName = profile.name,
                                                    onStatusTextChanged = onStatusTextChanged,
                                                    onSessionClose = { profileId ->
                                                        sessionViewModel.closeSession(profileId)
                                                    },
                                                    onSetConnecting = { profileId ->
                                                        sessionViewModel.setConnecting(profileId)
                                                    },
                                                    onUpdateStatus = {},
                                                )
                                            } else {
                                                viewModel.connect(
                                                    profile = profile,
                                                    onStatusTextChanged = onStatusTextChanged,
                                                    onSessionOpen = { profileId, profileName, driver ->
                                                        sessionViewModel.openSession(profileId, profileName, driver)
                                                        sessionViewModel.setActiveProfile(profileId)
                                                    },
                                                    onSetConnecting = { profileId ->
                                                        sessionViewModel.setConnecting(profileId)
                                                    },
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionPanelRow(
    profileName: String,
    isConnected: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnecting, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(
                color = when {
                    isConnecting -> Color.Yellow
                    isConnected -> Color.Green
                    else -> Color.Gray
                },
            )
        }

        Text(
            text = profileName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        if (isConnecting) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp))
        }
    }
}
