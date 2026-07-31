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
    var openModelSettings by rememberSaveable { mutableStateOf(false) }
    var showFiles by rememberSaveable { mutableStateOf(false) }
    var createBlankAfterRootSelection by rememberSaveable { mutableStateOf(false) }
    var permissionCenter by rememberSaveable { mutableStateOf(!state.settings.permissionOnboardingDone) }
    fun persistFolder(uri: android.net.Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
    }
    val rootFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persistFolder(uri)
            viewModel.setProjectsRoot(uri.toString())
            if (createBlankAfterRootSelection) viewModel.createRandomProject(uri.toString())
        }
        createBlankAfterRootSelection = false
    }
    val externalFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persistFolder(uri)
            val name = runCatching { DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':').substringAfterLast('/').ifBlank { "Новый проект" } }.getOrDefault("Новый проект")
            viewModel.addProject(name, uri.toString())
        }
    }

    if (showSettings) {
        SettingsScreen(
            initial = state.settings,
            startAtModels = openModelSettings,
            testingProfileId = state.testingProfileId,
            connectionResult = state.connectionResult,
            onTest = viewModel::testProfile,
            onClearResult = viewModel::clearConnectionResult,
            onDismiss = { showSettings = false; openModelSettings = false },
            onSave = { viewModel.saveSettings(it); showSettings = false; openModelSettings = false }
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
                    onNewProject = {
                        if (state.settings.projectsRootUri.isBlank()) {
                            createBlankAfterRootSelection = true
                            rootFolderLauncher.launch(null)
                        } else viewModel.createRandomProject()
                        scope.launch { drawerState.close() }
                    },
                    onAddExternalProject = { externalFolderLauncher.launch(null); scope.launch { drawerState.close() } },
                    onNewProjectChat = { viewModel.newChat(it); scope.launch { drawerState.close() } },
                    onSelect = { viewModel.selectConversation(it); scope.launch { drawerState.close() } },
                    onDelete = viewModel::deleteConversation,
                    onDeleteProject = viewModel::removeProject,
                    onSettings = { showSettings = true; scope.launch { drawerState.close() } }
                )
            }
        ) {
            Scaffold(topBar = {
                ChatHeader(
                    title = state.activeProject?.name ?: tr(state.settings.language, "Чат", "Чат", "Chat"),
                    subtitle = state.currentProfile.name,
                    language = state.settings.language,
                    onMenu = { scope.launch { drawerState.open() } },
                    onTitle = { if (state.activeProject != null) showFiles = true },
                    onNewChat = { viewModel.newChat(state.activeProjectId) }
                )
            }) { padding ->
                ChatScreen(
                    state, viewModel::selectProfile, viewModel::send, Modifier.padding(padding),
                    onOpenProjectFiles = { showFiles = true },
                    onOpenModelSettings = { openModelSettings = true; showSettings = true }
                )
            }
        }
    }

    if (permissionCenter) {
        PermissionCenter(
            hasProjects = state.settings.projectsRootUri.isNotBlank(),
            language = state.settings.language,
            onChooseFolder = { rootFolderLauncher.launch(null) },
            onDone = { permissionCenter = false; viewModel.finishPermissionOnboarding() }
        )
    }
    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(tr(state.settings.language, "Не получилось отправить", "Не вдалося надіслати", "Could not send")) }, text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(tr(state.settings.language, "Понятно", "Зрозуміло", "OK")) } },
            dismissButton = { TextButton(onClick = { viewModel.dismissError(); showSettings = true }) { Text(tr(state.settings.language, "Настроить модель", "Налаштувати модель", "Configure model")) } }
        )
    }
}

@Composable
private fun ChatHeader(title: String, subtitle: String, language: com.xanichka.xacode.model.UiLanguage, onMenu: () -> Unit, onTitle: () -> Unit, onNewChat: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        CircleIconButton(PhIcons.Menu, tr(language, "Меню", "Меню", "Menu"), onMenu)
        Column(Modifier.clickable(onClick = onTitle).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = XaBlue, fontSize = 10.sp, maxLines = 1)
        }
        CircleIconButton(PhIcons.Plus, tr(language, "Новый чат", "Новий чат", "New chat"), onNewChat)
    }
}

