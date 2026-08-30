package com.yingjing.pfa.ui.screens.settings

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yingjing.pfa.R
import com.yingjing.pfa.domain.model.Currency
import com.yingjing.pfa.domain.model.User
import com.yingjing.pfa.ui.components.AvatarCircle
import com.yingjing.pfa.ui.theme.BlueDarkMode
import com.yingjing.pfa.ui.theme.BlueLightMode

/**
 * 设置二级界面通用骨架：手写返回顶栏 + 加深背景 + 垂直滚动内容槽。
 * 顶栏写法对齐 [com.yingjing.pfa.ui.screens.portfolio.HoldingDetailScreen]（不用 material3 TopAppBar）。
 *
 * @param onBack 顶栏返回箭头回调。
 * @param clearStatus 进入时调用，清空共享 [SettingsViewModel] 中的残留消息。
 */
@Composable
internal fun SettingsDetailScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val pageBg = if (isDark) Color(0xFF080807) else Color(0xFFE9E9E3)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** 页头：品牌蓝渐变卡 + 64dp 头像 + 名称/@用户名 + 退出登录。 */
@Composable
internal fun GradientUserHeader(user: User?, onLogout: () -> Unit) {
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
            ) { Text(stringResource(R.string.settings_logout)) }
        }
    }
}

/** 分组卡片：色标标题（品牌蓝图标块 + 标题）+ 内容槽。 */
@Composable
internal fun SettingsGroupCard(
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
internal fun SettingRow(
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

/** 性别选项：存 locale 中性 code，渲染走 stringResource，跨语言保持选中态。 */
private data class GenderOption(val code: String, @StringRes val labelRes: Int)

private val GENDER_OPTIONS = listOf(
    GenderOption("male", R.string.profile_gender_male),
    GenderOption("female", R.string.profile_gender_female),
    GenderOption("other", R.string.profile_gender_other),
)

/**
 * 归一化历史本地化值（"男"/"女"/"其他"）到 locale 中性 code；已是 code 的原样返回。
 * 历史 DB 里存的是本地化文本，切换语言后 chip 选中态会丢失，故读出时归一化为 code。
 */
private fun normalizeGenderCode(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return when (raw) {
        "男" -> "male"
        "女" -> "female"
        "其他" -> "other"
        else -> raw
    }
}

/** 个人资料字段：昵称 / 性别 / 年龄 / 默认货币 / 保存。状态以当前用户值为初值。 */
@Composable
internal fun ProfileFields(
    nickname0: String,
    gender0: String,
    age0: Int?,
    currency: Currency,
    onCurrency: (Currency) -> Unit,
    onSave: (String, String, Int?) -> Unit,
) {
    var nickname by remember(nickname0) { mutableStateOf(nickname0) }
    var gender by remember(gender0) { mutableStateOf(normalizeGenderCode(gender0) ?: "") }
    var age by remember(age0) { mutableStateOf(age0?.toString() ?: "") }

    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LabeledSection(stringResource(R.string.profile_nickname)) {
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LabeledSection(stringResource(R.string.profile_gender)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GENDER_OPTIONS.forEach { opt ->
                    FilterChip(
                        selected = gender == opt.code,
                        onClick = { gender = if (gender == opt.code) "" else opt.code },
                        label = { Text(stringResource(opt.labelRes)) },
                    )
                }
            }
        }
        LabeledSection(stringResource(R.string.profile_age)) {
            OutlinedTextField(
                value = age,
                onValueChange = { input -> age = input.filter { it.isDigit() }.take(3) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LabeledSection(stringResource(R.string.profile_default_currency)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Currency.entries.forEach { c ->
                    FilterChip(
                        selected = currency == c,
                        onClick = { onCurrency(c) },
                        label = { Text("${stringResource(c.symbolRes)} ${stringResource(c.labelRes)}") },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = { onSave(nickname, gender, age.toIntOrNull()) }) { Text(stringResource(R.string.profile_save)) }
        }
    }
}

/** 带外置标签的字段区块（标签 + 内容），用于表单连续字段。 */
@Composable
internal fun LabeledSection(label: String, content: @Composable () -> Unit) {
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
internal fun UserItemRow(
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
                        stringResource(R.string.settings_current_badge),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { onSwitch(user.id) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) { Text(stringResource(R.string.settings_switch)) }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { onDelete(user.id) }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** 计划子区域：surfaceVariant 底 + 副标题 + 提示 + 周期 chip + 时间输入 + 保存。 */
@Composable
internal fun SchedulePlanFields(
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
            Text(stringResource(R.string.schedule_period), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    1 to R.string.every_day,
                    3 to R.string.every_3_days,
                    7 to R.string.every_week,
                ).forEach { (d, labelRes) ->
                    FilterChip(
                        selected = interval == d,
                        onClick = { interval = d },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = hourText,
                onValueChange = { input -> hourText = input.filter { it.isDigit() }.take(2) },
                label = { Text(stringResource(R.string.schedule_hour_label)) },
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
                    Text(stringResource(R.string.schedule_save))
                }
            }
        }
    }
}

/** 行情/备份计划的摘要文本（一级卡片用）："每天 9 点" / "每3天 3 点"。 */
@Composable
internal fun scheduleText(intervalDays: Int, hour: Int): String {
    val interval = if (intervalDays <= 1) {
        stringResource(R.string.every_day)
    } else {
        stringResource(R.string.every_n_days, intervalDays)
    }
    return "$interval ${stringResource(R.string.hour_oclock, hour)}"
}

/** 个人资料摘要（一级卡片用）："昵称 · 性别 · 年龄岁 · 货币"，缺失项显示「未设置」。 */
@Composable
internal fun profileSummary(user: User?): String {
    if (user == null) return stringResource(R.string.not_set)
    val notSet = stringResource(R.string.not_set)
    val parts = listOfNotNull(
        user.nickname?.takeIf { it.isNotBlank() } ?: notSet,
        user.gender?.takeIf { it.isNotBlank() } ?: notSet,
        user.age?.let { stringResource(R.string.profile_age_years, it) } ?: notSet,
        stringResource(user.defaultCurrency.labelRes),
    )
    return parts.joinToString(" · ")
}

@Composable
internal fun PassphraseDialog(
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
                label = { Text(stringResource(R.string.settings_passphrase)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pass) }, enabled = pass.length >= 4) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
internal fun SingleFieldDialog(
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
internal fun ChangePasswordDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_change_password)) },
        text = {
            Column {
                OutlinedTextField(
                    value = old,
                    onValueChange = { old = it },
                    label = { Text(stringResource(R.string.settings_old_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = new,
                    onValueChange = { new = it },
                    label = { Text(stringResource(R.string.settings_new_password)) },
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
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

internal fun formatTime(epochMs: Long): String =
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(epochMs))
