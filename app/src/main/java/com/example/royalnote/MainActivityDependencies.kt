package com.example.royalnote

import android.content.Context
import com.example.royalnote.network.OpenRouterUsageProvider
import com.example.royalnote.network.OpenRouterUsageService
import com.example.royalnote.settings.SettingsRepository
import com.example.royalnote.settings.SharedPreferencesSettingsStorage

internal data class MainActivitySettingsDependencies(
    val settingsRepository: SettingsRepository,
    val usageProvider: OpenRouterUsageProvider,
)

internal object MainActivityDependencyOverrides {
    private val lock = Any()
    @Volatile
    private var installed: MainActivitySettingsDependencies? = null

    fun resolve(context: Context): MainActivitySettingsDependencies = installed
        ?: MainActivitySettingsDependencies(
            settingsRepository = SettingsRepository(SharedPreferencesSettingsStorage(context)),
            usageProvider = OpenRouterUsageService(),
        )

    fun installForTest(dependencies: MainActivitySettingsDependencies): AutoCloseable {
        synchronized(lock) {
            check(installed == null) { "MainActivity dependency override is already installed" }
            installed = dependencies
        }
        return AutoCloseable {
            synchronized(lock) {
                if (installed === dependencies) installed = null
            }
        }
    }
}
