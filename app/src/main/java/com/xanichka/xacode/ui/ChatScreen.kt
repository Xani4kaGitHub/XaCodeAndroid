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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xanichka.xacode.model.ChatMessage
import com.xanichka.xacode.model.ToolTrace
import com.xanichka.xacode.model.ToolTraceState
import com.xanichka.xacode.data.AgentProgress
import com.xanichka.xacode.data.WorkspaceRepository
import com.xanichka.xacode.model.MessageRole
import com.xanichka.xacode.model.ModelProfile
import com.xanichka.xacode.model.presetFor
import com.xanichka.xacode.ui.icons.PhIcons
import com.xanichka.xacode.ui.theme.XaBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class UiAttachment(val name: String, val content: String)
private data class PromptCommand(val id: String, val title: String, val hint: String)

private val promptCommands = listOf(
    PromptCommand("goal", "Цель", "Довести большую задачу до результата"),
    PromptCommand("plan", "План", "Сначала составить понятный план"),
    PromptCommand("terminal", "Терминал", "Запустить команду через Termux"),
    PromptCommand("review", "Ревью", "Проверить код и найти проблемы"),
    PromptCommand("fix", "Исправить", "Найти причину и исправить ошибку"),
    PromptCommand("test", "Тесты", "Написать или запустить тесты"),
    PromptCommand("explain", "Объяснить", "Объяснить код простыми словами"),
    PromptCommand("learn", "Обучение", "Разобрать тему пошагово"),
    PromptCommand("btw", "Быстрый вопрос", "Короткий вопрос без смены задачи")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: AppUiState,
    onProfileSelected: (String) -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: (String, String) -> Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenProjectFiles: () -> Unit = {},
    onOpenModelSettings: () -> Unit = {}
) {
    val input = state.draftText
    var showModels by rememberSaveable { mutableStateOf(false) }
    var showTools by rememberSaveable { mutableStateOf(false) }
    var mentionResults by remember { mutableStateOf<List<String>>(emptyList()) }
    val attachments = remember { mutableStateListOf<UiAttachment>() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workspaceRepository = remember(context) { WorkspaceRepository(context) }
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
            if (empty) WelcomePanel(projectName = state.activeProject?.name, language = state.settings.language, onSuggestion = onDraftChange, modifier = Modifier.fillMaxSize())
            else LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    items(messages, key = { it.id }) { MessageItem(it, state.settings.language, state.settings.showToolActivity) }
                    if (state.isSending) item { ThinkingIndicator(state.agentProgress, state.settings.showToolActivity) }
                }
    }
    Column(modifier.fillMaxSize()) {
        if (state.settings.animationsEnabled) Crossfade(targetState = messages.isEmpty(), label = "chat-content", modifier = Modifier.weight(1f), content = chatContent)
        else Box(Modifier.weight(1f)) { chatContent(messages.isEmpty()) }

        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).navigationBarsPadding().imePadding().animateContentSize()) {
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
            val activeToken = input.substringAfterLast(' ').substringAfterLast('\n')
            val slashItems = if (activeToken.startsWith('/')) promptCommands.filter { it.id.contains(activeToken.drop(1), true) }.take(6) else emptyList()
            if (slashItems.isNotEmpty() || (activeToken.startsWith('@') && mentionResults.isNotEmpty())) {
                PromptSuggestions(
                    commands = slashItems,
                    files = if (activeToken.startsWith('@')) mentionResults else emptyList(),
                    onCommand = { command -> onDraftChange(replaceActiveToken(input, "/${command.id} ")) },
                    onFile = { path ->
                        val project = state.activeProject ?: return@PromptSuggestions
                        scope.launch {
                            val content = withContext(Dispatchers.IO) {
                                runCatching {
                                    val node = workspaceRepository.resolve(project.treeUri, path) ?: error("Путь не найден")
                                    if (node.isDirectory) workspaceRepository.listRelative(project.treeUri, path).joinToString("\n") { entry ->
                                        (if (entry.isDirectory) "[DIR] " else "[FILE] ") + "$path/${entry.name}"
                                    } else workspaceRepository.readRelative(project.treeUri, path)
                                }.getOrNull()
                            }
                            if (content != null) {
                                attachments.removeAll { it.name == path }
                                attachments += UiAttachment(path, content)
                                onDraftChange(replaceActiveToken(input, "@$path "))
                            }
                        }
                    }
                )
            }
            Surface(shape = RoundedCornerShape(27.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .72f))) {
                Column(Modifier.padding(horizontal = 7.dp, vertical = 6.dp)) {
                    BasicTextField(
                        value = input,
                        onValueChange = { value ->
                            onDraftChange(value)
                            val token = value.substringAfterLast(' ').substringAfterLast('\n')
                            if (token.startsWith('@') && state.activeProject != null) {
                                scope.launch {
                                    mentionResults = withContext(Dispatchers.IO) { workspaceRepository.search(state.activeProject!!.treeUri, token.drop(1), 8) }
                                }
                            } else mentionResults = emptyList()
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(XaBlue),
                        minLines = 1, maxLines = 5,
                        keyboardActions = KeyboardActions(onSend = {
                            if (input.isNotBlank() && !state.isSending) {
                                val fileContext = attachments.joinToString("\n\n") { "--- ${it.name} ---\n${it.content}" }
                                if (onSend(expandSlashPrompt(input), fileContext)) { attachments.clear(); focusManager.clearFocus() }
                            }
                        }),
                        decorationBox = { inner -> Box { if (input.isBlank()) Text(tr(state.settings.language, "Спроси или создай что-нибудь…", "Запитай або створи щось…", "Ask or create something…"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp); inner() } }
                    )
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showTools = true }) {
                            Icon(PhIcons.Plus, "Добавить файл", Modifier.size(22.dp))
                        }
                        val modelLocked = state.activeConversation?.messages?.isNotEmpty() == true
                        Surface(
                            modifier = Modifier.clickable(enabled = !modelLocked) { showModels = true },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProviderBadge(state.currentProfile.provider, 20.dp, selected = true)
                                Spacer(Modifier.width(6.dp))
                                Text(state.currentProfile.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                if (modelLocked) Text(" · привязана", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(
                            color = if (state.isSending) MaterialTheme.colorScheme.error else if (input.isNotBlank()) XaBlue else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier.size(42.dp)
                        ) {
                            IconButton(
                                enabled = state.isSending || input.isNotBlank(),
                                onClick = {
                                    if (state.isSending) onCancel() else {
                                        val fileContext = attachments.joinToString("\n\n") { "--- ${it.name} ---\n${it.content}" }
                                        if (onSend(expandSlashPrompt(input), fileContext)) { attachments.clear(); focusManager.clearFocus() }
                                    }
                                }
                            ) {
                                Icon(if (state.isSending) PhIcons.Stop else PhIcons.Send, if (state.isSending) "Остановить" else tr(state.settings.language, "Отправить", "Надіслати", "Send"), Modifier.size(20.dp), tint = if (state.isSending || input.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showModels) {
        ModalBottomSheet(onDismissRequest = { showModels = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Text(tr(state.settings.language, "Выберите модель", "Виберіть модель", "Choose a model"), Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            state.settings.profiles.forEach { profile ->
                ModelChoiceRow(profile, profile.id == state.currentProfile.id) {
                    onProfileSelected(profile.id); showModels = false
                }
            }
            ToolRow(PhIcons.Settings, tr(state.settings.language, "Настройки моделей", "Налаштування моделей", "Model settings"), tr(state.settings.language, "API-ключи, провайдеры и параметры", "API-ключі, провайдери та параметри", "API keys, providers and parameters")) { showModels = false; onOpenModelSettings() }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showTools) {
        ModalBottomSheet(onDismissRequest = { showTools = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Text(tr(state.settings.language, "Добавить в запрос", "Додати до запиту", "Add to prompt"), Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            ToolRow(PhIcons.Paperclip, tr(state.settings.language, "Файлы", "Файли", "Files"), tr(state.settings.language, "Прикрепить до четырёх файлов", "Прикріпити до чотирьох файлів", "Attach up to four files")) { showTools = false; fileLauncher.launch(arrayOf("*/*")) }
            ToolRow(PhIcons.FileCode, tr(state.settings.language, "Фото и скриншоты", "Фото та скриншоти", "Photos and screenshots"), tr(state.settings.language, "Выбрать изображение на устройстве", "Вибрати зображення на пристрої", "Choose an image on the device")) { showTools = false; fileLauncher.launch(arrayOf("image/*")) }
            if (state.activeProject != null) ToolRow(PhIcons.Folders, tr(state.settings.language, "Файлы проекта", "Файли проєкту", "Project files"), tr(state.settings.language, "Открыть, создать или изменить", "Відкрити, створити або змінити", "Open, create or edit")) { showTools = false; onOpenProjectFiles() }
            ToolRow(PhIcons.Sliders, tr(state.settings.language, "Модель и интеллект", "Модель та інтелект", "Model and intelligence"), state.currentProfile.name) { showTools = false; showModels = true }
            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
private fun PromptSuggestions(commands: List<PromptCommand>, files: List<String>, onCommand: (PromptCommand) -> Unit, onFile: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            commands.forEach { command ->
                Row(Modifier.fillMaxWidth().clickable { onCommand(command) }.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(8.dp), color = XaBlue.copy(alpha = .13f)) { Text("/${command.id}", Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = XaBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(10.dp)); Column { Text(command.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Text(command.hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
                }
            }
            files.forEach { path ->
                Row(Modifier.fillMaxWidth().clickable { onFile(path) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(PhIcons.FileCode, null, Modifier.size(20.dp), tint = XaBlue); Spacer(Modifier.width(10.dp)); Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                }
            }
        }
    }
}

private fun replaceActiveToken(text: String, replacement: String): String {
    val index = maxOf(text.lastIndexOf(' '), text.lastIndexOf('\n')) + 1
    return text.take(index) + replacement
}

private fun expandSlashPrompt(text: String): String {
    val expansions = mapOf(
        "goal" to "[GOAL MODE] Продолжай работу до полного результата. Проверяй каждый важный этап.",
        "plan" to "[PLANNING MODE] Сначала составь краткий план, затем последовательно выполни его.",
        "terminal" to "[TERMINAL TASK] Используй run_command через Termux, когда нужно выполнить или проверить код.",
        "review" to "[CODE REVIEW] Проверь код на ошибки, безопасность, производительность и удобство поддержки.",
        "fix" to "[FIX] Найди первопричину проблемы, исправь её и проверь результат.",
        "test" to "[TEST] Создай или запусти подходящие тесты и сообщи реальные результаты.",
        "explain" to "[EXPLAIN] Объясни решение понятными словами и с короткими примерами.",
        "learn" to "[LEARN] Обучай пошагово, проверяя понимание на небольших примерах.",
        "btw" to "[QUICK SIDE QUESTION] Ответь коротко, не меняя основную задачу."
    )
    var result = text.trim()
    expansions.forEach { (command, instruction) ->
        val regex = Regex("(^|\\s)/${Regex.escape(command)}(?=\\s|$)")
        result = result.replace(regex) { match -> "${match.groupValues[1]}$instruction" }
    }
    return result
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
        ProviderBadge(profile.provider, selected = selected)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.name, fontWeight = FontWeight.SemiBold)
            Text("${presetFor(profile.provider).label} · ${profile.model}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
        }
        if (selected) Icon(PhIcons.Check, null, Modifier.size(20.dp), tint = XaBlue)
    }
}

@Composable
private fun WelcomePanel(projectName: String?, language: com.xanichka.xacode.model.UiLanguage, onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    val suggestions = listOf(
        PhIcons.FileCode to tr(language, "Создай приложение по моей идее", "Створи застосунок за моєю ідеєю", "Build an app from my idea"),
        PhIcons.Robot to tr(language, "Напиши и настрой бота", "Напиши та налаштуй бота", "Build and configure a bot"),
        PhIcons.Search to tr(language, "Разберись в ошибке кода", "Розберися з помилкою в коді", "Debug a code error")
    )
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 26.dp, end = 26.dp, top = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom)) {
        item {
            Column { if (projectName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(PhIcons.Folders, null, Modifier.size(25.dp), tint = XaBlue); Spacer(Modifier.width(10.dp)); Text(projectName, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
                Text(tr(language, "Android-проект · инструменты зависят от разрешений", "Android-проєкт · інструменти залежать від дозволів", "Android project · tools depend on permissions"), Modifier.padding(top = 5.dp), color = XaBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(tr(language, "XaCode работает только внутри выбранной папки", "XaCode працює лише у вибраній папці", "XaCode works only inside the selected folder"), Modifier.padding(top = 3.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            } else Text(tr(language, "Что будем делать?", "Що будемо робити?", "What shall we build?"), fontSize = 25.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp)) }
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
private fun MessageItem(message: ChatMessage, language: com.xanichka.xacode.model.UiLanguage, showToolActivity: Boolean) {
    val isUser = message.role == MessageRole.USER
    val clipboard = LocalClipboardManager.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        if (!isUser) { BrandLogo(30.dp); Spacer(Modifier.width(10.dp)) }
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
            shape = RoundedCornerShape(18.dp, 18.dp, if (isUser) 18.dp else 4.dp, if (isUser) 4.dp else 18.dp),
            modifier = Modifier.fillMaxWidth(if (isUser) .86f else 1f)
        ) {
            Column(Modifier.padding(if (isUser) 13.dp else 4.dp)) {
                SelectionContainer {
                    if (isUser) Text(message.text, fontSize = 15.sp, lineHeight = 22.sp) else MarkdownText(message.text)
                }
                if (!isUser && showToolActivity && message.toolTrace.isNotEmpty()) ToolTracePanel(message.toolTrace, false)
                if (message.context.isNotBlank()) Text(tr(language, "Файлы прикреплены", "Файли прикріплено", "Files attached"), Modifier.padding(top = 6.dp), color = XaBlue, fontSize = 11.sp)
                if (!isUser) Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(message.text)) }, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)) { Icon(PhIcons.Copy, null, Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text(tr(language, "Копировать", "Копіювати", "Copy"), fontSize = 11.sp) }
                    Spacer(Modifier.weight(1f))
                    val stats = buildList {
                        if (message.inputTokens + message.outputTokens > 0) add("${message.inputTokens + message.outputTokens} токенов")
                        if (message.toolCalls > 0) add("${message.toolCalls} инструментов")
                        if (message.elapsedMs > 0) add("${"%.1f".format(message.elapsedMs / 1000f)} с")
                    }.joinToString(" · ")
                    if (stats.isNotBlank()) Text(stats, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun MarkdownText(markdown: String) {
    val parts = markdown.split("```")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                val content = part.substringAfter('\n', part).trimEnd()
                val language = part.substringBefore('\n', "").trim()
                CodeBlock(language, content)
            } else MarkdownLines(part.lines())
        }
    }
}

@Composable
private fun MarkdownLines(lines: List<String>) {
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val isTable = '|' in line && index + 1 < lines.size && '|' in lines[index + 1] &&
            lines[index + 1].split('|').filter { it.isNotBlank() }.all { it.trim().matches(Regex(":?-{3,}:?")) }
        when {
            isTable -> {
                val table = mutableListOf(line)
                index += 2
                while (index < lines.size && '|' in lines[index] && lines[index].isNotBlank()) table += lines[index++]
                MarkdownTable(table)
                continue
            }
            line.trim() == "$$" -> {
                val expression = mutableListOf<String>()
                index++
                while (index < lines.size && lines[index].trim() != "$$") expression += lines[index++]
                MathBlock(expression.joinToString("\n"))
            }
            line.trim().startsWith("$$") -> MathBlock(line.trim().removePrefix("$$").removeSuffix("$$"))
            line.startsWith("### ") -> Text(inlineMarkdown(line.drop(4)), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            line.startsWith("## ") -> Text(inlineMarkdown(line.drop(3)), fontSize = 19.sp, fontWeight = FontWeight.Bold)
            line.startsWith("# ") -> Text(inlineMarkdown(line.drop(2)), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            line.startsWith("> ") -> Surface(color = XaBlue.copy(alpha = .09f), shape = RoundedCornerShape(8.dp)) { Text(inlineMarkdown(line.drop(2)), Modifier.padding(10.dp), fontSize = 14.sp, lineHeight = 21.sp) }
            line.matches(Regex("^[-*] \\[[xX ]].*")) -> Row { Text(if (line.substring(3, 4).equals("x", true)) "☑  " else "☐  ", color = XaBlue); Text(inlineMarkdown(line.drop(6)), Modifier.weight(1f), fontSize = 15.sp) }
            line.startsWith("- ") || line.startsWith("* ") -> Row { Text("•  ", color = XaBlue); Text(inlineMarkdown(line.drop(2)), Modifier.weight(1f), fontSize = 15.sp, lineHeight = 22.sp) }
            line.matches(Regex("^\\d+\\. .*")) -> { val marker = line.substringBefore(' ') + " "; Row { Text(marker, color = XaBlue); Text(inlineMarkdown(line.substringAfter(' ')), Modifier.weight(1f), fontSize = 15.sp, lineHeight = 22.sp) } }
            line.isBlank() -> Spacer(Modifier.height(3.dp))
            line.trim().matches(Regex("-{3,}")) -> androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .5f))
            else -> Text(inlineMarkdown(line), fontSize = 15.sp, lineHeight = 22.sp)
        }
        index++
    }
}

@Composable
private fun MarkdownTable(rows: List<String>) {
    val clipboard = LocalClipboardManager.current
    val cells = rows.map { row -> row.trim().trim('|').split('|').map { it.trim() } }
    val columns = cells.maxOfOrNull { it.size } ?: 0
    val widths = (0 until columns).map { column ->
        val longest = cells.maxOfOrNull { it.getOrNull(column)?.length ?: 0 } ?: 0
        (longest * 7 + 30).coerceIn(104, 224).dp
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Таблица · ${cells.size}×$columns", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            TextButton(onClick = { clipboard.setText(AnnotatedString(rows.joinToString("\n"))) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                Icon(PhIcons.Copy, "Копировать таблицу", Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("Копировать", fontSize = 11.sp)
            }
        }
        Surface(shape = RoundedCornerShape(12.dp), color = Color.Transparent, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))) {
          Column(Modifier.horizontalScroll(rememberScrollState())) {
            cells.forEachIndexed { rowIndex, row ->
                Row {
                    (0 until columns).forEach { column ->
                        val cell = row.getOrNull(column).orEmpty()
                        Surface(
                            modifier = Modifier.width(widths[column]),
                            color = when { rowIndex == 0 -> XaBlue.copy(alpha = .13f); rowIndex % 2 == 0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .38f); else -> Color.Transparent },
                            border = androidx.compose.foundation.BorderStroke(.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = .32f))
                        ) {
                            Text(inlineMarkdown(cell), Modifier.padding(horizontal = 11.dp, vertical = 10.dp), fontSize = 12.sp, lineHeight = 17.sp, fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
          }
        }
    }
}

@Composable
private fun MathBlock(expression: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(prettyMath(expression), Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(14.dp), fontFamily = FontFamily.Serif, fontSize = 18.sp, lineHeight = 26.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun inlineMarkdown(text: String) = buildAnnotatedString {
    val regex = Regex("(\\*\\*.+?\\*\\*|~~.+?~~|`.+?`|\\$.+?\\$|\\[[^]]+]\\([^)]+\\)|(?<!\\*)\\*[^*]+?\\*(?!\\*)|_[^_]+?_)")
    var cursor = 0
    regex.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        val token = match.value
        if (token.startsWith("**")) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(token.drop(2).dropLast(2)) }
        else if (token.startsWith("~~")) withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(token.drop(2).dropLast(2)) }
        else if (token.startsWith("`")) withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = XaBlue, background = androidx.compose.ui.graphics.Color(0x26353143))) { append(token.drop(1).dropLast(1)) }
        else if (token.startsWith("$")) withStyle(SpanStyle(fontFamily = FontFamily.Serif, color = XaBlue)) { append(prettyMath(token.drop(1).dropLast(1))) }
        else if (token.startsWith("[")) withStyle(SpanStyle(color = XaBlue, textDecoration = TextDecoration.Underline)) { append(token.substringAfter('[').substringBefore(']')) }
        else withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(token.drop(1).dropLast(1)) }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}

internal fun prettyMath(source: String): String {
    var value = replaceMathCommand(source, "\\frac", 2) { args -> "(${prettyMath(args[0])})⁄(${prettyMath(args[1])})" }
    value = replaceMathCommand(value, "\\sqrt", 1) { args -> "√(${prettyMath(args[0])})" }
    return value
        .replace("\\times", "×").replace("\\div", "÷").replace("\\cdot", "·")
        .replace("\\pm", "±").replace("\\approx", "≈").replace("\\equiv", "≡")
        .replace("\\leq", "≤").replace("\\le", "≤").replace("\\geq", "≥").replace("\\ge", "≥").replace("\\neq", "≠").replace("\\ne", "≠")
        .replace("\\alpha", "α").replace("\\beta", "β").replace("\\gamma", "γ").replace("\\theta", "θ").replace("\\lambda", "λ")
        .replace("\\sum", "∑").replace("\\prod", "∏").replace("\\int", "∫").replace("\\partial", "∂")
        .replace("\\pi", "π").replace("\\infty", "∞").replace("\\rightarrow", "→").replace("\\Rightarrow", "⇒")
        .replace("^{0}", "⁰").replace("^{1}", "¹").replace("^{2}", "²").replace("^{3}", "³")
        .replace("^0", "⁰").replace("^1", "¹").replace("^2", "²").replace("^3", "³")
}

/** Parses braced LaTeX commands without Android ICU regex, including nested braces. */
private fun replaceMathCommand(source: String, command: String, argumentCount: Int, render: (List<String>) -> String): String {
    var value = source
    var from = 0
    while (true) {
        val start = value.indexOf(command, from)
        if (start < 0) return value
        var cursor = start + command.length
        val arguments = mutableListOf<String>()
        repeat(argumentCount) {
            while (cursor < value.length && value[cursor].isWhitespace()) cursor++
            if (cursor >= value.length || value[cursor] != '{') return@repeat
            var depth = 1
            var end = cursor + 1
            while (end < value.length && depth > 0) {
                if (value[end] == '{') depth++ else if (value[end] == '}') depth--
                end++
            }
            if (depth == 0) { arguments += value.substring(cursor + 1, end - 1); cursor = end }
        }
        if (arguments.size != argumentCount) { from = start + command.length; continue }
        value = value.replaceRange(start, cursor, render(arguments))
        from = start
    }
}

@Composable
private fun CodeBlock(language: String, code: String) {
    val clipboard = LocalClipboardManager.current
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 13.dp, end = 7.dp, top = 5.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(language.ifBlank { "code" }.uppercase(), Modifier.weight(1f), color = XaBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp)) { Icon(PhIcons.Copy, "Копировать код", Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text("Копировать", fontSize = 10.sp) }
            }
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
            Text(code, Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(13.dp), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun ThinkingIndicator(progress: AgentProgress, showToolActivity: Boolean) {
    Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandLogo(30.dp); Spacer(Modifier.width(12.dp)); CircularProgressIndicator(Modifier.size(18.dp), color = XaBlue, strokeWidth = 2.dp); Spacer(Modifier.width(9.dp))
        Column {
            Text(if (progress.currentTool.isBlank()) "XaCode думает…" else "Работает: ${progress.currentTool}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            val details = buildList { if (progress.inputTokens + progress.outputTokens > 0) add("${progress.inputTokens + progress.outputTokens} токенов"); if (progress.toolCalls > 0) add("${progress.toolCalls} инструментов") }.joinToString(" · ")
            if (details.isNotBlank()) Text(details, color = XaBlue, fontSize = 10.sp)
        }
    }
    if (showToolActivity && progress.toolTrace.isNotEmpty()) ToolTracePanel(progress.toolTrace, true)
    }
}

@Composable
private fun ToolTracePanel(traces: List<ToolTrace>, running: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(running) }
    val failures = traces.count { it.state == ToolTraceState.ERROR }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 9.dp).clickable { expanded = !expanded }.animateContentSize(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .72f)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (running) "●" else "✓", color = if (running) XaBlue else MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                Spacer(Modifier.width(7.dp))
                Text("Журнал инструментов · ${traces.size}", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (failures > 0) Text("$failures ошибок", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                Spacer(Modifier.width(6.dp)); Text(if (expanded) "⌃" else "⌄", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    traces.takeLast(20).forEachIndexed { index, trace ->
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.background.copy(alpha = .55f)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val status = when (trace.state) { ToolTraceState.RUNNING -> "выполняется"; ToolTraceState.SUCCESS -> "готово"; ToolTraceState.ERROR -> "ошибка" }
                                Text("${index + 1}. ${trace.name} · $status", color = if (trace.state == ToolTraceState.ERROR) MaterialTheme.colorScheme.error else XaBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                SelectionContainer { Column {
                                    if (trace.arguments.isNotBlank()) Text("АРГУМЕНТЫ\n${trace.arguments}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 15.sp)
                                    if (trace.result.isNotBlank()) Text("РЕЗУЛЬТАТ\n${trace.result}", Modifier.padding(top = 4.dp), fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 15.sp)
                                } }
                            }
                        }
                    }
                }
            }
        }
    }
}
