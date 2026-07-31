package com.xanichka.xacode.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xanichka.xacode.model.ChatMessage
import com.xanichka.xacode.model.Conversation
import com.xanichka.xacode.model.CreationMode
import com.xanichka.xacode.model.MessageRole
import com.xanichka.xacode.model.ProviderSettings
import com.xanichka.xacode.ui.theme.XaGreen
import com.xanichka.xacode.ui.theme.XaSurfaceHigh
import kotlinx.coroutines.launch

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
                conversations = state.conversations,
                activeId = state.activeId,
                onNewChat = {
                    viewModel.newChat()
                    scope.launch { drawerState.close() }
                },
                onSelect = {
                    viewModel.selectConversation(it)
                    scope.launch { drawerState.close() }
                },
                onDelete = viewModel::deleteConversation,
                onSettings = {
                    showSettings = true
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("XaCode", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                            Text(
                                state.activeConversation?.mode?.title ?: state.selectedMode.title,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Rounded.Menu, contentDescription = "Открыть меню")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.newChat() }) {
                            Icon(Icons.Rounded.Add, contentDescription = "Новый чат")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { padding ->
            ChatScreen(
                state = state,
                onModeSelected = viewModel::selectMode,
                onSend = viewModel::send,
                modifier = Modifier.padding(padding)
            )
        }
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            icon = { Icon(Icons.Rounded.Terminal, contentDescription = null) },
            title = { Text("Не получилось отправить") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) { Text("Понятно") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissError()
                    showSettings = true
                }) { Text("Настройки AI") }
            }
        )
    }

    if (showSettings) {
        SettingsDialog(
            initial = state.settings,
            onDismiss = { showSettings = false },
            onSave = {
                viewModel.saveSettings(it)
                showSettings = false
            }
        )
    }
}

