package com.xanichka.xacode.ui

import android.content.Intent
import android.provider.DocumentsContract
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.xanichka.xacode.model.presetFor
import com.xanichka.xacode.model.providerPresets
import com.xanichka.xacode.ui.icons.PhIcons
import com.xanichka.xacode.ui.theme.XaBlue
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class SettingsPage { HOME, MODELS, PROFILE, DEVICE, PERSONALIZATION }

@Composable
fun SettingsScreen(
    initial: AppSettings,
    testingProfileId: String?,
    connectionResult: String?,
    onTest: (AppSettings, ModelProfile) -> Unit,
    onClearResult: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var page by rememberSaveable { mutableStateOf(SettingsPage.HOME) }
    var editingId by rememberSaveable(initial.activeProfileId) { mutableStateOf(initial.activeProfileId) }

    fun goBack() {
        if (page == SettingsPage.HOME) onSave(draft) else page = SettingsPage.HOME
    }
    BackHandler(onBack = ::goBack)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            SettingsHeader(
                title = when (page) {
                    SettingsPage.HOME -> "Настройки"
                    SettingsPage.MODELS -> "Модели и API"
                    SettingsPage.PROFILE -> "Подключение"
                    SettingsPage.DEVICE -> "Доступ к устройству"
                    SettingsPage.PERSONALIZATION -> "Персонализация"
                },
                isHome = page == SettingsPage.HOME,
                onBack = ::goBack,
                onDone = { onSave(draft) }
            )
            when (page) {
                SettingsPage.HOME -> SettingsHome(
                    draft,
                    onModels = { page = SettingsPage.MODELS },
                    onDevice = { page = SettingsPage.DEVICE },
                    onPersonalization = { page = SettingsPage.PERSONALIZATION }
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
                SettingsPage.DEVICE -> DeviceAccessPage(draft) { draft = draft.copy(workspaceUri = it) }
                SettingsPage.PERSONALIZATION -> PersonalizationPage(draft) { draft = it }
            }
        }
    }
}

@Composable
private fun SettingsHeader(title: String, isHome: Boolean, onBack: () -> Unit, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleIconButton(if (isHome) PhIcons.Close else PhIcons.Back, if (isHome) "Закрыть" else "Назад", onBack)
        Text(title, Modifier.weight(1f).padding(start = 14.dp), fontSize = 21.sp, fontWeight = FontWeight.Bold)
        if (isHome) TextButton(onClick = onDone) { Text("Готово", color = XaBlue, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun SettingsHome(settings: AppSettings, onModels: () -> Unit, onDevice: () -> Unit, onPersonalization: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandLogo(62.dp); Spacer(Modifier.width(14.dp))
                Column { Text("XaCode Android", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("Версия 0.3.0", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item {
            SettingsSection("AI И РАЗРАБОТКА") {
                SettingsRow(PhIcons.Robot, "Модели и API", "${settings.profiles.size} подключений", onModels)
                HorizontalDivider(Modifier.padding(start = 58.dp))
                SettingsRow(PhIcons.Sliders, "Персонализация", if (settings.customInstructionsEnabled) "Свои инструкции включены" else "Стандартное поведение", onPersonalization)
            }
        }
        item {
            SettingsSection("УСТРОЙСТВО") {
                SettingsRow(PhIcons.Folders, "Файлы и рабочая папка", if (settings.workspaceUri.isBlank()) "Доступ не выбран" else "Доступ разрешён", onDevice)
                HorizontalDivider(Modifier.padding(start = 58.dp))
                SettingsRow(PhIcons.Shield, "Безопасность", "Без root · Android SAF") { }
            }
        }
        item {
            SettingsSection("ПРИЛОЖЕНИЕ") {
                SettingsRow(PhIcons.Palette, "Оформление", "Тёмная тема · синий акцент") { }
                HorizontalDivider(Modifier.padding(start = 58.dp))
                SettingsRow(PhIcons.Cpu, "Производительность", "Фоновое сохранение включено") { }
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

@Composable
private fun DeviceAccessPage(settings: AppSettings, onWorkspaceChanged: (String) -> Unit) {
    val context = LocalContext.current
    val permissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                if (settings.workspaceUri.isNotBlank() && settings.workspaceUri != uri.toString()) {
                    context.contentResolver.releasePersistableUriPermission(android.net.Uri.parse(settings.workspaceUri), permissionFlags)
                }
                context.contentResolver.takePersistableUriPermission(uri, permissionFlags)
            }
            onWorkspaceChanged(uri.toString())
        }
    }
    val folderName = remember(settings.workspaceUri) {
        runCatching { if (settings.workspaceUri.isBlank()) "Не выбрана" else DocumentsContract.getTreeDocumentId(android.net.Uri.parse(settings.workspaceUri)).substringAfterLast(':') }.getOrDefault("Выбранная папка")
    }
    val entryCount by produceState<Int?>(initialValue = null, settings.workspaceUri) {
        value = if (settings.workspaceUri.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching { WorkspaceRepository(context).list(settings.workspaceUri).size }.getOrNull()
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp)) {
                    Icon(PhIcons.Folders, null, Modifier.size(34.dp), tint = XaBlue)
                    Spacer(Modifier.height(12.dp))
                    Text("Рабочая папка", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        buildString {
                            append(folderName.ifBlank { "Корень выбранного хранилища" })
                            entryCount?.let { append(" · $it элементов") }
                        },
                        Modifier.padding(top = 5.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { launcher.launch(null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Icon(PhIcons.Folders, null); Spacer(Modifier.width(8.dp)); Text(if (settings.workspaceUri.isBlank()) "Выбрать папку" else "Изменить папку")
                    }
                    if (settings.workspaceUri.isNotBlank()) TextButton(onClick = {
                        runCatching { context.contentResolver.releasePersistableUriPermission(android.net.Uri.parse(settings.workspaceUri), permissionFlags) }
                        onWorkspaceChanged("")
                    }, modifier = Modifier.fillMaxWidth()) { Text("Отозвать доступ") }
                }
            }
        }
        item {
            SettingsSection("КАК ЭТО РАБОТАЕТ") {
                InfoRow(PhIcons.Shield, "Root не нужен", "Android сам выдаёт доступ только к выбранной папке")
                HorizontalDivider(Modifier.padding(start = 58.dp))
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
                    Switch(settings.customInstructionsEnabled, { onChange(settings.copy(customInstructionsEnabled = it)) })
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
                    Switch(settings.temperatureEnabled, { onChange(settings.copy(temperatureEnabled = it)) })
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
