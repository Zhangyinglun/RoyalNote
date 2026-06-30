package com.example.royalnote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    uiState: ImportUiState,
    onTextChange: (String) -> Unit,
    onImportClick: () -> Unit,
    onBack: () -> Unit,
    onMessageShown: () -> Unit,
    onSuccessConfirmed: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    val successDialogMessage = uiState.successDialogMessage

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入旧录", fontFamily = FontFamily.Serif) },
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.semantics { contentDescription = "返回" },
                        onClick = onBack,
                    ) {
                        Text(
                            text = "‹",
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = FontFamily.Serif,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.text,
                onValueChange = onTextChange,
                label = { Text("粘贴旧日记录") },
                minLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Button(
                onClick = onImportClick,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("导入", fontFamily = FontFamily.Serif)
                }
            }
        }
    }

    if (successDialogMessage != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("导入完毕", fontFamily = FontFamily.Serif) },
            text = { Text(successDialogMessage, fontFamily = FontFamily.Serif) },
            confirmButton = {
                TextButton(onClick = onSuccessConfirmed) {
                    Text("回到首页", fontFamily = FontFamily.Serif)
                }
            },
        )
    }
}
