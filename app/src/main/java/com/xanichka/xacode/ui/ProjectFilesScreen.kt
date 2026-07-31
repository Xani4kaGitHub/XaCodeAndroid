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
import androidx.compose.material3.MaterialTheme
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
import com.xanichka.xacode.model.ProjectWorkspace
import com.xanichka.xacode.ui.icons.PhIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProjectFilesScreen(project: ProjectWorkspace, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { WorkspaceRepository(context) }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var createKind by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<WorkspaceEntry?>(null) }
    var editorText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val entries by produceState(initialValue = emptyList(), project.treeUri, refresh) {
        value = withContext(Dispatchers.IO) { runCatching { repository.list(project.treeUri) }.getOrElse { error = it.message; emptyList() } }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(PhIcons.Back, "Назад", onBack)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(project.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Файлы проекта", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
                    Modifier.fillMaxWidth().clickable(enabled = !entry.isDirectory) {
                        scope.launch { runCatching { withContext(Dispatchers.IO) { repository.readText(entry.uri) } }.onSuccess { editorText = it; selected = entry }.onFailure { error = it.message } }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (entry.isDirectory) PhIcons.Folders else PhIcons.FileCode, null, Modifier.size(23.dp))
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) { Text(entry.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(if (entry.isDirectory) "Папка" else "${entry.size} байт", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
                    }
                }
            }
        }
    }

    createKind?.let { kind ->
        var name by remember(kind) { mutableStateOf(if (kind == "file") "new_file.txt" else "new_folder") }
        AlertDialog(
            onDismissRequest = { createKind = null },
            title = { Text(if (kind == "file") "Новый файл" else "Новая папка") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true) },
            confirmButton = { Button(onClick = {
                scope.launch { runCatching { withContext(Dispatchers.IO) { if (kind == "file") repository.writeText(project.treeUri, name, "") else repository.createDirectory(project.treeUri, name) } }.onSuccess { refresh++; createKind = null }.onFailure { error = it.message } }
            }, enabled = name.isNotBlank()) { Text("Создать") } },
            dismissButton = { TextButton(onClick = { createKind = null }) { Text("Отмена") } }
        )
    }
    selected?.let { file ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = { OutlinedTextField(editorText, { editorText = it }, modifier = Modifier.fillMaxWidth(), minLines = 12, maxLines = 20, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)) },
            confirmButton = { Button(onClick = { scope.launch { runCatching { withContext(Dispatchers.IO) { repository.updateText(file.uri, editorText) } }.onSuccess { selected = null; refresh++ }.onFailure { error = it.message } } }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Закрыть") } }
        )
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Ошибка доступа") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("Понятно") } }) }
}
