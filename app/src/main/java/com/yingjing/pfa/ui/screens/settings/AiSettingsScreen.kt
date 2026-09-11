package com.yingjing.pfa.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.R
import com.yingjing.pfa.data.ai.AiApiProtocol
import com.yingjing.pfa.data.ai.AiProviderPreset
import com.yingjing.pfa.data.ai.AiReportTone

/** FlowRow 属实验性 layout API，本文件内白名单启用。 */
@OptIn(ExperimentalLayoutApi::class)

/**
 * AI 档案编辑页（列表页点行进入）：预设服务商档案 + 自定义档案共用。
 *
 * - 预设档案：隐藏名称/服务商 chips（服务商由档案固定），表单只有 URL/模型/key/协议/明细；
 * - 自定义档案：名称输入 + 服务商 chips（仅回填模板 defaults，不锁定）+ 删除入口；
 * - 新建（profileId=new）：同自定义档案，但无删除按钮。
 *
 * API Key 明文只存在于本页输入框的临时状态，随「保存配置」提交后即丢弃；
 * 已保存的 key 只显示尾号掩码，支持整键清除。Base URL 强制 https://（target 35 禁明文流量）。
 */
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit = onBack,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // key 输入框的本地明文；保存成功后由 [onSave] 清空。
    var apiKeyInput by rememberSaveable { mutableStateOf("") }
    var showKeyInputError by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    fun submitSave() {
        if (!state.profile.baseUrl.startsWith("https://")) {
            showKeyInputError = true
            return
        }
        showKeyInputError = false
        viewModel.save(apiKeyInput)
        apiKeyInput = ""
    }

    fun submitTest() {
        if (!state.profile.baseUrl.startsWith("https://")) {
            showKeyInputError = true
            return
        }
        showKeyInputError = false
        viewModel.testConnection(apiKeyInput)
    }

    SettingsDetailScaffold(
        title = if (state.isPreset) {
            stringResource(state.profile.provider.labelRes())
        } else {
            stringResource(R.string.ai_profile_edit_title)
        },
        onBack = onBack,
    ) {
        if (!state.isPreset) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                LabeledSection(stringResource(R.string.ai_profile_name)) {
                    OutlinedTextField(
                        value = state.profile.name,
                        onValueChange = viewModel::setName,
                        placeholder = { Text(stringResource(R.string.ai_profile_name_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 0.dp),
                    )
                }
                ProviderSection(
                    selected = state.profile.provider,
                    onProvider = viewModel::setProvider,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            LabeledSection(stringResource(R.string.ai_base_url)) {
                OutlinedTextField(
                    value = state.profile.baseUrl,
                    onValueChange = viewModel::setBaseUrl,
                    placeholder = { Text(stringResource(R.string.ai_base_url_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    isError = showKeyInputError,
                    supportingText = if (showKeyInputError) {
                        { Text(stringResource(R.string.ai_base_url_must_be_https)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LabeledSection(stringResource(R.string.ai_model)) {
                OutlinedTextField(
                    value = state.profile.model,
                    onValueChange = viewModel::setModel,
                    placeholder = { Text(stringResource(R.string.ai_model_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ProtocolSection(
                selected = state.profile.protocol,
                onProtocol = viewModel::setProtocol,
            )
            ToneSection(
                selected = state.profile.tone,
                onTone = viewModel::setTone,
            )
            MaxTokensField(
                value = state.profile.maxTokens,
                onValueChange = viewModel::setMaxTokens,
            )
            ApiKeyField(
                hasKey = state.hasKey,
                keyTail = state.keyTail,
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                onClear = viewModel::clearKey,
            )
            DetailToggleRow(
                includeDetails = state.profile.includeDetails,
                onToggle = viewModel::setIncludeDetails,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    stringResource(R.string.ai_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ActionSection(
            saving = state.saving,
            testing = state.testing,
            status = state.status,
            statusArg = state.profile.model,
            statusDetail = state.statusDetail,
            canSubmit = state.profile.isConfigured,
            onSave = ::submitSave,
            onTest = ::submitTest,
        )

        // 删除入口：仅已落盘的自定义档案显示（预设档案固定存在，新建档案无物可删）。
        if (!state.isPreset && !state.isNew) {
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.ai_profile_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.ai_profile_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.ai_profile_delete_confirm_body,
                        state.profile.displayName(),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteProfile { removed -> if (removed) onDeleted() }
                }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/** 服务商预设 chips：自定义档案选模板回填默认 Base URL / 模型（仍可手改）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderSection(
    selected: AiProviderPreset,
    onProvider: (AiProviderPreset) -> Unit,
) {
    LabeledSection(stringResource(R.string.ai_provider)) {
        // FlowRow 自动换行：7 个 chip 一行放不下，Row 会把后面的挤出屏幕。
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val presets = listOf(
                AiProviderPreset.DEEPSEEK,
                AiProviderPreset.OPENAI,
                AiProviderPreset.KIMI,
                AiProviderPreset.QWEN,
                AiProviderPreset.HUNYUAN,
                AiProviderPreset.DOUBAO,
                AiProviderPreset.CUSTOM,
            )
            presets.forEach { preset ->
                FilterChip(
                    selected = selected == preset,
                    onClick = { onProvider(preset) },
                    label = { Text(stringResource(preset.labelRes())) },
                )
            }
        }
    }
}

/** 服务商显示名资源；包内共用（SettingsScreen 的 AI 摘要也用它）。 */
internal fun AiProviderPreset.labelRes(): Int = when (this) {
    AiProviderPreset.DEEPSEEK -> R.string.ai_provider_deepseek
    AiProviderPreset.OPENAI -> R.string.ai_provider_openai
    AiProviderPreset.KIMI -> R.string.ai_provider_kimi
    AiProviderPreset.QWEN -> R.string.ai_provider_qwen
    AiProviderPreset.HUNYUAN -> R.string.ai_provider_hunyuan
    AiProviderPreset.DOUBAO -> R.string.ai_provider_doubao
    AiProviderPreset.CUSTOM -> R.string.ai_provider_custom
}

/**
 * 接口协议选择：Chat Completions 为主流通用；部分服务商（如腾讯 TokenHub hy3）
 * 只开放 Responses 端点，选错会得到「服务 ID 不存在」类 400 错误。
 */
@Composable
private fun ProtocolSection(
    selected: AiApiProtocol,
    onProtocol: (AiApiProtocol) -> Unit,
) {
    LabeledSection(stringResource(R.string.ai_protocol)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selected == AiApiProtocol.CHAT_COMPLETIONS,
                onClick = { onProtocol(AiApiProtocol.CHAT_COMPLETIONS) },
                label = { Text(stringResource(R.string.ai_protocol_chat_completions)) },
            )
            FilterChip(
                selected = selected == AiApiProtocol.RESPONSES,
                onClick = { onProtocol(AiApiProtocol.RESPONSES) },
                label = { Text(stringResource(R.string.ai_protocol_responses)) },
            )
        }
    }
}

/**
 * 语气档选择：分析师（专业评判）/ 伙伴（叙事化陪伴）。只影响 AI 输出口吻，
 * 不改变数据外发范围。
 */
@Composable
private fun ToneSection(
    selected: AiReportTone,
    onTone: (AiReportTone) -> Unit,
) {
    LabeledSection(stringResource(R.string.ai_tone)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selected == AiReportTone.ANALYST,
                onClick = { onTone(AiReportTone.ANALYST) },
                label = { Text(stringResource(R.string.ai_tone_analyst)) },
            )
            FilterChip(
                selected = selected == AiReportTone.COMPANION,
                onClick = { onTone(AiReportTone.COMPANION) },
                label = { Text(stringResource(R.string.ai_tone_companion)) },
            )
        }
        Text(
            stringResource(R.string.ai_tone_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

/**
 * 输出 token 上限（高级设置）：纯数字输入，空白/非法输入回默认值；
 * 上限裁到 65536（防误填过大值触发服务商 4xx）。
 */
@Composable
private fun MaxTokensField(
    value: Int,
    onValueChange: (String) -> Unit,
) {
    LabeledSection(stringResource(R.string.ai_max_tokens)) {
        OutlinedTextField(
            value = value.toString(),
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.ai_max_tokens_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

/**
 * API Key 输入区：已保存 key 时显示「已保存 · 尾号 xxxx」掩码 + 清除按钮；
 * 输入框走密码变换 + 可见性切换（明文仅停留本页临时状态）。
 */
@Composable
private fun ApiKeyField(
    hasKey: Boolean,
    keyTail: String?,
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    var visible by rememberSaveable { mutableStateOf(false) }

    LabeledSection(stringResource(R.string.ai_api_key)) {
        if (hasKey && keyTail != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.ai_api_key_configured_mask, keyTail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.ai_api_key_clear), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(stringResource(R.string.ai_api_key_hint)) },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(
                            if (visible) R.string.ai_api_key_hide else R.string.ai_api_key_show,
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 持仓明细开关：关闭后 payload 仅含分类汇总，进一步减少外发数据。 */
@Composable
private fun DetailToggleRow(
    includeDetails: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.ai_include_details), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.ai_include_details_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = includeDetails, onCheckedChange = onToggle)
    }
}

/** 保存 / 测试连接按钮 + 状态文本（成功提示与错误文案都落在页内）。 */
@Composable
private fun ActionSection(
    saving: Boolean,
    testing: Boolean,
    status: AiStatus?,
    statusArg: String,
    statusDetail: String?,
    canSubmit: Boolean,
    onSave: () -> Unit,
    onTest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onSave,
                enabled = canSubmit && !saving,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.ai_save_config))
            }
            OutlinedButton(
                onClick = onTest,
                enabled = canSubmit && !testing,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(
                        if (testing) R.string.ai_test_connection_testing else R.string.ai_test_connection,
                    ),
                )
            }
        }
        status?.let {
            Text(
                text = it.resolveText(statusArg, statusDetail),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (it.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AiStatus.resolveText(arg: String, detail: String?): String = when (this) {
    AiStatus.CONFIG_SAVED -> stringResource(R.string.ai_config_saved)
    AiStatus.KEY_CLEARED -> stringResource(R.string.ai_key_cleared)
    AiStatus.NOT_CONFIGURED -> stringResource(R.string.ai_err_not_configured)
    AiStatus.NO_KEY -> stringResource(R.string.ai_err_no_key)
    AiStatus.TEST_OK -> stringResource(R.string.ai_test_ok, arg)
    AiStatus.NETWORK -> stringResource(R.string.ai_err_network)
    AiStatus.TIMEOUT -> stringResource(R.string.ai_err_timeout)
    AiStatus.UNAUTHORIZED -> stringResource(R.string.ai_err_unauthorized)
    AiStatus.RATE_LIMITED -> stringResource(R.string.ai_err_rate_limited)
    AiStatus.SERVER_ERROR -> stringResource(R.string.ai_err_server)
    AiStatus.EMPTY_RESPONSE -> stringResource(R.string.ai_err_empty_response)
    // 有服务商原始说明时直接展示（如「输入的服务 ID 不存在…」），否则退回通用文案。
    AiStatus.BAD_REQUEST -> stringResource(R.string.ai_err_bad_request_generic)
    AiStatus.BAD_REQUEST_DETAIL -> detail?.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.ai_err_bad_request, it) }
        ?: stringResource(R.string.ai_err_bad_request_generic)
}

private val AiStatus.isError: Boolean
    get() = this != AiStatus.CONFIG_SAVED &&
        this != AiStatus.KEY_CLEARED &&
        this != AiStatus.TEST_OK