@Composable
private fun AppDrawer(
    conversations: List<Conversation>, projects: List<ProjectWorkspace>, activeId: String?, activeProjectId: String?, settings: AppSettings,
    onNewChat: () -> Unit, onSelectProject: (String) -> Unit, onNewProject: () -> Unit, onAddExternalProject: () -> Unit, onNewProjectChat: (String) -> Unit, onSelect: (String) -> Unit,
    onDelete: (String) -> Unit, onDeleteProject: (String, Boolean) -> Unit, onSettings: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showProjectCreation by remember { mutableStateOf(false) }
    val visible = remember(conversations, query) { conversations.filter { (query.isNotBlank() && it.title.contains(query, true)) || (query.isBlank() && it.projectId == null) } }
    ModalDrawerSheet(Modifier.width(342.dp), drawerContainerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                BrandLogo(40.dp); Spacer(Modifier.width(11.dp)); Text("XaCode", fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
                Box {
                    CircleIconButton(PhIcons.Plus, tr(settings.language, "Создать", "Створити", "Create"), onClick = { showProjectCreation = true })
                    DropdownMenu(expanded = showProjectCreation, onDismissRequest = { showProjectCreation = false }) {
                        DropdownMenuItem(
                            text = { Column { Text(tr(settings.language, "Начать с нуля", "Почати з нуля", "Start from scratch"), fontWeight = FontWeight.SemiBold); Text(tr(settings.language, "Создать новую папку автоматически", "Створити нову папку автоматично", "Create a new folder automatically"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                            leadingIcon = { Icon(PhIcons.Plus, null) },
                            onClick = { showProjectCreation = false; onNewProject() }
                        )
                        DropdownMenuItem(
                            text = { Column { Text(tr(settings.language, "Выбрать папку", "Вибрати папку", "Choose folder"), fontWeight = FontWeight.SemiBold); Text(tr(settings.language, "Подключить существующий проект", "Підключити наявний проєкт", "Connect an existing project"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                            leadingIcon = { Icon(PhIcons.Folders, null) },
                            onClick = { showProjectCreation = false; onAddExternalProject() }
                        )
                    }
                }
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 14.dp), placeholder = { Text(tr(settings.language, "Поиск в чатах", "Пошук у чатах", "Search chats")) }, leadingIcon = { Icon(PhIcons.Search, null, Modifier.size(19.dp)) }, singleLine = true, shape = RoundedCornerShape(24.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)) {
                item { DrawerAction(PhIcons.Chat, tr(settings.language, "Новый чат", "Новий чат", "New chat"), tr(settings.language, "Без папки", "Без папки", "No folder"), onNewChat) }
                item { Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text(tr(settings.language, "ПРОЕКТЫ", "ПРОЄКТИ", "PROJECTS"), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = .9.sp); IconButton(onClick = onNewProject) { Icon(PhIcons.Plus, tr(settings.language, "Добавить проект", "Додати проєкт", "Add project"), Modifier.size(19.dp)) } } }
                items(projects, key = { "project-${it.id}" }) { project ->
                    Column {
                        ProjectDrawerRow(project, conversations.count { it.projectId == project.id }, activeProjectId == project.id, settings.language, { onSelectProject(project.id) }, { onNewProjectChat(project.id) }, onDeleteProject)
                        conversations.filter { it.projectId == project.id }.take(4).forEach { conversation -> NestedConversationRow(conversation, conversation.id == activeId) { onSelect(conversation.id) } }
                    }
                }
                item { TextButton(onClick = onAddExternalProject, Modifier.padding(horizontal = 12.dp)) { Icon(PhIcons.Paperclip, null, Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text(tr(settings.language, "Подключить другую папку", "Підключити іншу папку", "Connect another folder")) } }
                item { Text(tr(settings.language, "ЧАТЫ", "ЧАТИ", "CHATS"), Modifier.padding(start = 18.dp, top = 12.dp, bottom = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = .9.sp) }
                items(visible, key = { it.id }) { conversation ->
                    Box(Modifier.padding(horizontal = 8.dp)) { DrawerConversationRow(conversation, settings.profiles.firstOrNull { it.id == conversation.modelProfileId }?.name ?: "Модель", conversation.id == activeId, { onSelect(conversation.id) }, { onDelete(conversation.id) }) }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .3f))
            DrawerAction(PhIcons.Settings, tr(settings.language, "Настройки", "Налаштування", "Settings"), tr(settings.language, "Модели, API и доступ", "Моделі, API та доступ", "Models, API and access"), onSettings)
        }
    }
}

@Composable
private fun NestedConversationRow(conversation: Conversation, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 38.dp, end = 10.dp).background(if (selected) XaSurfaceHigh else MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(conversation.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        if (selected) Surface(Modifier.size(6.dp), shape = androidx.compose.foundation.shape.CircleShape, color = XaBlue) {}
    }
}

@Composable
private fun ProjectDrawerRow(project: ProjectWorkspace, chatCount: Int, selected: Boolean, language: com.xanichka.xacode.model.UiLanguage, onClick: () -> Unit, onNewChat: () -> Unit, onDelete: (String, Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).background(if (selected) XaSurfaceHigh else MaterialTheme.colorScheme.background, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(PhIcons.Folders, null, Modifier.size(22.dp), tint = if (selected) XaBlue else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold); Text(tr(language, "$chatCount чатов", "$chatCount чатів", "$chatCount chats"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp) }
        IconButton(onClick = onNewChat) { Icon(PhIcons.Plus, tr(language, "Новый чат в этой папке", "Новий чат у цій папці", "New chat in this folder"), Modifier.size(18.dp)) }
        Box { IconButton(onClick = { expanded = true }) { Icon(PhIcons.More, "Действия", Modifier.size(18.dp)) }; DropdownMenu(expanded, { expanded = false }) { DropdownMenuItem(text = { Text("Убрать из XaCode") }, onClick = { expanded = false; onDelete(project.id, false) }); if (project.managed) DropdownMenuItem(text = { Text("Удалить папку и содержимое", color = MaterialTheme.colorScheme.error) }, onClick = { expanded = false; confirmDelete = true }) } }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Удалить проект?") }, text = { Text("Папка «${project.name}» и всё внутри неё будут удалены без возможности восстановления.") }, confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(project.id, true) }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } })
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
