package com.xanichka.xacode.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xanichka.xacode.model.ChatMessage
import com.xanichka.xacode.model.MessageRole
import com.xanichka.xacode.model.ModelProfile
import com.xanichka.xacode.model.presetFor
import com.xanichka.xacode.ui.icons.PhIcons
import com.xanichka.xacode.ui.theme.XaBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class UiAttachment(val name: String, val content: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: AppUiState,
    onProfileSelected: (String) -> Unit,
    onSend: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenProjectFiles: () -> Unit = {},
    onOpenModelSettings: () -> Unit = {}
) {
    var input by rememberSaveable { mutableStateOf("") }
    var showModels by rememberSaveable { mutableStateOf(false) }
    var showTools by rememberSaveable { mutableStateOf(false) }
    val attachments = remember { mutableStateListOf<UiAttachment>() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = state.activeConversation?.messages.orEmpty()
    val listState = rememberLazyListState()
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                uris.take(4).mapNotNull { uri ->
                    runCatching {
                        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        } ?: "файл"
                        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                            val buffer = CharArray(64 * 1024)
                            val count = reader.read(buffer)
                            if (count > 0) String(buffer, 0, count) else ""
                        } ?: return@runCatching null
                        UiAttachment(name, text)
                    }.getOrNull()
                }
            }
            attachments.addAll(loaded.filterNotNull())
        }
    }

    LaunchedEffect(messages.size, state.isSending) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex + if (state.isSending) 1 else 0)
    }

    val chatContent: @Composable (Boolean) -> Unit = { empty ->
            if (empty) WelcomePanel(projectName = state.activeProject?.name, onSuggestion = { input = it }, modifier = Modifier.fillMaxSize())
            else LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    items(messages, key = { it.id }) { MessageItem(it) }
                    if (state.isSending) item { ThinkingIndicator() }
                }
    }
    Column(modifier.fillMaxSize().imePadding()) {
        if (state.settings.animationsEnabled) Crossfade(targetState = messages.isEmpty(), label = "chat-content", modifier = Modifier.weight(1f), content = chatContent)
        else Box(Modifier.weight(1f)) { chatContent(messages.isEmpty()) }

        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).navigationBarsPadding().animateContentSize()) {
            AnimatedVisibility(attachments.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    attachments.take(3).forEach { attachment ->
                        AssistChip(
                            onClick = { attachments.remove(attachment) },
                            label = { Text(attachment.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(PhIcons.FileCode, contentDescription = null, Modifier.size(15.dp)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }
            }
            Surface(shape = RoundedCornerShape(27.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .72f))) {
                Column(Modifier.padding(horizontal = 7.dp, vertical = 6.dp)) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(XaBlue),
                        minLines = 1, maxLines = 5,
                        keyboardActions = KeyboardActions(onSend = {
                            if (input.isNotBlank()) {
                                val fileContext = attachments.joinToString("\n\n") { "--- ${it.name} ---\n${it.content}" }
                                onSend(input, fileContext); input = ""; attachments.clear(); focusManager.clearFocus()
                            }
                        }),
                        decorationBox = { inner -> Box { if (input.isBlank()) Text("Спроси или создай что-нибудь…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp); inner() } }
                    )
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showTools = true }) {
                            Icon(PhIcons.Plus, "Добавить файл", Modifier.size(22.dp))
                        }
                        Surface(
                            modifier = Modifier.clickable(enabled = !state.isSending) { showModels = true },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(PhIcons.Sparkle, null, Modifier.size(16.dp), tint = XaBlue)
                                Spacer(Modifier.width(6.dp))
                                Text(state.currentProfile.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(
                            color = if (input.isNotBlank() && !state.isSending) XaBlue else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier.size(42.dp)
                        ) {
                            IconButton(
                                enabled = input.isNotBlank() && !state.isSending,
                                onClick = {
                                    val fileContext = attachments.joinToString("\n\n") { "--- ${it.name} ---\n${it.content}" }
                                    onSend(input, fileContext); input = ""; attachments.clear(); focusManager.clearFocus()
                                }
                            ) {
                                Icon(PhIcons.Send, "Отправить", Modifier.size(20.dp), tint = if (input.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showModels) {
        ModalBottomSheet(onDismissRequest = { showModels = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Text("Выберите модель", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            state.settings.profiles.forEach { profile ->
                ModelChoiceRow(profile, profile.id == state.currentProfile.id) {
                    onProfileSelected(profile.id); showModels = false
                }
            }
            ToolRow(PhIcons.Settings, "Настройки моделей", "API-ключи, провайдеры и параметры") { showModels = false; onOpenModelSettings() }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showTools) {
        ModalBottomSheet(onDismissRequest = { showTools = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Text("Добавить в запрос", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            ToolRow(PhIcons.Paperclip, "Файлы", "Прикрепить до четырёх файлов") { showTools = false; fileLauncher.launch(arrayOf("*/*")) }
            ToolRow(PhIcons.FileCode, "Фото и скриншоты", "Выбрать изображение на устройстве") { showTools = false; fileLauncher.launch(arrayOf("image/*")) }
            if (state.activeProject != null) ToolRow(PhIcons.Folders, "Файлы проекта", "Открыть, создать или изменить") { showTools = false; onOpenProjectFiles() }
            ToolRow(PhIcons.Sliders, "Модель и интеллект", state.currentProfile.name) { showTools = false; showModels = true }
            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
private fun ToolRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(46.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(23.dp)) } }
        Spacer(Modifier.width(14.dp)); Column { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
    }
}

@Composable
private fun ModelChoiceRow(profile: ModelProfile, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = if (selected) XaBlue.copy(alpha = .18f) else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(42.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(PhIcons.Robot, null, Modifier.size(22.dp), tint = if (selected) XaBlue else MaterialTheme.colorScheme.onSurface) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.name, fontWeight = FontWeight.SemiBold)
            Text("${presetFor(profile.provider).label} · ${profile.model}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
        }
        if (selected) Icon(PhIcons.Check, null, Modifier.size(20.dp), tint = XaBlue)
    }
}

@Composable
private fun WelcomePanel(projectName: String?, onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    val suggestions = listOf(
        PhIcons.FileCode to "Создай приложение по моей идее",
        PhIcons.Robot to "Напиши и настрой бота",
        PhIcons.Search to "Разберись в ошибке кода"
    )
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 26.dp, end = 26.dp, top = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom)) {
        item {
            Column { if (projectName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(PhIcons.Folders, null, Modifier.size(25.dp), tint = XaBlue); Spacer(Modifier.width(10.dp)); Text(projectName, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
                Text("Чат работает в контексте этой папки", Modifier.padding(top = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else Text("Что будем делать?", fontSize = 25.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp)) }
        }
        items(suggestions) { (icon, title) ->
            Row(Modifier.fillMaxWidth().clickable { onSuggestion(title) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(13.dp))
                Text(title, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun MessageItem(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        if (!isUser) { BrandLogo(30.dp); Spacer(Modifier.width(10.dp)) }
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
            shape = RoundedCornerShape(18.dp, 18.dp, if (isUser) 18.dp else 4.dp, if (isUser) 4.dp else 18.dp),
            modifier = Modifier.fillMaxWidth(if (isUser) .86f else 1f)
        ) {
            Column(Modifier.padding(if (isUser) 13.dp else 4.dp)) {
                Text(message.text, fontSize = 15.sp, lineHeight = 22.sp, fontFamily = if (message.text.contains("```")) FontFamily.Monospace else FontFamily.Default)
                if (message.context.isNotBlank()) Text("Файлы прикреплены", Modifier.padding(top = 6.dp), color = XaBlue, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandLogo(30.dp); Spacer(Modifier.width(12.dp)); CircularProgressIndicator(Modifier.size(18.dp), color = XaBlue, strokeWidth = 2.dp); Spacer(Modifier.width(9.dp))
        Text("XaCode думает…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}
