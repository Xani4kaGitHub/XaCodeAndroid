package com.xanichka.xacode.ui

import android.content.Intent
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.DocumentsContract
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xanichka.xacode.model.AppSettings
import com.xanichka.xacode.data.WorkspaceRepository
import com.xanichka.xacode.model.ModelProfile
import com.xanichka.xacode.model.ProviderType
import com.xanichka.xacode.model.ProjectWorkspace
import com.xanichka.xacode.model.UiLanguage
import com.xanichka.xacode.model.presetFor
import com.xanichka.xacode.model.providerPresets
import com.xanichka.xacode.ui.icons.PhIcons
import com.xanichka.xacode.ui.theme.XaBlue
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class SettingsPage { HOME, MODELS, PROFILE, DEVICE, PERSONALIZATION, TOOLS, APPEARANCE }

@Composable
fun SettingsScreen(
    initial: AppSettings,
    startAtModels: Boolean = false,
    testingProfileId: String?,
    connectionResult: String?,
    onTest: (AppSettings, ModelProfile) -> Unit,
    onClearResult: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var page by rememberSaveable { mutableStateOf(if (startAtModels) SettingsPage.MODELS else SettingsPage.HOME) }
    var editingId by rememberSaveable(initial.activeProfileId) { mutableStateOf(initial.activeProfileId) }

    fun goBack() {
        if (page == SettingsPage.HOME) onSave(draft) else page = SettingsPage.HOME
    }
    BackHandler(onBack = ::goBack)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            SettingsHeader(
                title = when (page) {
                    SettingsPage.HOME -> tr(draft.language, "Настройки", "Налаштування", "Settings")
                    SettingsPage.MODELS -> tr(draft.language, "Модели и API", "Моделі та API", "Models and API")
                    SettingsPage.PROFILE -> tr(draft.language, "Подключение", "Підключення", "Connection")
                    SettingsPage.DEVICE -> tr(draft.language, "Доступ к устройству", "Доступ до пристрою", "Device access")
                    SettingsPage.PERSONALIZATION -> tr(draft.language, "Персонализация", "Персоналізація", "Personalization")
                    SettingsPage.TOOLS -> tr(draft.language, "Инструменты агента", "Інструменти агента", "Agent tools")
                    SettingsPage.APPEARANCE -> tr(draft.language, "Оформление", "Оформлення", "Appearance")
                },
                language = draft.language,
                isHome = page == SettingsPage.HOME,
                onBack = ::goBack,
                onDone = { onSave(draft) }
            )
            when (page) {
                SettingsPage.HOME -> SettingsHome(
                    draft,
                    onModels = { page = SettingsPage.MODELS },
                    onDevice = { page = SettingsPage.DEVICE },
                    onPersonalization = { page = SettingsPage.PERSONALIZATION },
                    onTools = { page = SettingsPage.TOOLS },
                    onAppearance = { page = SettingsPage.APPEARANCE }
                )
                SettingsPage.MODELS -> ModelsPage(
                    settings = draft,
                    onSettingsChange = { draft = it },
                    onEdit = { editingId = it; onClearResult(); page = SettingsPage.PROFILE },
                    onAdd = {
                        val profile = ModelProfile(id = UUID.randomUUID().toString(), name = "Новое подключение")
                        draft = draft.copy(profiles = draft.profiles + profile)
                        editingId = profile.id
                        page = SettingsPage.PROFILE
                    }
                )
                SettingsPage.PROFILE -> {
                    val profile = draft.profiles.firstOrNull { it.id == editingId } ?: draft.profiles.first()
                    ProfileEditor(
                        profile = profile,
                        settings = draft,
                        testing = testingProfileId == profile.id,
                        connectionResult = connectionResult,
                        onUpdate = { updated -> draft = draft.copy(profiles = draft.profiles.map { if (it.id == updated.id) updated else it }); onClearResult() },
                        onTest = { onTest(draft, it) },
                        onActivate = { draft = draft.copy(activeProfileId = profile.id) },
                        onDuplicate = {
                            val copy = profile.copy(id = UUID.randomUUID().toString(), name = "${profile.name} — копия")
                            draft = draft.copy(profiles = draft.profiles + copy); editingId = copy.id; onClearResult()
                        },
                        onDelete = {
                            if (draft.profiles.size > 1) {
                                val remaining = draft.profiles.filterNot { it.id == profile.id }
                                draft = draft.copy(
                                    profiles = remaining,
                                    activeProfileId = if (draft.activeProfileId == profile.id) remaining.first().id else draft.activeProfileId
                                )
                                page = SettingsPage.MODELS
                            }
                        }
                    )
                }
                SettingsPage.DEVICE -> DeviceAccessPage(draft) { draft = it }
                SettingsPage.PERSONALIZATION -> PersonalizationPage(draft) { draft = it }
                SettingsPage.TOOLS -> ToolsPage(draft) { draft = it }
                SettingsPage.APPEARANCE -> AppearancePage(draft) { draft = it }
            }
        }
    }
}

