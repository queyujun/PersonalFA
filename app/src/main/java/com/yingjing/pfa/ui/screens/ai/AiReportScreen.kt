package com.yingjing.pfa.ui.screens.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yingjing.pfa.R
import com.yingjing.pfa.domain.ai.AiFailureKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * AI 资产报告页：Idle 说明卡 → Loading（可取消）→ Done（Markdown 展示 + 导出）/ Error（重试）。
 *
 * 独立于 settings 包的 internal 组件，顶栏手写（仿 HoldingDetailScreen 的 56dp Row）。
 * 报告内容仅在内存展示，导出走 SAF（CreateDocument text/markdown）。
 */
@Composable
fun AiReportScreen(
    onBack: () -> Unit,
    onGoSettings: () -> Unit,
    viewModel: AiReportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val consented by viewModel.consented.collectAsState()
    val cancelHint by viewModel.cancelHint.collectAsState()
    val exportResult by viewModel.exportResult.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val cancelledText = stringResource(R.string.ai_generation_cancelled)
    val exportedText = stringResource(R.string.ai_report_exported)
    val exportFailedText = stringResource(R.string.ai_report_export_failed)

    LaunchedEffect(cancelHint) {
        if (cancelHint == AiCancelHint.JUST_CANCELLED) {
            snackbarHostState.showSnackbar(cancelledText)
            viewModel.clearCancelHint()
        }
    }
    LaunchedEffect(exportResult) {
        when (exportResult) {
            AiExportResult.Success -> snackbarHostState.showSnackbar(exportedText)
            is AiExportResult.Failure -> snackbarHostState.showSnackbar(
                exportFailedText.format((exportResult as AiExportResult.Failure).message ?: ""),
            )
            AiExportResult.Idle -> Unit
        }
        if (exportResult != AiExportResult.Idle) viewModel.clearExportResult()
    }

    var showPrivacyDialog by remember { mutableStateOf(false) }
    var pendingGenerate by remember { mutableStateOf(false) }

    // 导出：SAF 建文档（text/markdown），回调里交给 VM 写入
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri -> if (uri != null) viewModel.exportTo(uri) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏：手写 56dp Row（settings 包 internal 组件不可跨包使用）
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
                stringResource(R.string.ai_report_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }

        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                when (val s = state) {
                    AiUiState.Idle -> IdleSection(
                        onGenerate = {
                            if (consented) viewModel.generate() else {
                                pendingGenerate = true
                                showPrivacyDialog = true
                            }
                        },
                    )
                    AiUiState.Loading -> LoadingSection(onCancel = viewModel::cancel)
                    is AiUiState.Done -> DoneSection(
                        done = s,
                        onRegenerate = viewModel::generate,
                        onExport = {
                            exportLauncher.launch("ai_report_${fileTimestamp(s.generatedAtMs)}.md")
                        },
                    )
                    is AiUiState.Error -> ErrorSection(
                        error = s,
                        onRetry = viewModel::generate,
                        onGoSettings = onGoSettings,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.ai_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = {
                showPrivacyDialog = false
                pendingGenerate = false
            },
            title = { Text(stringResource(R.string.ai_privacy_dialog_title)) },
            text = { Text(stringResource(R.string.ai_privacy_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showPrivacyDialog = false
                    viewModel.consent()
                    if (pendingGenerate) {
                        pendingGenerate = false
                        viewModel.generate()
                    }
                }) { Text(stringResource(R.string.ai_privacy_dialog_agree)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPrivacyDialog = false
                    pendingGenerate = false
                }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun IdleSection(onGenerate: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.ai_report_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_report_generate))
            }
        }
    }
}

@Composable
private fun LoadingSection(onCancel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.ai_generating), style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.ai_cancel_generation))
            }
        }
    }
}

@Composable
private fun DoneSection(
    done: AiUiState.Done,
    onRegenerate: () -> Unit,
    onExport: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        done.model?.let { model ->
            Text(
                stringResource(R.string.ai_generated_at, formatGeneratedAt(done.generatedAtMs), model),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            AiMarkdownText(
                markdown = done.markdown,
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRegenerate, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.ai_report_regenerate))
            }
            Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.ai_report_export))
            }
        }
    }
}

@Composable
private fun ErrorSection(
    error: AiUiState.Error,
    onRetry: () -> Unit,
    onGoSettings: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(errorResOf(error.kind)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (error.canRetry) {
                    Button(onClick = onRetry, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.ai_retry))
                    }
                } else {
                    Button(onClick = onGoSettings, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.ai_go_settings))
                    }
                }
            }
            // UNAUTHORIZED 额外提供去设置入口（key 失效常见于额度用尽/重置）
            if (error.kind == AiFailureKind.UNAUTHORIZED) {
                TextButton(onClick = onGoSettings) {
                    Text(stringResource(R.string.ai_go_settings))
                }
            }
        }
    }
}

/** 导出文件名时间戳：yyyy-MM-dd_HHmm（SAF 文件名不含冒号）。 */
internal fun fileTimestamp(epochMs: Long): String =
    DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMs))
