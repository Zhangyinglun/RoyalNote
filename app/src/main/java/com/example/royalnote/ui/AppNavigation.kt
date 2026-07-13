package com.example.royalnote.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
            modifier = Modifier.padding(padding),
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
        Triple(AppRoutes.ANALYSIS, "分析", R.drawable.ic_analysis_24),
        Triple(AppRoutes.SETTINGS, "设置", R.drawable.ic_settings_24),
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        items.forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = { onNavigate(route) },
                icon = {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                    )
                },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                ),
            )
        }
    }
}