@Composable
private fun SettingsHeader(title: String, language: UiLanguage, isHome: Boolean, onBack: () -> Unit, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleIconButton(if (isHome) PhIcons.Close else PhIcons.Back, if (isHome) tr(language, "Закрыть", "Закрити", "Close") else tr(language, "Назад", "Назад", "Back"), onBack)
        Text(title, Modifier.weight(1f).padding(start = 14.dp), fontSize = 21.sp, fontWeight = FontWeight.Bold)
        if (isHome) TextButton(onClick = onDone) { Text(tr(language, "Готово", "Готово", "Done"), color = XaBlue, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun SettingsHome(settings: AppSettings, onModels: () -> Unit, onDevice: () -> Unit, onPersonalization: () -> Unit, onTools: () -> Unit, onAppearance: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandLogo(62.dp); Spacer(Modifier.width(14.dp))
                Column { Text("XaCode Android", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(tr(settings.language, "Версия 0.8.0", "Версія 0.8.0", "Version 0.8.0"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item {
            SettingsSection(tr(settings.language, "AI И РАЗРАБОТКА", "AI ТА РОЗРОБКА", "AI AND DEVELOPMENT")) {
                SettingsRow(PhIcons.Robot, tr(settings.language, "Модели и API", "Моделі та API", "Models and API"), tr(settings.language, "${settings.profiles.size} подключений", "${settings.profiles.size} підключень", "${settings.profiles.size} connections"), onModels)
                HorizontalDivider(Modifier.padding(start = 58.dp))
                SettingsRow(PhIcons.Sliders, tr(settings.language, "Персонализация", "Персоналізація", "Personalization"), if (settings.customInstructionsEnabled) tr(settings.language, "Свои инструкции включены", "Власні інструкції увімкнено", "Custom instructions enabled") else tr(settings.language, "Стандартное поведение", "Стандартна поведінка", "Default behavior"), onPersonalization)
                HorizontalDivider(Modifier.padding(start = 58.dp))
                SettingsRow(PhIcons.Cpu, tr(settings.language, "Инструменты агента", "Інструменти агента", "Agent tools"), if (settings.agentFileToolsEnabled) tr(settings.language, "Файловые инструменты включены", "Файлові інструменти увімкнено", "File tools enabled") else tr(settings.language, "Выключены", "Вимкнено", "Disabled"), onTools)
            }
        }
        item {
            SettingsSection(tr(settings.language, "УСТРОЙСТВО", "ПРИСТРІЙ", "DEVICE")) {
                SettingsRow(PhIcons.Folders, tr(settings.language, "Проекты и доступ", "Проєкти та доступ", "Projects and access"), if (settings.projects.isEmpty()) tr(settings.language, "Папки не добавлены", "Папки не додано", "No folders added") else tr(settings.language, "${settings.projects.size} рабочих папок", "${settings.projects.size} робочих папок", "${settings.projects.size} workspace folders"), onDevice)
                HorizontalDivider(Modifier.padding(start = 58.dp))
                SettingsRow(PhIcons.Shield, tr(settings.language, "Разрешения Android", "Дозволи Android", "Android permissions"), tr(settings.language, "Только выбранные папки", "Лише вибрані папки", "Selected folders only"), onDevice)
            }
        }
        item {
            SettingsSection(tr(settings.language, "ПРИЛОЖЕНИЕ", "ЗАСТОСУНОК", "APPLICATION")) {
                SettingsRow(PhIcons.Palette, tr(settings.language, "Оформление и язык", "Оформлення та мова", "Appearance and language"), "Catppuccin", onAppearance)
                HorizontalDivider(Modifier.padding(start = 58.dp))
                InfoRow(PhIcons.Storage, tr(settings.language, "Локальные данные", "Локальні дані", "Local data"), tr(settings.language, "История хранится на этом устройстве", "Історія зберігається на цьому пристрої", "History is stored on this device"))
            }
        }
    }
}

@Composable
private fun SettingsSection(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, Modifier.padding(start = 4.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = .8.sp)
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, Modifier.size(25.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Icon(PhIcons.Next, contentDescription = null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ModelsPage(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit, onEdit: (String) -> Unit, onAdd: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Добавь сколько угодно подключений и переключай их из поля ввода.", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Icon(PhIcons.Plus, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Добавить модель")
            }
        }
        items(settings.profiles, key = { it.id }) { profile ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onEdit(profile.id) },
                shape = RoundedCornerShape(17.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = if (profile.id == settings.activeProfileId) XaBlue.copy(alpha = .18f) else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(46.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(PhIcons.Robot, null, Modifier.size(24.dp), tint = if (profile.id == settings.activeProfileId) XaBlue else MaterialTheme.colorScheme.onSurface) }
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(profile.name, fontWeight = FontWeight.Bold)
                            if (profile.id == settings.activeProfileId) Text("  АКТИВНА", color = XaBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("${presetFor(profile.provider).label} · ${profile.model}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (profile.id != settings.activeProfileId) {
                        TextButton(onClick = { onSettingsChange(settings.copy(activeProfileId = profile.id)) }) { Text("Выбрать") }
                    } else Icon(PhIcons.Check, null, Modifier.size(20.dp), tint = XaBlue)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditor(
    profile: ModelProfile,
    settings: AppSettings,
    testing: Boolean,
    connectionResult: String?,
    onUpdate: (ModelProfile) -> Unit,
    onTest: (ModelProfile) -> Unit,
    onActivate: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var showKey by rememberSaveable(profile.id) { mutableStateOf(false) }
    val activity = LocalContext.current.findActivity()
    DisposableEffect(showKey, activity) {
        if (showKey) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            OutlinedTextField(profile.name, { onUpdate(profile.copy(name = it)) }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        }
        item { ProviderPicker(profile.provider) { type -> val preset = presetFor(type); onUpdate(profile.copy(provider = type, name = preset.label, baseUrl = preset.baseUrl, model = preset.defaultModel)) } }
        item { OutlinedTextField(profile.baseUrl, { onUpdate(profile.copy(baseUrl = it)) }, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) }
        item {
            OutlinedTextField(profile.model, { onUpdate(profile.copy(model = it)) }, label = { Text("ID модели") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            val models = presetFor(profile.provider).models
            if (models.isNotEmpty()) {
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    models.forEach { model ->
                        Surface(Modifier.clickable { onUpdate(profile.copy(model = model)) }, shape = RoundedCornerShape(10.dp), color = if (profile.model == model) XaBlue.copy(alpha = .2f) else MaterialTheme.colorScheme.surface) {
                            Text(model, Modifier.padding(horizontal = 9.dp, vertical = 7.dp), color = if (profile.model == model) XaBlue else MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                profile.apiKey,
                { onUpdate(profile.copy(apiKey = it)) },
                label = { Text("API-ключ") },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) PhIcons.EyeOff else PhIcons.Eye, if (showKey) "Скрыть" else "Показать") } },
                supportingText = { Text(if (presetFor(profile.provider).apiKeyOptional) "Можно оставить пустым" else "Зашифрован Android Keystore") }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(onClick = { onTest(profile) }, enabled = !testing && profile.baseUrl.isNotBlank() && profile.model.isNotBlank(), modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                    if (testing) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp) else Icon(PhIcons.Cpu, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp)); Text("Проверить")
                }
                OutlinedButton(onClick = onDuplicate, shape = RoundedCornerShape(13.dp)) { Icon(PhIcons.Copy, "Дублировать") }
            }
            connectionResult?.let { Text(it, Modifier.padding(top = 8.dp), color = if (it == "Подключение работает") XaBlue else MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        }
        item {
            if (profile.id != settings.activeProfileId) OutlinedButton(onClick = onActivate, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                Icon(PhIcons.Check, null); Spacer(Modifier.width(8.dp)); Text("Сделать активной")
            }
        }
        if (settings.profiles.size > 1) item {
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Icon(PhIcons.Trash, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(8.dp)); Text("Удалить подключение", color = MaterialTheme.colorScheme.error) }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun DeviceAccessPage(settings: AppSettings, onSettingsChanged: (AppSettings) -> Unit) {
    val context = LocalContext.current
    val permissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    val rootLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, permissionFlags) }
            onSettingsChanged(settings.copy(projectsRootUri = uri.toString()))
        }
    }
    val externalLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, permissionFlags) }
            val name = runCatching { DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':').substringAfterLast('/').ifBlank { "Новый проект" } }.getOrDefault("Новый проект")
            onSettingsChanged(settings.copy(projects = settings.projects + ProjectWorkspace(name = name, treeUri = uri.toString(), managed = false)))
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp)) {
                    Icon(PhIcons.Folders, null, Modifier.size(34.dp), tint = XaBlue)
                    Spacer(Modifier.height(12.dp))
                    Text("Папка новых проектов", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(if (settings.projectsRootUri.isBlank()) "Выберите место, где XaCode будет создавать подпапки проектов." else "Новые проекты будут создаваться внутри этой папки.", Modifier.padding(top = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { rootLauncher.launch(null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Icon(PhIcons.Folders, null); Spacer(Modifier.width(8.dp)); Text(if (settings.projectsRootUri.isBlank()) "Выбрать папку" else "Изменить папку")
                    }
                }
            }
        }
        item { OutlinedButton(onClick = { externalLauncher.launch(null) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(PhIcons.Paperclip, null); Spacer(Modifier.width(8.dp)); Text("Подключить существующую папку") } }
        items(settings.projects, key = { it.id }) { project ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(PhIcons.Folders, null, Modifier.size(24.dp)); Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) { Text(project.name, fontWeight = FontWeight.SemiBold); Text(if (project.managed) "Создан XaCode" else "Подключённая папка", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
                    IconButton(onClick = {
                        if (!project.managed) runCatching { context.contentResolver.releasePersistableUriPermission(android.net.Uri.parse(project.treeUri), permissionFlags) }
                        onSettingsChanged(settings.copy(projects = settings.projects.filterNot { it.id == project.id }))
                    }) { Icon(PhIcons.Trash, "Удалить проект") }
                }
            }
        }
        item {
            SettingsSection("ДОСТУП") {
                InfoRow(PhIcons.FileCode, "Чтение и запись", "XaCode сможет работать с кодом и файлами проекта")
                HorizontalDivider(Modifier.padding(start = 58.dp))
                InfoRow(PhIcons.Key, "Доступ сохраняется", "Повторно выбирать папку после перезапуска не нужно")
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, subtitle: String) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(16.dp))
        Column { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp) }
    }
}

@Composable
private fun PersonalizationPage(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SettingsSection("ИНСТРУКЦИИ") {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Свои инструкции", fontWeight = FontWeight.SemiBold); Text("Язык, стиль и технологии", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                    XaSwitch(settings.customInstructionsEnabled) { onChange(settings.copy(customInstructionsEnabled = it)) }
                }
                if (settings.customInstructionsEnabled) OutlinedTextField(
                    settings.customInstructions,
                    { onChange(settings.copy(customInstructions = it)) },
                    label = { Text("Как должен отвечать XaCode") },
                    minLines = 4,
                    maxLines = 9,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }
        item {
            SettingsSection("ГЕНЕРАЦИЯ") {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Своя temperature", fontWeight = FontWeight.SemiBold); Text("Точность ↔ творчество", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                    XaSwitch(settings.temperatureEnabled) { onChange(settings.copy(temperatureEnabled = it)) }
                }
                if (settings.temperatureEnabled) Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("${"%.1f".format(settings.temperature)}", color = XaBlue, fontWeight = FontWeight.Bold)
                    Slider(settings.temperature, { onChange(settings.copy(temperature = it)) }, valueRange = 0f..2f, steps = 19)
                }
            }
        }
    }
}

