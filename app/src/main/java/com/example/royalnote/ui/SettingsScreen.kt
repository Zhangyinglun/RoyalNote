package com.example.royalnote.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.royalnote.R
import com.example.royalnote.settings.AnalysisModel
import com.example.royalnote.settings.AppSettings
import com.example.royalnote.settings.ReasoningEffort
import java.text.NumberFormat
import java.util.Locale

private val SettingsCardShape = RoundedCornerShape(8.dp)
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
    onModelSelected: (AnalysisModel) -> Unit,
    onEffortSelected: (AnalysisModel, ReasoningEffort) -> Unit,
    onRefreshUsage: () -> Unit,
    onVisible: () -> Unit,
) {
    LaunchedEffect(Unit) { onVisible() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 18.dp),
        ) {
            item {
                ApiKeyCard(
                    apiKey = uiState.settings.apiKey,
                    keyVisible = keyVisible,
                    onApiKeyChange = onApiKeyChange,
                    onToggleKeyVisibility = onToggleKeyVisibility,
                )
            }
            item { UsageCard(uiState.usage, onRefreshUsage) }
            item {
                ModelCard(
                    settings = uiState.settings,
                    onModelSelected = onModelSelected,
                    onEffortSelected = onEffortSelected,
                )
            }
            item {
                Text(
                    "导入旧录将使用此处配置",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = SettingsSupportingTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ApiKeyCard(
    apiKey: String,
    keyVisible: Boolean,
    onApiKeyChange: (String) -> Unit,
    onToggleKeyVisibility: () -> Unit,
) {
    InkSettingsCard {
        Text("OpenRouter API Key", style = MaterialTheme.typography.titleMedium)
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
                        contentDescription = if (keyVisible) "隐藏 API Key" else "显示 API Key",
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
}

@Composable
private fun UsageCard(
    usage: UsageUiState,
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
    val helper = when (usage) {
        UsageUiState.MissingKey -> "填写 API Key 后可查询"
        UsageUiState.Ready -> "点击刷新查询新 Key 的用量"
        is UsageUiState.Loading -> "当前 API Key · 按 UTC 月统计"
        is UsageUiState.Success -> "当前 API Key · 按 UTC 月统计"
        is UsageUiState.Error -> "当前 API Key · 按 UTC 月统计"
    }

    InkSettingsCard(
        modifier = Modifier
            .heightIn(min = 148.dp)
            .testTag("usageCard"),
    ) {
        Text("本月消费", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                amount?.let(::formatUsd) ?: "—",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).testTag("usageLoading"),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                IconButton(
                    onClick = onRefreshUsage,
                    enabled = !isLoading,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh_24),
                        contentDescription = "刷新本月消费",
                    )
                }
            }
        }
        Text(
            helper,
            style = SettingsSupportingTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                "\u00A0\n\u00A0",
                modifier = Modifier.clearAndSetSemantics { },
                style = SettingsSupportingTextStyle,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
            )
            if (usage is UsageUiState.Error) {
                Text(
                    usage.message,
                    style = SettingsSupportingTextStyle,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelCard(
    settings: AppSettings,
    onModelSelected: (AnalysisModel) -> Unit,
    onEffortSelected: (AnalysisModel, ReasoningEffort) -> Unit,
) {
    InkSettingsCard {
        Text("分析模型", style = MaterialTheme.typography.titleMedium)
        AnalysisModel.entries.forEach { model ->
            val selected = model == settings.selectedModel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = selected,
                        onClick = { onModelSelected(model) },
                        role = Role.RadioButton,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(selected = selected, onClick = null)
                Text(model.displayName, style = MaterialTheme.typography.bodyLarge)
            }
            if (selected) {
                Text(
                    "推理强度",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    model.supportedEfforts.forEach { effort ->
                        FilterChip(
                            selected = settings.effortFor(model) == effort,
                            onClick = { onEffortSelected(model, effort) },
                            label = { Text(effort.wireValue) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InkSettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, SettingsCardShape)
            .drawWithContent {
                drawContent()
                drawRect(
                    color = primary.copy(alpha = 0.7f),
                    topLeft = Offset.Zero,
                    size = Size(width = 3.dp.toPx(), height = size.height),
                )
            },
        shape = SettingsCardShape,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

private fun formatUsd(amount: Double): String =
    NumberFormat.getCurrencyInstance(Locale.US).format(amount)
