package com.xanichka.xacode.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xanichka.xacode.model.AppSettings
import com.xanichka.xacode.model.Conversation
import com.xanichka.xacode.ui.icons.PhIcons
import com.xanichka.xacode.ui.theme.XaBlue
import com.xanichka.xacode.ui.theme.XaSurfaceHigh
import kotlinx.coroutines.launch

@Composable
fun XaCodeApp(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !showSettings,
        drawerContent = {
            AppDrawer(
                conversations = state.conversations,
                activeId = state.activeId,
                settings = state.settings,
                onNewChat = { viewModel.newChat(); scope.launch { drawerState.close() } },
                onSelect = { viewModel.selectConversation(it); scope.launch { drawerState.close() } },
                onDelete = viewModel::deleteConversation,
                onSettings = { showSettings = true; scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            topBar = {
                ChatHeader(
                    profileName = state.currentProfile.name,
                    onMenu = { scope.launch { drawerState.open() } },
                    onNewChat = viewModel::newChat
                )
            }
        ) { padding ->
            ChatScreen(
                state = state,
                onProfileSelected = viewModel::selectProfile,
                onSend = viewModel::send,
                modifier = Modifier.padding(padding)
            )
        }
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            icon = { Icon(PhIcons.FileCode, contentDescription = null) },
            title = { Text("Не получилось отправить") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("Понятно") } },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissError(); showSettings = true }) { Text("Настроить модель") }
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
private fun ChatHeader(profileName: String, onMenu: () -> Unit, onNewChat: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        CircleIconButton(PhIcons.Menu, "Меню", onMenu)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("XaCode", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(profileName, color = XaBlue, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        CircleIconButton(PhIcons.Plus, "Новый чат", onNewChat)
    }
}

@Composable
private fun AppDrawer(
    conversations: List<Conversation>,
    activeId: String?,
    settings: AppSettings,
    onNewChat: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSettings: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(conversations, query) {
        if (query.isBlank()) conversations else conversations.filter { it.title.contains(query, ignoreCase = true) }
    }
    ModalDrawerSheet(modifier = Modifier.width(330.dp), drawerContainerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                BrandLogo(42.dp)
                Spacer(Modifier.width(11.dp))
                Text("XaCode", fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
                CircleIconButton(PhIcons.Plus, "Новый чат", onNewChat)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                placeholder = { Text("Поиск по чатам") },
                leadingIcon = { Icon(PhIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(22.dp)
            )
            Text("НЕДАВНИЕ", Modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = 1.sp)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                if (visible.isEmpty()) item {
                    Text("Чаты не найдены", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(visible, key = { it.id }) { conversation ->
                    val profile = settings.profiles.firstOrNull { it.id == conversation.modelProfileId }
                    DrawerConversationRow(conversation, profile?.name ?: "Модель", conversation.id == activeId, { onSelect(conversation.id) }, { onDelete(conversation.id) })
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
            Row(Modifier.fillMaxWidth().clickable(onClick = onSettings).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(PhIcons.Settings, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Настройки", fontWeight = FontWeight.Medium)
                    Text("${settings.profiles.size} моделей", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(PhIcons.Next, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DrawerConversationRow(
    conversation: Conversation,
    profileName: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .background(if (selected) XaSurfaceHigh else MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(start = 12.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Text(profileName, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Box {
            IconButton(onClick = { expanded = true }) { Icon(PhIcons.More, "Действия", Modifier.size(20.dp)) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Удалить") },
                    leadingIcon = { Icon(PhIcons.Trash, contentDescription = null) },
                    onClick = { expanded = false; onDelete() }
                )
            }
        }
    }
}
