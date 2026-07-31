package com.xanichka.xacode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xanichka.xacode.model.AppSettings
import com.xanichka.xacode.model.ChatMessage
import com.xanichka.xacode.model.Conversation
import com.xanichka.xacode.model.MessageRole
import com.xanichka.xacode.model.ModelProfile
import com.xanichka.xacode.model.ProviderType
import com.xanichka.xacode.model.presetFor
import com.xanichka.xacode.model.providerPresets
import com.xanichka.xacode.ui.theme.XaGreen
import com.xanichka.xacode.ui.theme.XaSurfaceHigh
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XaCodeApp(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            XaCodeDrawer(
                state.conversations,
                state.activeId,
                state.settings,
                onNewChat = { viewModel.newChat(); scope.launch { drawerState.close() } },
                onSelect = { viewModel.selectConversation(it); scope.launch { drawerState.close() } },
                onDelete = viewModel::deleteConversation,
                onSettings = { showSettings = true; scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("XaCode", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                            Text(state.currentProfile.name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Rounded.Menu, contentDescription = "Открыть меню")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.newChat() }) { Icon(Icons.Rounded.Add, contentDescription = "Новый чат") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        ) { padding ->
            ChatScreen(state, viewModel::selectProfile, viewModel::send, Modifier.padding(padding))
        }
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            icon = { Icon(Icons.Rounded.Terminal, contentDescription = null) },
            title = { Text("Не получилось отправить") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("Понятно") } },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissError(); showSettings = true }) { Text("Настройки моделей") }
            }
        )
    }

    if (showSettings) {
        SettingsScreen(
            initial = state.settings,
            testingProfileId = state.testingProfileId,
            connectionResult = state.connectionResult,
            onTest = viewModel::testProfile,
            onClearResult = viewModel::clearConnectionResult,
            onDismiss = { showSettings = false },
            onSave = { viewModel.saveSettings(it); showSettings = false }
        )
    }
}

