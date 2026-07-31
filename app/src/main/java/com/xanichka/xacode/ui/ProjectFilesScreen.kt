package com.xanichka.xacode.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xanichka.xacode.data.WorkspaceEntry
import com.xanichka.xacode.data.WorkspaceRepository
import com.xanichka.xacode.data.PythonRuntime
import com.xanichka.xacode.model.ProjectWorkspace
import com.xanichka.xacode.ui.icons.PhIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProjectFilesScreen(project: ProjectWorkspace, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { WorkspaceRepository(context) }
    val pythonRuntime = remember(context) { PythonRuntime(context, repository) }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var currentUri by remember(project.treeUri) { mutableStateOf(project.treeUri) }
    var path by remember(project.id) { mutableStateOf(listOf(project.name to project.treeUri)) }
    var createKind by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<WorkspaceEntry?>(null) }
    var menuEntry by remember { mutableStateOf<WorkspaceEntry?>(null) }
    var renameEntry by remember { mutableStateOf<WorkspaceEntry?>(null) }
    var deleteEntry by remember { mutableStateOf<WorkspaceEntry?>(null) }
    var editorText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var runOutput by remember { mutableStateOf<String?>(null) }
    val entries by produceState(initialValue = emptyList(), currentUri, refresh) {
        value = withContext(Dispatchers.IO) { runCatching { repository.list(currentUri) }.getOrElse { error = it.message; emptyList() } }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(PhIcons.Back, "Назад", onClick = {
                if (path.size > 1) { path = path.dropLast(1); currentUri = path.last().second } else onBack()
            })
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(project.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(path.joinToString(" / ") { it.first }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CircleIconButton(PhIcons.Plus, "Создать файл", onClick = { createKind = "file" })
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { createKind = "file" }, modifier = Modifier.weight(1f)) { Icon(PhIcons.FileCode, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text("Файл") }
            OutlinedButton(onClick = { createKind = "folder" }, modifier = Modifier.weight(1f)) { Icon(PhIcons.Folders, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text("Папка") }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (entries.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(PhIcons.Folders, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp)); Text("Папка пока пустая", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(entries, key = { it.uri }) { entry ->
                Card(
                    Modifier.fillMaxWidth().clickable {
                        if (entry.isDirectory) { path = path + (entry.name to entry.uri); currentUri = entry.uri }
                        else scope.launch { runCatching { withContext(Dispatchers.IO) { repository.readText(entry.uri) } }.onSuccess { editorText = it; selected = entry }.onFailure { error = it.message } }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (entry.isDirectory) PhIcons.Folders else PhIcons.FileCode, null, Modifier.size(23.dp))
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) { Text(entry.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(if (entry.isDirectory) "Папка" else "${entry.size} байт", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
                        androidx.compose.foundation.layout.Box {
                            IconButton(onClick = { menuEntry = entry }) { Icon(PhIcons.More, "Действия") }
                            DropdownMenu(expanded = menuEntry?.uri == entry.uri, onDismissRequest = { menuEntry = null }) {
                                DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menuEntry = null; renameEntry = entry })
                                DropdownMenuItem(text = { Text("Удалить", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(PhIcons.Trash, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menuEntry = null; deleteEntry = entry })
                            }
                        }
                    }
                }
            }
        }
    }

    createKind?.let { kind ->
        var name by remember(kind) { mutableStateOf(if (kind == "file") "main.py" else "new_folder") }
        AlertDialog(
            onDismissRequest = { createKind = null },
            title = { Text(if (kind == "file") "Новый файл" else "Новая папка") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true) },
            confirmButton = { Button(onClick = {
                scope.launch { runCatching { withContext(Dispatchers.IO) { if (kind == "file") repository.writeText(currentUri, name, "") else repository.createDirectory(currentUri, name) } }.onSuccess { refresh++; createKind = null }.onFailure { error = it.message } }
            }, enabled = name.isNotBlank()) { Text("Создать") } },
            dismissButton = { TextButton(onClick = { createKind = null }) { Text("Отмена") } }
        )
    }
    selected?.let { file ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = { OutlinedTextField(editorText, { editorText = it }, modifier = Modifier.fillMaxWidth(), minLines = 12, maxLines = 20, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)) },
            confirmButton = {
                Row {
                    if (file.name.endsWith(".py", ignoreCase = true)) TextButton(onClick = {
                        val relativePath = (path.drop(1).map { it.first } + file.name).joinToString("/")
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { repository.updateText(file.uri, editorText); pythonRuntime.run(project.treeUri, relativePath) } }
                                .onSuccess { runOutput = it; refresh++ }.onFailure { error = it.message }
                        }
                    }) { Icon(PhIcons.Send, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Run") }
                    Button(onClick = { scope.launch { runCatching { withContext(Dispatchers.IO) { repository.updateText(file.uri, editorText) } }.onSuccess { selected = null; refresh++ }.onFailure { error = it.message } } }) { Text("Сохранить") }
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Закрыть") } }
        )
    }
    renameEntry?.let { entry ->
        var name by remember(entry.uri) { mutableStateOf(entry.name) }
        AlertDialog(onDismissRequest = { renameEntry = null }, title = { Text("Переименовать") }, text = { OutlinedTextField(name, { name = it }, label = { Text("Новое название") }, singleLine = true) }, confirmButton = { Button(enabled = name.isNotBlank(), onClick = {
            val relativePath = (path.drop(1).map { it.first } + entry.name).joinToString("/")
            scope.launch { runCatching { withContext(Dispatchers.IO) { require(repository.renameRelative(project.treeUri, relativePath, name)) { "Переименование не поддерживается этой папкой Android" } } }.onSuccess { renameEntry = null; refresh++ }.onFailure { error = it.message } }
        }) { Text("Сохранить") } }, dismissButton = { TextButton(onClick = { renameEntry = null }) { Text("Отмена") } })
    }
    deleteEntry?.let { entry ->
        AlertDialog(onDismissRequest = { deleteEntry = null }, title = { Text(if (entry.isDirectory) "Удалить папку?" else "Удалить файл?") }, text = { Text("«${entry.name}» будет удалён без возможности восстановления.") }, confirmButton = { TextButton(onClick = { scope.launch { runCatching { withContext(Dispatchers.IO) { require(repository.delete(entry.uri)) { "Android не разрешил удалить этот объект" } } }.onSuccess { deleteEntry = null; refresh++ }.onFailure { error = it.message } } }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { deleteEntry = null }) { Text("Отмена") } })
    }
    runOutput?.let { output -> AlertDialog(onDismissRequest = { runOutput = null }, title = { Text("Результат Python") }, text = { Text(output, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }, confirmButton = { TextButton(onClick = { runOutput = null }) { Text("Готово") } }) }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Ошибка доступа") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("Понятно") } }) }
}