@Composable
private fun ToolsPage(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("По подходу XaCode Desktop модель получает только включённые инструменты. На Android они работают строго внутри папки проекта.", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
        }
        item { SettingsSection(tr(settings.language, "ФАЙЛЫ ПРОЕКТА", "ФАЙЛИ ПРОЄКТУ", "PROJECT FILES")) {
            ToggleRow(tr(settings.language, "Файловые инструменты", "Файлові інструменти", "File tools"), tr(settings.language, "Чтение, запись, поиск, создание и переименование", "Читання, запис, пошук, створення та перейменування", "Read, write, search, create and rename"), settings.agentFileToolsEnabled) { onChange(settings.copy(agentFileToolsEnabled = it)) }
            HorizontalDivider(Modifier.padding(start = 16.dp))
            ToggleRow(tr(settings.language, "Проверять изменения", "Перевіряти зміни", "Verify changes"), tr(settings.language, "После работы перечитать изменённые файлы", "Після роботи перечитати змінені файли", "Re-read changed files after work"), settings.autoVerifyChanges) { onChange(settings.copy(autoVerifyChanges = it)) }
        } }
        item { SettingsSection(tr(settings.language, "ОПАСНЫЕ ВОЗМОЖНОСТИ", "НЕБЕЗПЕЧНІ МОЖЛИВОСТІ", "RISKY CAPABILITIES")) {
            ToggleRow(tr(settings.language, "Удаление файлов агентом", "Видалення файлів агентом", "Agent file deletion"), tr(settings.language, "Разрешает модели без отдельного диалога удалять файлы внутри проекта", "Дозволяє моделі без окремого діалогу видаляти файли в проєкті", "Allows the model to delete project files without another dialog"), settings.destructiveToolsEnabled) { onChange(settings.copy(destructiveToolsEnabled = it)) }
            HorizontalDivider(Modifier.padding(start = 16.dp))
            ToggleRow(tr(settings.language, "Загрузки из интернета", "Завантаження з інтернету", "Internet downloads"), tr(settings.language, "Только публичные HTTPS-адреса, до 25 МБ", "Лише публічні HTTPS-адреси, до 25 МБ", "Public HTTPS addresses only, up to 25 MB"), settings.networkDownloadsEnabled) { onChange(settings.copy(networkDownloadsEnabled = it)) }
            HorizontalDivider(Modifier.padding(start = 16.dp))
            ToggleRow(tr(settings.language, "Запуск Python агентом", "Запуск Python агентом", "Agent Python execution"), tr(settings.language, "Код выполняется внутри процесса приложения — включайте только для доверенных проектов", "Код виконується в процесі застосунку — вмикайте лише для довірених проєктів", "Code runs inside the app process — enable only for trusted projects"), settings.pythonExecutionEnabled) { onChange(settings.copy(pythonExecutionEnabled = it)) }
        } }
        item { SettingsSection("ДОСТУПНЫЕ ИНСТРУМЕНТЫ") {
            InfoRow(PhIcons.FileCode, "read_file · write_file · edit_file", "Чтение и изменение кода")
            HorizontalDivider(Modifier.padding(start = 58.dp))
            InfoRow(PhIcons.Search, "list · find · search · inspect", "Навигация и анализ проекта")
            HorizontalDivider(Modifier.padding(start = 58.dp))
            InfoRow(PhIcons.Folders, "create · rename · delete", "Управление файлами и папками")
            HorizontalDivider(Modifier.padding(start = 58.dp))
            InfoRow(PhIcons.Sliders, "apply_patch · undo · todos", "Правки, откат и план работы")
        } }
        item { SettingsSection("СРЕДА ANDROID") {
            InfoRow(PhIcons.Check, "Файловый агент", "Работает через доступ Android к выбранной папке")
            HorizontalDivider(Modifier.padding(start = 58.dp))
            InfoRow(PhIcons.FileCode, "Python 3.13", "Встроен: запуск .py и синхронизация результатов")
            HorizontalDivider(Modifier.padding(start = 58.dp))
            InfoRow(PhIcons.Cpu, "Node.js и npm", "Нужен runtime или мост с Termux")
        } }
    }
}

