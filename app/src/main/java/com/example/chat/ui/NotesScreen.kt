package com.example.chat.ui

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.chat.NotesViewModel
import com.example.chat.data.NoteEntity
import com.example.chat.model.PetTypes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.chat.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    viewModel: NotesViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val notes by viewModel.notes.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var currentEditingNote by remember { mutableStateOf<NoteEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(255, 255, 255))
    ) {
        // 过滤器
        FilterChips(
            selectedType = viewModel.selectedPetType,
            onFilterSelected = { viewModel.setFilter(it) }
        )

        // 便利贴网格
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp), // 增加水平内边距
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

        // 添加按钮
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.End),
            containerColor = Color(255,143, 45),
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add,
                contentDescription = "添加便利贴")
        }
    }

    // 添加便利贴对话框
    if (showAddDialog) {
        AddNoteDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { content, petType ->
                viewModel.addNote(content, petType)
                showAddDialog = false
            }
        )
    }

    // 编辑便利贴对话框
    if (showEditDialog && currentEditingNote != null) {
        EditNoteDialog(
            note = currentEditingNote!!,
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
                viewModel.deleteNote(currentEditingNote!!)
                showEditDialog = false
                currentEditingNote = null
            }
        )
    }
}

@Composable
private fun EditNoteDialog(
    note: NoteEntity,
    onDismiss: () -> Unit,
    onUpdate: (NoteEntity) -> Unit,
    onDelete: () -> Unit
) {
    var content by remember { mutableStateOf(note.content) }
    var selectedType by remember { mutableStateOf(PetTypes.valueOf(note.petType)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑便利贴") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    minLines = 5,
                    maxLines = 10
                )

                Text("选择宠物类型:", style = MaterialTheme.typography.labelLarge)
                // 使用LazyRow和FilterChip替换原来的选择器
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(PetTypes.values()) { type ->
                        val petName = when(type) {
                            PetTypes.CAT -> "布丁"
                            PetTypes.DOG -> "大白"
                            PetTypes.DOG2 -> "豆豆"
                            PetTypes.HAMSTER -> "团绒"
                        }

                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(petName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(255, 143, 45),
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
                        "删除",
                        color = Color.White
                    )
                }
//                // 删除按钮
//                Button(
//                    onClick = onDelete,
//                    colors = ButtonDefaults.buttonColors(
//                        containerColor = Color.Red.copy(alpha = 0.8f)
//                    )
//                ) {
//                    Icon(
//                        imageVector = Icons.Default.Delete,
//                        contentDescription = "删除",
//                        tint = Color.White
//                    )
//                    Spacer(modifier = Modifier.width(4.dp))
//                    Text("删除")
//                }

                // 保存按钮
                Button(
                    onClick = {
                        // 创建更新后的便利贴对象，保留原ID和时间戳
                        val updatedNote = note.copy(
                            content = content,
                            petType = selectedType.name
                        )
                        onUpdate(updatedNote)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(255, 143, 45)
                    )
                ) {
                    Text("保存")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "取消",
                    color = Color(255, 143, 45)
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
                label = { Text("全部") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(255, 143, 45),
                    selectedLabelColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            )
        }

        items(PetTypes.values()) { type ->
            FilterChip(
                selected = selectedType == type.name,
                onClick = { onFilterSelected(type.name) },
                label = { Text("#${type.displayName}") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(255, 143, 45),
                    selectedLabelColor = Color.White,
                    containerColor = Color.Transparent,
                    labelColor = Color(255, 143, 45),
                ),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
private fun NoteCard(
    note: NoteEntity,
    onClick: () -> Unit
) {
    val background = R.drawable.notebackground

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f) // 设置固定宽高比，确保便签有足够空间显示背景
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
//        shape = RoundedCornerShape(8.dp) // 添加圆角
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = background),
                contentDescription = null,
                contentScale = ContentScale.FillBounds, // 修改为FillBounds确保背景填充整个区域
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
                    // 便利贴内容
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 添加宠物类型标签
                    Text(
                        text = "#${PetTypes.valueOf(note.petType).displayName}",
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

// 修改添加便利贴对话框，使用LazyRow和FilterChip
@Composable
private fun AddNoteDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(PetTypes.CAT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加便利贴") },
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
                    label = { Text("内容") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    minLines = 5,
                    maxLines = 10
                )

                Text("选择宠物类型:", style = MaterialTheme.typography.labelLarge)

                // 使用LazyRow和FilterChip替换原来的选择器
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(PetTypes.values()) { type ->
                        val petName = when(type) {
                            PetTypes.CAT -> "布丁"
                            PetTypes.DOG -> "大白"
                            PetTypes.DOG2 -> "豆豆"
                            PetTypes.HAMSTER -> "团绒"
                        }

                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(petName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(255, 143, 45),
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
                    containerColor = Color(255, 143, 45)
                )
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "取消",
                    color = Color(255, 143, 45)
                )
            }
        }
    )
}