package com.example.royalnote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.royalnote.R

internal object AppRoutes {
    const val HOME = "home"
    const val ANALYSIS = "analysis"
    const val SETTINGS = "settings"
    const val IMPORT = "import"
}

@Composable
fun RoyalNoteNavigation(
    homeContent: @Composable (onImport: () -> Unit) -> Unit,
    analysisContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit,
    importContent: @Composable (onBack: () -> Unit) -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val topLevelRoutes = setOf(AppRoutes.HOME, AppRoutes.ANALYSIS, AppRoutes.SETTINGS)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (route in topLevelRoutes) {
                RoyalNoteBottomBar(route.orEmpty()) { target ->
                    navController.navigate(target) {
                        popUpTo(AppRoutes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppRoutes.HOME,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable(AppRoutes.HOME) {
                homeContent { navController.navigate(AppRoutes.IMPORT) }
            }
            composable(AppRoutes.ANALYSIS) { analysisContent() }
            composable(AppRoutes.SETTINGS) { settingsContent() }
            composable(AppRoutes.IMPORT) {
                importContent { navController.popBackStack() }
            }
        }
    }
}

@Composable
private fun RoyalNoteBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
) {
    val items = listOf(
        Triple(AppRoutes.HOME, "主页", R.drawable.ic_home_24),
        Triple(AppRoutes.ANALYSIS, "省察", R.drawable.ic_analysis_24),
        Triple(AppRoutes.SETTINGS, "设置", R.drawable.ic_settings_24),
    )
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = 56.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (route, label, icon) ->
                val selected = currentRoute == route
                val contentColor = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .selectable(
                            selected = selected,
                            onClick = { onNavigate(route) },
                            role = Role.Tab,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = contentColor,
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(
                                width = 24.dp,
                                height = 2.dp,
                            )
                            .background(
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                },
                                shape = CircleShape,
                            )
                    )
                }
            }
        }
    }
}