@Composable
private fun XaCodeDrawer(
    conversations: List<Conversation>,
    activeId: String?,
    onNewChat: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSettings: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(310.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandMark()
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("XaCode", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("Твори с помощью AI", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(
                onClick = onNewChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(12.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Новый чат")
            }
            Text(
                "НЕДАВНИЕ",
                modifier = Modifier.padding(start = 18.dp, top = 24.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (conversations.isEmpty()) {
                    item {
                        Text(
                            "Здесь появятся ваши разговоры",
                            modifier = Modifier.padding(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
                items(conversations, key = { it.id }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        selected = conversation.id == activeId,
                        onClick = { onSelect(conversation.id) },
                        onDelete = { onDelete(conversation.id) }
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSettings)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("Настройки AI")
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val background = if (selected) XaSurfaceHigh else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .background(background, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(modeIcon(conversation.mode), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Text(conversation.mode.title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Box {
            IconButton(onClick = { expanded = true }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = "Действия", modifier = Modifier.size(19.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Удалить") },
                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatScreen(
    state: AppUiState,
    onModeSelected: (CreationMode) -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var input by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val messages = state.activeConversation?.messages.orEmpty()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, state.isSending) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex + if (state.isSending) 1 else 0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        if (messages.isEmpty()) {
            WelcomePanel(
                selectedMode = state.selectedMode,
                onModeSelected = onModeSelected,
                onSuggestion = { input = it },
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(messages, key = { it.id }) { MessageItem(it) }
                if (state.isSending) {
                    item { ThinkingIndicator() }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Спроси, придумай или создай…") },
                minLines = 1,
                maxLines = 5,
                shape = RoundedCornerShape(22.dp),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val value = input
                            input = ""
                            focusManager.clearFocus()
                            onSend(value)
                        },
                        enabled = input.isNotBlank() && !state.isSending
                    ) {
                        Surface(
                            color = if (input.isNotBlank()) XaGreen else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Отправить",
                                    tint = if (input.isNotBlank()) Color(0xFF13200D) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                },
                keyboardActions = KeyboardActions(onSend = {
                    val value = input
                    input = ""
                    onSend(value)
                })
            )
            Text(
                "AI может ошибаться — проверяйте важный код",
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomePanel(
    selectedMode: CreationMode,
    onModeSelected: (CreationMode) -> Unit,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(Modifier.height(22.dp))
            BrandMark(58.dp)
            Spacer(Modifier.height(18.dp))
            Text("Что создадим сегодня?", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "От идеи до рабочего кода — прямо с телефона",
                modifier = Modifier.padding(top = 7.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(24.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CreationMode.entries.forEach { mode ->
                    ModeChip(mode, selected = mode == selectedMode) { onModeSelected(mode) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        val suggestions = when (selectedMode) {
            CreationMode.CHAT -> listOf("Объясни сложную тему простыми словами", "Помоги придумать идею для стартапа")
            CreationMode.APP -> listOf("Создай план приложения для изучения языков", "Спроектируй Android-приложение для привычек")
            CreationMode.CODE -> listOf("Напиши экран авторизации на Kotlin Compose", "Найди ошибку в моём коде")
            CreationMode.BOT -> listOf("Создай Telegram-бота для магазина", "Спроектируй AI-бота поддержки")
        }
        items(suggestions) { suggestion ->
            SuggestionCard(suggestion) { onSuggestion(suggestion) }
            Spacer(Modifier.height(9.dp))
        }
    }
}

@Composable
private fun ModeChip(mode: CreationMode, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(modeIcon(mode), contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text(mode.title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SuggestionCard(text: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, modifier = Modifier.weight(1f), fontSize = 14.sp)
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp), tint = XaGreen)
        }
    }
}

@Composable
private fun MessageItem(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            BrandMark(30.dp)
            Spacer(Modifier.width(10.dp))
        }
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            modifier = Modifier.fillMaxWidth(if (isUser) .86f else 1f)
        ) {
            Text(
                message.text,
                modifier = Modifier.padding(if (isUser) 13.dp else 4.dp),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontFamily = if (message.text.contains("```")) FontFamily.Monospace else FontFamily.Default
            )
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandMark(30.dp)
        Spacer(Modifier.width(12.dp))
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(9.dp))
        Text("XaCode думает…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun BrandMark(size: androidx.compose.ui.unit.Dp = 40.dp) {
    Surface(color = XaGreen, shape = RoundedCornerShape(size * .28f), modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Code,
                contentDescription = null,
                tint = Color(0xFF15210F),
                modifier = Modifier.size(size * .58f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDialog(
    initial: ProviderSettings,
    onDismiss: () -> Unit,
    onSave: (ProviderSettings) -> Unit
) {
    var endpoint by remember(initial) { mutableStateOf(initial.endpoint) }
    var apiKey by remember(initial) { mutableStateOf(initial.apiKey) }
    var model by remember(initial) { mutableStateOf(initial.model) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.statusBarsPadding()) {
            TopAppBar(
                title = { Text("Настройки AI") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onSave(ProviderSettings(endpoint, apiKey, model)) },
                        enabled = endpoint.isNotBlank() && model.isNotBlank()
                    ) { Text("Сохранить") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text("Провайдер", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Подключите OpenAI или любой совместимый сервис.",
                        modifier = Modifier.padding(top = 5.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = { Text("API endpoint") },
                        supportingText = { Text("Например: https://api.openai.com/v1/chat/completions") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Модель") },
                        supportingText = { Text("Имя модели у выбранного провайдера") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API-ключ") },
                        supportingText = { Text("Хранится только в приватных данных приложения") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Совместимые сервисы", fontWeight = FontWeight.SemiBold)
                            Text(
                                "OpenAI, OpenRouter, локальные серверы и другие API с форматом Chat Completions.",
                                modifier = Modifier.padding(top = 6.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun modeIcon(mode: CreationMode): ImageVector = when (mode) {
    CreationMode.CHAT -> Icons.Rounded.ChatBubbleOutline
    CreationMode.APP -> Icons.Rounded.Android
    CreationMode.CODE -> Icons.Rounded.Code
    CreationMode.BOT -> Icons.Rounded.SmartToy
}