@Composable
private fun AppearancePage(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    var languageMenu by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            SettingsSection(tr(settings.language, "ЯЗЫК", "МОВА", "LANGUAGE")) {
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { languageMenu = true }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(PhIcons.Chat, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr(settings.language, "Язык приложения", "Мова застосунку", "App language"), fontWeight = FontWeight.SemiBold)
                            Text(settings.language.label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Icon(PhIcons.Next, null, Modifier.size(17.dp))
                    }
                    DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                        UiLanguage.entries.forEach { language ->
                            DropdownMenuItem(
                                text = { Text(language.label) },
                                trailingIcon = { if (language == settings.language) Icon(PhIcons.Check, null, tint = XaBlue) },
                                onClick = { languageMenu = false; onChange(settings.copy(language = language)) }
                            )
                        }
                    }
                }
            }
        }
        item {
            SettingsSection(tr(settings.language, "ТЕМА", "ТЕМА", "THEME")) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Catppuccin", fontWeight = FontWeight.Bold); Text(tr(settings.language, "Тёмная фиолетовая палитра", "Темна фіолетова палітра", "Dark purple palette"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                    Icon(PhIcons.Check, null, tint = XaBlue)
                }
                Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(Color(0xFFCBA6F7), Color(0xFF1E1E2E), Color(0xFFCDD6F4), Color(0xFFF38BA8)).forEach { color -> Surface(Modifier.size(36.dp), shape = CircleShape, color = color, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {} }
                }
            }
        }
        item { SettingsSection(tr(settings.language, "ИНТЕРФЕЙС", "ІНТЕРФЕЙС", "INTERFACE")) { ToggleRow(tr(settings.language, "Плавные анимации", "Плавні анімації", "Smooth animations"), tr(settings.language, "Переходы экранов и появление элементов", "Переходи екранів і поява елементів", "Screen transitions and element appearance"), settings.animationsEnabled) { onChange(settings.copy(animationsEnabled = it)) } } }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp) }
        Spacer(Modifier.width(12.dp)); XaSwitch(checked, onCheckedChange)
    }
}

@Composable
private fun XaSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = XaBlue,
            checkedBorderColor = XaBlue,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
private fun ProviderPicker(selected: ProviderType, onSelected: (ProviderType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Icon(PhIcons.Robot, null); Spacer(Modifier.width(8.dp)); Text(presetFor(selected).label, Modifier.weight(1f)); Icon(PhIcons.Next, null, Modifier.size(17.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            providerPresets.forEach { preset ->
                DropdownMenuItem(
                    text = { Column { Text(preset.label); Text(preset.defaultModel.ifBlank { "Настроить вручную" }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) } },
                    onClick = { expanded = false; onSelected(preset.type) },
                    trailingIcon = { if (preset.type == selected) Icon(PhIcons.Check, null, tint = XaBlue) }
                )
            }
        }
    }
}
