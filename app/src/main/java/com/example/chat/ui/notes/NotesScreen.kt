package com.example.chat.ui.notes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chat.model.NoteUiModel
import com.example.chat.model.NotesUiState
import com.example.chat.model.PetType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.chat.R
import com.example.chat.ui.theme.AccentOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    viewModel: NotesViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val state = (uiState as? NotesUiState.Ready) ?: NotesUiState.Ready()
    val notes = state.notes
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var currentEditingNote by remember { mutableStateOf<NoteUiModel?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(255, 255, 255))
    ) {
        FilterChips(
            selectedType = state.selectedPetType,
            onFilterSelected = { viewModel.setFilter(it) }
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.weight(1f),
            state = rememberLazyGridState(),
        ) {
            items(notes) { note ->
                NoteCard(
                    note = note,
                    onClick = {
                        currentEditingNote = note
                        showEditDialog = true
                    }
                )
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.End),
            containerColor = AccentOrange,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add,
                contentDescription = stringResource(R.string.notes_add))
        }
    }

    if (showAddDialog) {
        AddNoteDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { content, petType ->
                viewModel.addNote(content, petType)
                showAddDialog = false
            }
        )
    }

    val editingNote = currentEditingNote
    if (showEditDialog && editingNote != null) {
        EditNoteDialog(
            note = editingNote,
            onDismiss = {
                showEditDialog = false
                currentEditingNote = null
            },
            onUpdate = { updatedNote ->
                viewModel.updateNote(updatedNote)
                showEditDialog = false
                currentEditingNote = null
            },
            onDelete = {
                viewModel.deleteNote(editingNote)
                showEditDialog = false
                currentEditingNote = null
            }
        )
    }
}

@Composable
private fun EditNoteDialog(
    note: NoteUiModel,
    onDismiss: () -> Unit,
    onUpdate: (NoteUiModel) -> Unit,
    onDelete: () -> Unit
) {
    var content by remember { mutableStateOf(note.content) }
    var selectedType by remember { mutableStateOf(note.petType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notes_edit)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.notes_content_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    minLines = 5,
                    maxLines = 10
                )

                Text(stringResource(R.string.notes_select_pet_type), style = MaterialTheme.typography.labelLarge)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(PetType.entries) { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentOrange,
                                selectedLabelColor = Color.White,
                                containerColor = Color.Transparent,
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(245,181,134)
                    )
                ) {
                    Text(
                        stringResource(R.string.notes_delete),
                        color = Color.White
                    )
                }
                Button(
                    onClick = {
                        val updatedNote = note.copy(
                            content = content,
                            petType = selectedType
                        )
                        onUpdate(updatedNote)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange
                    )
                ) {
                    Text(stringResource(R.string.notes_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.notes_cancel),
                    color = AccentOrange
                )
            }
        }
    )
}

@Composable
private fun FilterChips(
    selectedType: String?,
    onFilterSelected: (String?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedType == null,
                onClick = { onFilterSelected(null) },
                label = { Text(stringResource(R.string.notes_filter_all)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentOrange,
                    selectedLabelColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            )
        }

        items(PetType.entries) { type ->
            FilterChip(
                selected = selectedType == type.name,
                onClick = { onFilterSelected(type.name) },
                label = { Text("#${type.displayName}") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentOrange,
                    selectedLabelColor = Color.White,
                    containerColor = Color.Transparent,
                    labelColor = AccentOrange,
                ),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
private fun NoteCard(
    note: NoteUiModel,
    onClick: () -> Unit
) {
    val background = R.drawable.notebackground

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = background),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "#${note.petType.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddNoteDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(PetType.CAT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notes_add)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.notes_content_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    minLines = 5,
                    maxLines = 10
                )

                Text(stringResource(R.string.notes_select_pet_type), style = MaterialTheme.typography.labelLarge)

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(PetType.entries) { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentOrange,
                                selectedLabelColor = Color.White,
                                containerColor = Color.Transparent,
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (content.isNotEmpty()) {
                        onAdd(content, selectedType.name)
                    }
                },
                enabled = content.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentOrange
                )
            ) {
                Text(stringResource(R.string.notes_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.notes_cancel),
                    color = AccentOrange
                )
            }
        }
    )
}
