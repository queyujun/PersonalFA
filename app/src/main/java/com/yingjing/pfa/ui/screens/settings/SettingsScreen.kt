package com.yingjing.pfa.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.User
import com.yingjing.pfa.ui.components.AvatarCircle
import com.yingjing.pfa.ui.theme.BlueDarkMode
import com.yingjing.pfa.ui.theme.BlueLightMode

/**
 * 设置页：5 个语义分组卡片 + 品牌蓝色标标题 + 页头渐变用户卡。
 * 仅视觉/排版重设计；状态与操作全部复用 [SettingsViewModel]，对话框保持不变。
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    var exportPass by remember { mutableStateOf("") }
    var showExportDialog by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showUsernameDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> if (uri != null) viewModel.exportTo(uri, exportPass) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) { importUri = uri; showImportDialog = true } }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GradientUserHeader(user = state.currentUser, onLogout = viewModel::logout)

        // 1. 个人资料
        SettingsGroupCard(icon = Icons.Outlined.Person, title = "个人资料") {
            ProfileFields(
                nickname0 = state.currentUser?.nickname ?: "",
                gender0 = state.currentUser?.gender ?: "",
                age0 = state.currentUser?.age,
                currency = state.currentUser?.defaultCurrency ?: Currency.CNY,
                onCurrency = viewModel::updateCurrency,
                onSave = { n, g, a -> viewModel.updateProfile(n, g, a) },
            )
        }

        // 2. 账号与安全
        SettingsGroupCard(icon = Icons.Outlined.Lock, title = "账号与安全") {
            SettingRow(title = "修改用户名", onClick = { showUsernameDialog = true })
            SettingRow(title = "修改密码", onClick = { showPasswordDialog = true })
            if (state.biometricAvailable) {
                SettingRow(
                    title = "指纹 / 面容登录",
                    subtitle = "开启后可在登录页用指纹/面容快速登录上次账号",
                    trailing = {
                        Switch(
                            checked = state.biometricEnabled,
                            onCheckedChange = { viewModel.setBiometric(it) },
                        )
                    },
                )
            }
        }

        // 3. 账号管理
        SettingsGroupCard(icon = Icons.Outlined.Group, title = "账号管理") {
            state.users.forEach { user ->
                UserItemRow(
                    user = user,
                    isCurrent = user.id == state.currentUser?.id,
                    onSwitch = viewModel::switchUser,
                    onDelete = viewModel::deleteUser,
                )
            }
        }

        // 4. 数据与同步
        SettingsGroupCard(icon = Icons.Outlined.Sync, title = "数据与同步") {
            SettingRow(
                title = "立即刷新行情",
                subtitle = "上次更新：" + (state.lastSyncMs?.let { formatTime(it) } ?: "尚未更新"),
                trailing = {
                    OutlinedButton(onClick = viewModel::refreshQuotesNow) { Text("刷新") }
                },
            )
            SchedulePlanFields(
                subtitle = "行情刷新计划",
                hint = "「提醒」抓取随行情刷新一起进行（同一时间 / 周期）",
                intervalDays = state.syncIntervalDays,
                hour = state.syncHour,
                onSave = { d, h -> viewModel.setSyncSchedule(d, h) },
            )
        }

        // 5. 数据备份
        SettingsGroupCard(icon = Icons.Outlined.CloudUpload, title = "数据备份") {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(onClick = { exportPass = ""; showExportDialog = true }) { Text("立即备份") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("从文件恢复") }
                }
            }
            SettingRow(
                title = "每周自动备份",
                subtitle = "本机加密安全副本（便携备份请用「立即备份」自选口令）",
                trailing = {
                    Switch(
                        checked = state.autoBackupEnabled,
                        onCheckedChange = { viewModel.setAutoBackup(it) },
                    )
                },
            )
            state.statusMessage?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            SchedulePlanFields(
                subtitle = "自动备份计划",
                hint = "启用上方「每周自动备份」后，按此周期 / 时间执行",
                intervalDays = state.backupIntervalDays,
                hour = state.backupHour,
                onSave = { d, h -> viewModel.setBackupSchedule(d, h) },
            )
        }

        Text(
            "备份采用 AES-256-GCM 加密（口令派生密钥）；导出文件建议妥善保存，口令遗失将无法恢复。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    if (showExportDialog) {
        PassphraseDialog(
            title = "设置备份口令",
            confirmText = "导出",
            onConfirm = { pass ->
                exportPass = pass
                showExportDialog = false
                exportLauncher.launch("盈景私助-backup-${System.currentTimeMillis()}.pfa")
            },
            onDismiss = { showExportDialog = false },
        )
    }

    if (showImportDialog) {
        PassphraseDialog(
            title = "输入备份口令",
            confirmText = "恢复",
            onConfirm = { pass ->
                showImportDialog = false
                importUri?.let { viewModel.importFrom(it, pass) }
            },
            onDismiss = { showImportDialog = false },
        )
    }

    if (showUsernameDialog) {
        SingleFieldDialog(
            title = "修改用户名",
            label = "新用户名",
            confirmText = "保存",
            onConfirm = { name -> showUsernameDialog = false; viewModel.changeUsername(name) },
            onDismiss = { showUsernameDialog = false },
        )
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            onConfirm = { old, new -> showPasswordDialog = false; viewModel.changePassword(old, new) },
            onDismiss = { showPasswordDialog = false },
        )
    }
}

/** 页头：品牌蓝渐变卡 + 64dp 头像 + 名称/@用户名 + 退出登录。 */
@Composable
private fun GradientUserHeader(user: User?, onLogout: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val startColor = if (isDark) BlueDarkMode else BlueLightMode
    val endColor = if (isDark) Color(0xFF5BA0F0) else Color(0xFF4A90E2)
    val display = user?.let { it.nickname?.takeIf { s -> s.isNotBlank() } ?: it.username } ?: "-"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(startColor, endColor)))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarCircle(name = display, size = 64.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(display, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "@${user?.username ?: "-"}",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedButton(
                onClick = onLogout,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) { Text("退出登录") }
        }
    }
}

