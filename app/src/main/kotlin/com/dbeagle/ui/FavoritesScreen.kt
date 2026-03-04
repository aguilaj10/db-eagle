package com.dbeagle.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dbeagle.favorites.FavoritesRepository
import com.dbeagle.model.FavoriteQuery
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FavoritesScreen(
    repository: FavoritesRepository,
    onLoadQuery: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var favorites by remember { mutableStateOf(repository.getAll()) }
    var searchQuery by remember { mutableStateOf("") }
    var editingFavorite by remember { mutableStateOf<FavoriteQuery?>(null) }
    var deletingFavorite by remember { mutableStateOf<FavoriteQuery?>(null) }

    val displayedFavorites = remember(searchQuery, favorites) {
        if (searchQuery.isBlank()) favorites else repository.search(searchQuery)
    }

    if (editingFavorite != null) {
        EditFavoriteDialog(
            favorite = editingFavorite!!,
            onDismiss = { editingFavorite = null },
            onSave = { updated ->
                repository.save(updated)
                favorites = repository.getAll()
                editingFavorite = null
            }
        )
    }

    if (deletingFavorite != null) {
        AlertDialog(
            onDismissRequest = { deletingFavorite = null },
            title = { Text("Delete Favorite") },
            text = { Text("Are you sure you want to delete '${deletingFavorite!!.name}'? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        repository.delete(deletingFavorite!!.id)
                        favorites = repository.getAll()
                        deletingFavorite = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFavorite = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${displayedFavorites.size} ${if (displayedFavorites.size == 1) "favorite" else "favorites"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search favorites...") },
                singleLine = true,
                modifier = Modifier.width(300.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }

        HorizontalDivider()

        if (displayedFavorites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isBlank()) {
                        "No favorites yet.\nSave queries from Query Editor to see them here."
                    } else {
                        "No favorites match your search."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(displayedFavorites) { favorite ->
                    FavoriteCard(
                        favorite = favorite,
                        onClick = { onLoadQuery(favorite.query) },
                        onEdit = { editingFavorite = favorite },
                        onDelete = { deletingFavorite = favorite }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun FavoriteCard(
    favorite: FavoriteQuery,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val timestampText = dateFormat.format(Date(favorite.lastModified))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = favorite.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (favorite.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    favorite.tags.forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = tag,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = favorite.query,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Modified: $timestampText",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EditFavoriteDialog(
    favorite: FavoriteQuery,
    onDismiss: () -> Unit,
    onSave: (FavoriteQuery) -> Unit
) {
    var name by remember { mutableStateOf(favorite.name) }
    var tagsText by remember { mutableStateOf(favorite.tags.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Favorite") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("Tags (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val tags = tagsText.split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    onSave(favorite.copy(name = name, tags = tags))
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