@Composable
private fun XaCodeDrawer(
    conversations: List<Conversation>,
    activeId: String?,
    settings: AppSettings,
    onNewChat: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSettings: () -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(310.dp), drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                BrandMark()
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("XaCode", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("AI понимает задачу сам", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(
                onClick = onNewChat,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(12.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Новый чат")
            }
            Text("НЕДАВНИЕ", Modifier.padding(start = 18.dp, top = 24.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = 1.sp)
            LazyColumn(Modifier.weight(1f)) {
                if (conversations.isEmpty()) item {
                    Text("Здесь появятся ваши разговоры", Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                items(conversations, key = { it.id }) { conversation ->
                    val profileName = settings.profiles.firstOrNull { it.id == conversation.modelProfileId }?.name ?: "Модель"
                    ConversationRow(conversation, profileName, conversation.id == activeId, { onSelect(conversation.id) }, { onDelete(conversation.id) })
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
            Row(Modifier.fillMaxWidth().clickable(onClick = onSettings).padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Settings, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("Модели и настройки")
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, profileName: String, selected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
            .background(if (selected) XaSurfaceHigh else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(start = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Text(profileName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Box {
            IconButton(onClick = { expanded = true }, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.MoreHoriz, "Действия", Modifier.size(19.dp)) }
            DropdownMenu(expanded, { expanded = false }) {
                DropdownMenuItem({ Text("Удалить") }, onClick = { expanded = false; onDelete() }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) })
            }
        }
    }
}

@Composable
private fun ChatScreen(state: AppUiState, onProfileSelected: (String) -> Unit, onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    var input by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val messages = state.activeConversation?.messages.orEmpty()
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, state.isSending) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex + if (state.isSending) 1 else 0)
    }
    Column(modifier.fillMaxSize().imePadding()) {
        if (messages.isEmpty()) WelcomePanel({ input = it }, Modifier.weight(1f)) else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                items(messages, key = { it.id }) { MessageItem(it) }
                if (state.isSending) item { ThinkingIndicator() }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding()) {
            ProfilePicker(state.settings.profiles, state.currentProfile.id, state.isSending, onProfileSelected)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                input, { input = it }, Modifier.fillMaxWidth(),
                placeholder = { Text("Спроси, придумай или создай…") }, minLines = 1, maxLines = 5,
                shape = RoundedCornerShape(22.dp),
                trailingIcon = {
                    IconButton(
                        onClick = { val value = input; input = ""; focusManager.clearFocus(); onSend(value) },
                        enabled = input.isNotBlank() && !state.isSending
                    ) {
                        Surface(color = if (input.isNotBlank()) XaGreen else MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape, modifier = Modifier.size(34.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Rounded.Send, "Отправить", tint = if (input.isNotBlank()) Color(0xFF13200D) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                },
                keyboardActions = KeyboardActions(onSend = { val value = input; input = ""; onSend(value) })
            )
            Text("AI может ошибаться — проверяйте важный код", Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ProfilePicker(profiles: List<ModelProfile>, selectedId: String, disabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = profiles.firstOrNull { it.id == selectedId } ?: profiles.first()
    Box {
        Surface(
            modifier = Modifier.clickable(enabled = !disabled) { expanded = true },
            shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface
        ) {
            Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = XaGreen)
                Spacer(Modifier.width(7.dp))
                Text(selected.name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(5.dp))
                Text(selected.model, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
                Icon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded, { expanded = false }) {
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Column { Text(profile.name); Text("${presetFor(profile.provider).label} · ${profile.model}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                    onClick = { expanded = false; onSelect(profile.id) },
                    trailingIcon = { if (profile.id == selected.id) Icon(Icons.Rounded.Check, null) }
                )
            }
        }
    }
}

@Composable
private fun WelcomePanel(onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    val suggestions = listOf(
        "Создай план Android-приложения по моей идее",
        "Напиши Telegram-бота и объясни запуск",
        "Помоги исправить ошибку в коде",
        "Ответь на мой вопрос простыми словами"
    )
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Spacer(Modifier.height(22.dp)); BrandMark(58.dp); Spacer(Modifier.height(18.dp))
            Text("Что создадим сегодня?", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text("Просто опишите задачу — XaCode сам поймёт, что делать", Modifier.padding(top = 7.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))
        }
        items(suggestions) { suggestion -> SuggestionCard(suggestion) { onSuggestion(suggestion) }; Spacer(Modifier.height(9.dp)) }
    }
}

@Composable
private fun SuggestionCard(text: String, onClick: () -> Unit) {
    Card(onClick, Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp), border = CardDefaults.outlinedCardBorder()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), fontSize = 14.sp)
            Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(16.dp), tint = XaGreen)
        }
    }
}

@Composable
private fun MessageItem(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        if (!isUser) { BrandMark(30.dp); Spacer(Modifier.width(10.dp)) }
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
            shape = RoundedCornerShape(18.dp, 18.dp, if (isUser) 18.dp else 4.dp, if (isUser) 4.dp else 18.dp),
            modifier = Modifier.fillMaxWidth(if (isUser) .86f else 1f)
        ) {
            Text(message.text, Modifier.padding(if (isUser) 13.dp else 4.dp), fontSize = 15.sp, lineHeight = 22.sp, fontFamily = if (message.text.contains("```")) FontFamily.Monospace else FontFamily.Default)
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandMark(30.dp); Spacer(Modifier.width(12.dp)); CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(9.dp))
        Text("XaCode думает…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun BrandMark(size: Dp = 40.dp) {
    Surface(color = XaGreen, shape = RoundedCornerShape(size * .28f), modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Code, null, tint = Color(0xFF15210F), modifier = Modifier.size(size * .58f)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    initial: AppSettings,
    testingProfileId: String?,
    connectionResult: String?,
    onTest: (AppSettings, ModelProfile) -> Unit,
    onClearResult: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var editingId by remember(initial) { mutableStateOf(initial.activeProfileId) }
    var showApiKey by rememberSaveable { mutableStateOf(false) }
    val profile = draft.profiles.firstOrNull { it.id == editingId } ?: draft.profiles.first()
    fun updateProfile(transform: (ModelProfile) -> ModelProfile) {
        draft = draft.copy(profiles = draft.profiles.map { if (it.id == profile.id) transform(it) else it })
        onClearResult()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding()) {
            TopAppBar(
                title = { Text("Модели и AI") },
                navigationIcon = { IconButton(onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                actions = { TextButton(onClick = { onSave(draft) }) { Text("Сохранить") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Подключения", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            Text("Можно добавить несколько моделей и менять их прямо в чате.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                        IconButton(onClick = {
                            val added = ModelProfile(id = UUID.randomUUID().toString(), name = "Новое подключение")
                            draft = draft.copy(profiles = draft.profiles + added)
                            editingId = added.id
                        }) { Icon(Icons.Rounded.Add, "Добавить модель") }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        draft.profiles.forEach { item ->
                            Surface(
                                Modifier.clickable { editingId = item.id; showApiKey = false; onClearResult() },
                                shape = RoundedCornerShape(12.dp),
                                color = if (item.id == profile.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.SmartToy, null, Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text(item.name, maxLines = 1)
                                    if (item.id == draft.activeProfileId) { Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.Check, null, Modifier.size(15.dp), tint = XaGreen) }
                                }
                            }
                        }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                            OutlinedTextField(profile.name, { value -> updateProfile { it.copy(name = value) } }, label = { Text("Название подключения") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            ProviderPicker(profile.provider) { type ->
                                val preset = presetFor(type)
                                updateProfile { it.copy(provider = type, name = preset.label, baseUrl = preset.baseUrl, model = preset.defaultModel) }
                            }
                            OutlinedTextField(profile.baseUrl, { value -> updateProfile { it.copy(baseUrl = value) } }, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(profile.model, { value -> updateProfile { it.copy(model = value) } }, label = { Text("Модель") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            val suggestions = presetFor(profile.provider).models
                            if (suggestions.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                suggestions.forEach { model ->
                                    Surface(Modifier.clickable { updateProfile { it.copy(model = model) } }, shape = RoundedCornerShape(9.dp), color = if (model == profile.model) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                                        Text(model, Modifier.padding(horizontal = 9.dp, vertical = 6.dp), fontSize = 11.sp)
                                    }
                                }
                            }
                            OutlinedTextField(
                                profile.apiKey, { value -> updateProfile { it.copy(apiKey = value) } },
                                label = { Text("API-ключ") },
                                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                                trailingIcon = { IconButton({ showApiKey = !showApiKey }) { Icon(if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (showApiKey) "Скрыть ключ" else "Показать ключ") } },
                                supportingText = { Text(if (presetFor(profile.provider).apiKeyOptional) "Ключ можно оставить пустым" else "Ключ зашифрован Android Keystore") }
                            )
                            if (profile.provider == ProviderType.DEEPSEEK) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text("Показывать reasoning"); Text("Для reasoning-моделей DeepSeek", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    Switch(profile.showReasoning, { checked -> updateProfile { it.copy(showReasoning = checked) } })
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onTest(draft, profile) },
                                    enabled = testingProfileId == null && profile.baseUrl.isNotBlank() && profile.model.isNotBlank(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (testingProfileId == profile.id) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Terminal, null, Modifier.size(17.dp))
                                    Spacer(Modifier.width(7.dp)); Text("Проверить")
                                }
                                OutlinedButton(onClick = {
                                    val copy = profile.copy(id = UUID.randomUUID().toString(), name = "${profile.name} — копия")
                                    draft = draft.copy(profiles = draft.profiles + copy); editingId = copy.id
                                }) { Icon(Icons.Rounded.ContentCopy, "Дублировать") }
                                if (draft.profiles.size > 1) IconButton(onClick = {
                                    val remaining = draft.profiles.filterNot { it.id == profile.id }
                                    val active = if (draft.activeProfileId == profile.id) remaining.first().id else draft.activeProfileId
                                    draft = draft.copy(profiles = remaining, activeProfileId = active); editingId = remaining.first().id
                                }) { Icon(Icons.Rounded.DeleteOutline, "Удалить", tint = MaterialTheme.colorScheme.error) }
                            }
                            connectionResult?.let { Text(it, color = if (it == "Подключение работает") XaGreen else MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                            if (profile.id != draft.activeProfileId) OutlinedButton({ draft = draft.copy(activeProfileId = profile.id) }, Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(7.dp)); Text("Сделать активной")
                            }
                        }
                    }
                }
                item {
                    Text("Поведение XaCode", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("Свои инструкции"); Text("Например, язык и стиль кода", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Switch(draft.customInstructionsEnabled, { draft = draft.copy(customInstructionsEnabled = it) })
                            }
                            if (draft.customInstructionsEnabled) OutlinedTextField(draft.customInstructions, { draft = draft.copy(customInstructions = it) }, label = { Text("Инструкции для XaCode") }, minLines = 3, maxLines = 7, modifier = Modifier.fillMaxWidth())
                            HorizontalDivider()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("Своя temperature"); Text("Точность ↔ творчество", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Switch(draft.temperatureEnabled, { draft = draft.copy(temperatureEnabled = it) })
                            }
                            if (draft.temperatureEnabled) {
                                Text("${"%.1f".format(draft.temperature)}", color = XaGreen, fontWeight = FontWeight.SemiBold)
                                Slider(draft.temperature, { draft = draft.copy(temperature = it) }, valueRange = 0f..2f, steps = 19)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ProviderPicker(selected: ProviderType, onSelected: (ProviderType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.SmartToy, null); Spacer(Modifier.width(8.dp)); Text(presetFor(selected).label, Modifier.weight(1f)); Icon(Icons.Rounded.ExpandMore, null)
        }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.widthIn(min = 260.dp)) {
            providerPresets.forEach { preset ->
                DropdownMenuItem(
                    text = { Column { Text(preset.label); Text(preset.defaultModel.ifBlank { "Настроить вручную" }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                    onClick = { expanded = false; onSelected(preset.type) },
                    trailingIcon = { if (preset.type == selected) Icon(Icons.Rounded.Check, null) }
                )
            }
        }
    }
}