/** 分组卡片：色标标题（品牌蓝图标块 + 标题）+ 内容槽。 */
@Composable
private fun SettingsGroupCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

/** 通用设置行：标题 + 可选副标题 + 可选 trailing 控件；onClick 非空时整行可点并显示 ›。 */
@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val clickable = onClick != null
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (clickable) Modifier.clickable { onClick?.invoke() } else Modifier)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                subtitle?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trailing?.invoke()
            if (clickable && trailing == null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 个人资料字段：昵称 / 性别 / 年龄 / 默认货币 / 保存。状态以当前用户值为初值。 */
@Composable
private fun ProfileFields(
    nickname0: String,
    gender0: String,
    age0: Int?,
    currency: Currency,
    onCurrency: (Currency) -> Unit,
    onSave: (String, String, Int?) -> Unit,
) {
    var nickname by remember(nickname0) { mutableStateOf(nickname0) }
    var gender by remember(gender0) { mutableStateOf(gender0) }
    var age by remember(age0) { mutableStateOf(age0?.toString() ?: "") }

    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LabeledSection("昵称") {
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LabeledSection("性别") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("男", "女", "其他").forEach { g ->
                    FilterChip(
                        selected = gender == g,
                        onClick = { gender = if (gender == g) "" else g },
                        label = { Text(g) },
                    )
                }
            }
        }
        LabeledSection("年龄") {
            OutlinedTextField(
                value = age,
                onValueChange = { input -> age = input.filter { it.isDigit() }.take(3) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LabeledSection("默认货币") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Currency.entries.forEach { c ->
                    FilterChip(
                        selected = currency == c,
                        onClick = { onCurrency(c) },
                        label = { Text("${c.symbol} ${c.label}") },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = { onSave(nickname, gender, age.toIntOrNull()) }) { Text("保存资料") }
        }
    }
}

/** 带外置标签的字段区块（标签 + 内容），用于表单连续字段。 */
@Composable
private fun LabeledSection(label: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

/** 账号列表项：头像 + 用户名 + 「当前」徽章 或 切换/删除。 */
@Composable
private fun UserItemRow(
    user: User,
    isCurrent: Boolean,
    onSwitch: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val name = user.nickname?.takeIf { it.isNotBlank() } ?: user.username
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarCircle(name = name, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Text(user.username, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (isCurrent) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        "当前",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { onSwitch(user.id) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) { Text("切换") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { onDelete(user.id) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** 计划子区域：surfaceVariant 底 + 副标题 + 提示 + 周期 chip + 时间输入 + 保存。 */
@Composable
private fun SchedulePlanFields(
    subtitle: String,
    hint: String,
    intervalDays: Int,
    hour: Int,
    onSave: (Int, Int) -> Unit,
) {
    var interval by remember(intervalDays) { mutableStateOf(intervalDays) }
    var hourText by remember(hour) { mutableStateOf(hour.toString()) }

    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
        ) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text("周期", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "每天", 3 to "每3天", 7 to "每周").forEach { (d, label) ->
                    FilterChip(
                        selected = interval == d,
                        onClick = { interval = d },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = hourText,
                onValueChange = { input -> hourText = input.filter { it.isDigit() }.take(2) },
                label = { Text("时间（整点，0-23）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = { onSave(interval, hourText.toIntOrNull()?.coerceIn(0, 23) ?: hour) }) {
                    Text("保存计划")
                }
            }
        }
    }
}

@Composable
private fun PassphraseDialog(
    title: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("口令") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pass) }, enabled = pass.length >= 4) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SingleFieldDialog(
    title: String,
    label: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ChangePasswordDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改密码") },
        text = {
            Column {
                OutlinedTextField(
                    value = old,
                    onValueChange = { old = it },
                    label = { Text("旧密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = new,
                    onValueChange = { new = it },
                    label = { Text("新密码（≥4 位）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(old, new) },
                enabled = old.isNotBlank() && new.length >= 4,
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun formatTime(epochMs: Long): String =
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(epochMs))
