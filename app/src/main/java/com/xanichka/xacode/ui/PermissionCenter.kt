package com.xanichka.xacode.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.xanichka.xacode.model.UiLanguage
import com.xanichka.xacode.ui.icons.PhIcons
import com.xanichka.xacode.ui.theme.XaBlue

@Composable
fun PermissionCenter(hasProjects: Boolean, language: UiLanguage, onChooseFolder: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val notificationGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val powerManager = context.getSystemService(PowerManager::class.java)
    val batteryUnrestricted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    val openBatteryAccess = {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(direct) }.getOrElse {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }
    }

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(22.dp)) {
                Spacer(Modifier.height(18.dp)); BrandLogo(62.dp); Spacer(Modifier.height(18.dp))
                Text(tr(language, "Настрой XaCode для фоновой работы", "Налаштуй XaCode для фонової роботи", "Set up XaCode for background work"), fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text(tr(language, "Это показывается один раз. Android не разрешает выдать доступ автоматически — нажми нужные пункты ниже.", "Це показується один раз. Android не дозволяє видати доступ автоматично — натисни потрібні пункти нижче.", "This is shown once. Android requires you to grant each access explicitly."), Modifier.padding(top = 8.dp, bottom = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)

                PermissionChoice("files", tr(language, "Папка проектов", "Папка проєктів", "Project folder"), if (hasProjects) tr(language, "Выбрана — новые проекты создаются здесь", "Вибрана — нові проєкти створюються тут", "Selected — new projects are created here") else tr(language, "Чтение и запись только в выбранной папке", "Читання й запис лише у вибраній папці", "Read and write only in the selected folder"), hasProjects, onChooseFolder)
                PermissionChoice("notifications", tr(language, "Уведомления о завершении", "Сповіщення про завершення", "Completion notifications"), tr(language, "XaCode сообщит, когда агент закончит", "XaCode повідомить, коли агент завершить", "XaCode tells you when the agent finishes"), notificationGranted) {
                    if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                PermissionChoice("battery", tr(language, "Работа при выключенном экране", "Робота з вимкненим екраном", "Work with screen off"), tr(language, "В батарее выбери «Без ограничений»", "У батареї вибери «Без обмежень»", "Choose Unrestricted battery usage"), batteryUnrestricted, openBatteryAccess)

                Text(tr(language, "Во время задачи XaCode показывает постоянное уведомление. Можно перейти в другое приложение — работа и журнал инструментов продолжатся.", "Під час задачі XaCode показує постійне сповіщення. Можна перейти в інший застосунок — робота та журнал інструментів продовжаться.", "While a task runs, XaCode shows an ongoing notification. You may use other apps; work and the tool log continue."), color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 19.sp, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Button(onClick = onDone, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text(tr(language, "Готово, продолжить", "Готово, продовжити", "Done, continue"), Modifier.padding(vertical = 5.dp), fontWeight = FontWeight.Bold) }
                TextButton(onClick = onDone, Modifier.fillMaxWidth()) { Text(tr(language, "Настрою позже", "Налаштую пізніше", "Set up later")) }
                Text(tr(language, "Доступ можно изменить в настройках Android", "Доступ можна змінити в налаштуваннях Android", "Access can be changed in Android settings"), Modifier.align(Alignment.CenterHorizontally), color = XaBlue, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PermissionChoice(id: String, title: String, subtitle: String, checked: Boolean, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = onClick), shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Icon(if (id == "files") PhIcons.Folders else if (id == "notifications") PhIcons.Chat else PhIcons.Cpu, null, Modifier.size(24.dp), tint = if (checked) XaBlue else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
            Checkbox(checked = checked, onCheckedChange = { onClick() })
        }
    }
}
