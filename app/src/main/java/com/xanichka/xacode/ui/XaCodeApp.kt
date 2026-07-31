package com.xanichka.xacode.ui

import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xanichka.xacode.model.AppSettings
import com.xanichka.xacode.model.Conversation
import com.xanichka.xacode.model.ProjectWorkspace
import com.xanichka.xacode.ui.icons.PhIcons
import com.xanichka.xacode.ui.theme.XaBlue
import com.xanichka.xacode.ui.theme.XaSurfaceHigh
import kotlinx.coroutines.launch

@Composable
fun XaCodeApp(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showFiles by rememberSaveable { mutableStateOf(false) }
    var permissionCenter by rememberSaveable { mutableStateOf(!state.settings.permissionOnboardingDone) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            val name = runCatching { DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':').substringAfterLast('/').ifBlank { "Новый проект" } }.getOrDefault("Новый проект")
            viewModel.addProject(name, uri.toString())
        }
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
    } else if (showFiles && state.activeProject != null) {
        Surface(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), color = MaterialTheme.colorScheme.background) {
            ProjectFilesScreen(state.activeProject!!, onBack = { showFiles = false })
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                AppDrawer(
                    conversations = state.conversations,
                    projects = state.settings.projects,
                    activeId = state.activeId,
                    activeProjectId = state.activeProjectId,
                    settings = state.settings,
                    onNewChat = { viewModel.newChat(null); scope.launch { drawerState.close() } },
                    onSelectProject = { viewModel.selectProject(it); scope.launch { drawerState.close() } },
                    onNewProject = { folderLauncher.launch(null) },
                    onSelect = { viewModel.selectConversation(it); scope.launch { drawerState.close() } },
                    onDelete = viewModel::deleteConversation,
                    onSettings = { showSettings = true; scope.launch { drawerState.close() } }
                )
            }
        ) {
            Scaffold(topBar = {
                ChatHeader(
                    title = state.activeProject?.name ?: "Чат",
                    subtitle = state.currentProfile.name,
                    onMenu = { scope.launch { drawerState.open() } },
                    onTitle = { if (state.activeProject != null) showFiles = true },
                    onNewChat = { viewModel.newChat(state.activeProjectId) }
                )
            }) { padding ->
                ChatScreen(state, viewModel::selectProfile, viewModel::send, Modifier.padding(padding), onOpenProjectFiles = { showFiles = true })
            }
        }
    }

    if (permissionCenter) {
        PermissionCenter(
            hasProjects = state.settings.projects.isNotEmpty(),
            onChooseFolder = { folderLauncher.launch(null) },
            onDone = { permissionCenter = false; viewModel.finishPermissionOnboarding() }
        )
    }
    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Не получилось отправить") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("Понятно") } },
            dismissButton = { TextButton(onClick = { viewModel.dismissError(); showSettings = true }) { Text("Настроить модель") } }
        )
    }
}

@Composable
private fun ChatHeader(title: String, subtitle: String, onMenu: () -> Unit, onTitle: () -> Unit, onNewChat: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        CircleIconButton(PhIcons.Menu, "Меню", onMenu)
        Column(Modifier.clickable(onClick = onTitle).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = XaBlue, fontSize = 10.sp, maxLines = 1)
        }
        CircleIconButton(PhIcons.Plus, "Новый чат", onNewChat)
    }
}

@Composable
private fun AppDrawer(
    conversations: List<Conversation>, projects: List<ProjectWorkspace>, activeId: String?, activeProjectId: String?, settings: AppSettings,
    onNewChat: () -> Unit, onSelectProject: (String) -> Unit, onNewProject: () -> Unit, onSelect: (String) -> Unit,
    onDelete: (String) -> Unit, onSettings: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(conversations, query) { conversations.filter { query.isBlank() || it.title.contains(query, true) } }
    ModalDrawerSheet(Modifier.width(342.dp), drawerContainerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                BrandLogo(40.dp); Spacer(Modifier.width(11.dp)); Text("XaCode", fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
                CircleIconButton(PhIcons.Search, "Поиск", onClick = { })
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 14.dp), placeholder = { Text("Поиск в чатах") }, leadingIcon = { Icon(PhIcons.Search, null, Modifier.size(19.dp)) }, singleLine = true, shape = RoundedCornerShape(24.dp))
            DrawerAction(PhIcons.Chat, "Новый чат", "Без папки", onNewChat)
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ПРОЕКТЫ", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = .9.sp)
                IconButton(onClick = onNewProject) { Icon(PhIcons.Plus, "Добавить проект", Modifier.size(19.dp)) }
            }
            projects.forEach { project ->
                DrawerAction(PhIcons.Folders, project.name, "${conversations.count { it.projectId == project.id }} чатов", { onSelectProject(project.id) }, selected = activeProjectId == project.id)
            }
            Text("ЧАТЫ", Modifier.padding(start = 18.dp, top = 14.dp, bottom = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = .9.sp)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                items(visible, key = { it.id }) { conversation ->
                    DrawerConversationRow(conversation, settings.profiles.firstOrNull { it.id == conversation.modelProfileId }?.name ?: "Модель", conversation.id == activeId, { onSelect(conversation.id) }, { onDelete(conversation.id) })
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .3f))
            DrawerAction(PhIcons.Settings, "Настройки", "Модели, API и доступ", onSettings)
        }
    }
}

@Composable
private fun DrawerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit, selected: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).background(if (selected) XaSurfaceHigh else MaterialTheme.colorScheme.background, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(23.dp), tint = if (selected) XaBlue else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(13.dp)); Column { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
    }
}

@Composable
private fun DrawerConversationRow(conversation: Conversation, profileName: String, selected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).background(if (selected) XaSurfaceHigh else MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(start = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp); Text(profileName, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp) }
        Box { IconButton(onClick = { expanded = true }) { Icon(PhIcons.More, "Действия", Modifier.size(19.dp)) }; DropdownMenu(expanded, { expanded = false }) { DropdownMenuItem(text = { Text("Удалить") }, leadingIcon = { Icon(PhIcons.Trash, null) }, onClick = { expanded = false; onDelete() }) } }
    }
}
