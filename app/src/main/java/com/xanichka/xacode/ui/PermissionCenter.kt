package com.xanichka.xacode.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xanichka.xacode.ui.icons.PhIcons
import com.xanichka.xacode.ui.theme.XaBlue
import com.xanichka.xacode.model.UiLanguage

@Composable
fun PermissionCenter(hasProjects: Boolean, language: UiLanguage, onChooseFolder: () -> Unit, onDone: () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(22.dp)) {
                Spacer(Modifier.height(22.dp)); BrandLogo(66.dp); Spacer(Modifier.height(22.dp))
                Text(tr(language, "Доступ для XaCode", "Доступ для XaCode", "XaCode access"), fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text(tr(language, "Выбери только то, что понадобится. Разрешения можно изменить позже.", "Вибери лише те, що потрібно. Дозволи можна змінити пізніше.", "Choose only what you need. Permissions can be changed later."), Modifier.padding(top = 8.dp, bottom = 22.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                PermissionChoice("files", tr(language, "Папки проектов", "Папки проєктів", "Project folders"), if (hasProjects) tr(language, "Рабочая папка уже выбрана", "Робочу папку вже вибрано", "Workspace folder selected") else tr(language, "Чтение и запись только в выбранной папке", "Читання та запис лише у вибраній папці", "Read and write only in the selected folder"), hasProjects, onChooseFolder)
                Text(tr(language, "Вложения выбираются через системное окно Android. Камера, микрофон и уведомления не запрашиваются.", "Вкладення вибираються через системне вікно Android. Камера, мікрофон і сповіщення не запитуються.", "Attachments use the Android system picker. Camera, microphone and notification permissions are not requested."), color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                Spacer(Modifier.weight(1f))
                Button(onClick = onDone, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text(tr(language, "Продолжить", "Продовжити", "Continue"), Modifier.padding(vertical = 5.dp), fontWeight = FontWeight.Bold) }
                TextButton(onClick = onDone, Modifier.fillMaxWidth()) { Text(tr(language, "Пока без доступа", "Поки без доступу", "Continue without access")) }
                Text(tr(language, "Доступ можно изменить в настройках", "Доступ можна змінити в налаштуваннях", "Access can be changed in settings"), Modifier.align(Alignment.CenterHorizontally), color = XaBlue, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PermissionChoice(id: String, title: String, subtitle: String, checked: Boolean, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = onClick), shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Icon(if (id == "files") PhIcons.Folders else if (id == "notifications") PhIcons.Chat else PhIcons.Paperclip, null, Modifier.size(24.dp), tint = if (checked) XaBlue else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
            Checkbox(checked = checked, onCheckedChange = { onClick() })
        }
    }
}
