package com.xanichka.xacode.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xanichka.xacode.ui.icons.PhIcons
import com.xanichka.xacode.ui.theme.XaBlue

@Composable
fun PermissionCenter(hasProjects: Boolean, onChooseFolder: () -> Unit, onDone: () -> Unit) {
    val selected = remember { mutableStateMapOf("camera" to false, "microphone" to false, "photos" to false, "notifications" to false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onDone() }
    fun requestSelected() {
        val permissions = buildList {
            if (selected["camera"] == true) add(Manifest.permission.CAMERA)
            if (selected["microphone"] == true) add(Manifest.permission.RECORD_AUDIO)
            // Android's system picker grants access only to explicitly selected images.
            if (selected["notifications"] == true && Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.distinct().toTypedArray()
        if (permissions.isEmpty()) onDone() else permissionLauncher.launch(permissions)
    }
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(22.dp)) {
                Spacer(Modifier.height(22.dp)); BrandLogo(66.dp); Spacer(Modifier.height(22.dp))
                Text("Доступ для XaCode", fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text("Выбери только то, что понадобится. Разрешения можно изменить позже в настройках Android.", Modifier.padding(top = 8.dp, bottom = 22.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                PermissionChoice("files", "Папки проектов", if (hasProjects) "Рабочая папка уже выбрана" else "Чтение и запись только в выбранной папке", hasProjects, onChooseFolder)
                PermissionChoice("camera", "Камера", "Фото кода, ошибок и документов", selected["camera"] == true) { selected["camera"] = !(selected["camera"] ?: false) }
                PermissionChoice("microphone", "Микрофон", "Голосовой ввод запросов", selected["microphone"] == true) { selected["microphone"] = !(selected["microphone"] ?: false) }
                PermissionChoice("photos", "Изображения", "Прикрепление скриншотов из галереи", selected["photos"] == true) { selected["photos"] = !(selected["photos"] ?: false) }
                PermissionChoice("notifications", "Уведомления", "Сообщать о завершении долгой задачи", selected["notifications"] == true) { selected["notifications"] = !(selected["notifications"] ?: false) }
                Spacer(Modifier.weight(1f))
                Button(onClick = ::requestSelected, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("Продолжить", Modifier.padding(vertical = 5.dp), fontWeight = FontWeight.Bold) }
                TextButton(onClick = onDone, Modifier.fillMaxWidth()) { Text("Пока без разрешений") }
                Text("Root-доступ не используется", Modifier.align(Alignment.CenterHorizontally), color = XaBlue, fontSize = 11.sp)
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
