package com.example.royalnote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.royalnote.R
import com.example.royalnote.settings.AnalysisModel
import com.example.royalnote.settings.AppSettings
import com.example.royalnote.settings.AppThemeMode
import com.example.royalnote.settings.ReasoningEffort
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val SettingsControlShape = RoundedCornerShape(6.dp)
private val UpdatedTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val UpdatedDateFormatter = DateTimeFormatter.ofPattern("MM-dd")

internal val SettingsSupportingTextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.5.sp,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    keyVisible: Boolean,
    onApiKeyChange: (String) -> Unit,
    onToggleKeyVisibility: () -> Unit,
    onThemeModeSelected: (AppThemeMode) -> Unit = {},
    onModelSelected: (AnalysisModel) -> Unit,
    onEffortSelected: (AnalysisModel, ReasoningEffort) -> Unit,
    onRefreshUsage: () -> Unit,
    onVisible: () -> Unit,
) {
    var showApiKeyEditor by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var showModelEditor by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(Unit) { onVisible() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        ) {
            item {
                AppearanceGroup(
                    selectedMode = uiState.settings.themeMode,
                    onThemeModeSelected = onThemeModeSelected,
                )
            }
            item {
                OpenRouterGroup(
                    settings = uiState.settings,
                    usage = uiState.usage,
                    onEditApiKey = { showApiKeyEditor = true },
                    onRefreshUsage = onRefreshUsage,
                )
            }
            item {
                ModelGroup(
                    settings = uiState.settings,
                    onEditModel = { showModelEditor = true },
                )
            }
            item {
                Text(
                    "导入旧录与七日省察会把必要内容发送至 OpenRouter",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    textAlign = TextAlign.Center,
                    style = SettingsSupportingTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showApiKeyEditor) {
        ApiKeyDialog(
            apiKey = uiState.settings.apiKey,
            keyVisible = keyVisible,
            onApiKeyChange = onApiKeyChange,
            onToggleKeyVisibility = onToggleKeyVisibility,
            onDismiss = { showApiKeyEditor = false },
        )
    }
    if (showModelEditor) {
        ModelSheet(
            settings = uiState.settings,
            onModelSelected = onModelSelected,
            onEffortSelected = onEffortSelected,
            onDismiss = { showModelEditor = false },
        )
    }
}

@Composable
private fun AppearanceGroup(
    selectedMode: AppThemeMode,
    onThemeModeSelected: (AppThemeMode) -> Unit,
) {
    SettingsGroup(title = "外观") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppThemeMode.entries.forEach { mode ->
                val selected = selectedMode == mode
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 58.dp)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = SettingsControlShape,
                        )
                        .selectable(
                            selected = selected,
                            onClick = { onThemeModeSelected(mode) },
                            role = Role.RadioButton,
                        ),
                    shape = SettingsControlShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            mode.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                        if (mode == AppThemeMode.AUTO) {
                            Text(
                                "跟随系统",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenRouterGroup(
    settings: AppSettings,
    usage: UsageUiState,
    onEditApiKey: () -> Unit,
    onRefreshUsage: () -> Unit,
) {
    val isLoading = usage is UsageUiState.Loading
    val amount = when (usage) {
        is UsageUiState.Loading -> usage.previousAmount
        is UsageUiState.Success -> usage.amount
        is UsageUiState.Error -> usage.previousAmount
        UsageUiState.MissingKey,
        UsageUiState.Ready,
        -> null
    }
    val usageStatus = when (usage) {
        UsageUiState.MissingKey -> "未配置"
        UsageUiState.Ready -> "待查询"
        is UsageUiState.Loading -> amount?.let(::formatUsd) ?: "查询中"
        is UsageUiState.Success -> formatUsd(usage.amount)
        is UsageUiState.Error -> amount?.let(::formatUsd) ?: "失败"
    }
    val usageHelper = when (usage) {
        UsageUiState.MissingKey -> "填写 API Key 后可查询"
        UsageUiState.Ready -> "点击此行刷新用量"
        is UsageUiState.Loading -> "正在查询本月消费"
        is UsageUiState.Success -> "最近一次更新：${formatUpdatedAt(usage.updatedAtMillis)}"
        is UsageUiState.Error -> usage.message
    }
    val canRefresh = settings.apiKey.isNotBlank() && !isLoading

    SettingsGroup(title = "OPENROUTER") {
        SettingsRow(
            iconRes = R.drawable.ic_lock_24,
            title = "API 密钥",
            subtitle = "用于导入与七日省察",
            status = if (settings.apiKey.isBlank()) "未设置" else "已保存",
            onClick = onEditApiKey,
        )
        SettingsRow(
            iconRes = R.drawable.ic_analysis_24,
            title = "本月消费",
            subtitle = usageHelper,
            status = usageStatus,
            statusColor = if (usage is UsageUiState.Error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.secondary
            },
            enabled = canRefresh,
            onClick = onRefreshUsage,
            contentDescription = "刷新本月消费",
            showProgress = isLoading,
            modifier = Modifier.testTag("usageCard"),
        )
    }
}

@Composable
private fun ModelGroup(
    settings: AppSettings,
    onEditModel: () -> Unit,
) {
    SettingsGroup(title = "分析模型") {
        SettingsRow(
            iconRes = R.drawable.ic_model_24,
            title = settings.selectedModel.displayName,
            subtitle = "推理强度：${settings.selectedEffort.wireValue}",
            status = "当前",
            onClick = onEditModel,
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.padding(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            title,
            modifier = Modifier.padding(bottom = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.2.sp,
        )
        content()
    }
}

@Composable
private fun SettingsRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    status: String,
    modifier: Modifier = Modifier,
    statusColor: Color = MaterialTheme.colorScheme.secondary,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    showProgress: Boolean = false,
) {
    val interactiveModifier = if (onClick != null) {
        Modifier.clickable(enabled = enabled, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
            .then(interactiveModifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = SettingsSupportingTextStyle,
                color = if (subtitle.startsWith("用量查询失败")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(18.dp)
                    .testTag("usageLoading"),
                strokeWidth = 2.dp,
            )
        }
        Text(
            status,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled || onClick == null) statusColor else statusColor.copy(alpha = 0.65f),
            fontWeight = FontWeight.Bold,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ApiKeyDialog(
    apiKey: String,
    keyVisible: Boolean,
    onApiKeyChange: (String) -> Unit,
    onToggleKeyVisibility: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API 密钥") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key", style = SettingsSupportingTextStyle) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (keyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = onToggleKeyVisibility) {
                            Icon(
                                painter = painterResource(
                                    if (keyVisible) R.drawable.ic_visibility_off_24
                                    else R.drawable.ic_visibility_24
                                ),
                                contentDescription = if (keyVisible) {
                                    "隐藏 API Key"
                                } else {
                                    "显示 API Key"
                                },
                            )
                        }
                    },
                )
                Text(
                    "输入会自动保存在本机",
                    style = SettingsSupportingTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSheet(
    settings: AppSettings,
    onModelSelected: (AnalysisModel) -> Unit,
    onEffortSelected: (AnalysisModel, ReasoningEffort) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "分析模型",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                )
                Button(
                    onClick = onDismiss,
                    shape = SettingsControlShape,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("完成")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnalysisModel.entries.forEach { model ->
                    val selected = model == settings.selectedModel
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 88.dp)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                shape = RoundedCornerShape(8.dp),
                            )
                            .selectable(
                                selected = selected,
                                onClick = { onModelSelected(model) },
                                role = Role.RadioButton,
                            ),
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Transparent
                                        },
                                        shape = CircleShape,
                                    )
                            )
                            Text(
                                model.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 17.sp,
                            )
                            Text(
                                "${model.supportedEfforts.size} 档",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            EffortSliderPanel(
                model = settings.selectedModel,
                selectedEffort = settings.selectedEffort,
                onEffortSelected = { effort ->
                    onEffortSelected(settings.selectedModel, effort)
                },
            )

            Text(
                "· 每个模型分别记住上次选择；切换模型时恢复对应档位。",
                style = SettingsSupportingTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EffortSliderPanel(
    model: AnalysisModel,
    selectedEffort: ReasoningEffort,
    onEffortSelected: (ReasoningEffort) -> Unit,
) {
    val efforts = model.supportedEfforts.asReversed()
    val selectedIndex = efforts.indexOf(selectedEffort).coerceAtLeast(0)
    val maxIndex = efforts.lastIndex
    val accentColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
            )
            .drawBehind {
                drawRect(
                    color = accentColor,
                    size = Size(width = 3.dp.toPx(), height = size.height),
                )
            }
            .padding(start = 18.dp, top = 16.dp, end = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "推理强度",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                selectedEffort.wireValue,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            effortDescription(selectedEffort),
            style = SettingsSupportingTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = selectedIndex.toFloat(),
            onValueChange = { value ->
                val effort = efforts[value.roundToInt().coerceIn(0, maxIndex)]
                if (effort != selectedEffort) onEffortSelected(effort)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("effortSlider"),
            valueRange = 0f..maxIndex.toFloat(),
            steps = (efforts.size - 2).coerceAtLeast(0),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            efforts.forEach { effort ->
                Text(
                    effort.displayLabel(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (effort == selectedEffort) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (effort == selectedEffort) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun ReasoningEffort.displayLabel(): String = when (this) {
    ReasoningEffort.NONE -> "关闭"
    else -> wireValue
}

private fun effortDescription(effort: ReasoningEffort): String = when (effort) {
    ReasoningEffort.NONE -> "关闭额外推理，适合只需要快速响应的场景。"
    ReasoningEffort.LOW -> "轻量分析，响应更快、消耗更低。"
    ReasoningEffort.MEDIUM -> "在速度、消耗与分析深度之间取得平衡。"
    ReasoningEffort.HIGH -> "更充分地分析，速度与消耗也会相应增加。"
    ReasoningEffort.XHIGH -> "用于更复杂的材料，给予模型更多推理空间。"
    ReasoningEffort.MAX -> "使用模型允许的最高推理强度。"
}

private fun formatUsd(amount: Double): String =
    NumberFormat.getCurrencyInstance(Locale.US).format(amount)

private fun formatUpdatedAt(millis: Long): String {
    val value = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return if (value.toLocalDate() == LocalDate.now(value.zone)) {
        "今日 ${value.format(UpdatedTimeFormatter)}"
    } else {
        value.format(UpdatedDateFormatter)
    }
}
