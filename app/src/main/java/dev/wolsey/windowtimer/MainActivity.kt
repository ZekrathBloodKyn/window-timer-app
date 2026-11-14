package dev.wolsey.windowtimer

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import dev.wolsey.windowtimer.data.LegacyImport
import dev.wolsey.windowtimer.data.store.PersonStore
import dev.wolsey.windowtimer.data.store.TypeStore
import dev.wolsey.windowtimer.ui.history.HistoryScreen
import dev.wolsey.windowtimer.ui.jobs.JobsScreen
import dev.wolsey.windowtimer.ui.route.RouteScreen
import dev.wolsey.windowtimer.ui.settings.SettingsScreen
import dev.wolsey.windowtimer.ui.timer.CountRowsSaver
import dev.wolsey.windowtimer.ui.timer.TimerScreen
import kotlinx.coroutines.launch

enum class Screen { TIMER, SETTINGS, HISTORY, ROUTE, JOBS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            LegacyImport.runIfNeeded(applicationContext)
            TypeStore.ensureStandard(applicationContext)
            PersonStore.ensureOwner(applicationContext)
        }
        setContent {
            val dark = isSystemInDarkTheme()
            val colors = when {
                Build.VERSION.SDK_INT >= 31 ->
                    if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var screen by rememberSaveable { mutableStateOf(Screen.TIMER) }
                    var jobName by rememberSaveable { mutableStateOf("") }
                    var address by rememberSaveable { mutableStateOf("") }
                    var rows by rememberSaveable(stateSaver = CountRowsSaver) {
                        mutableStateOf(emptyList())
                    }
                    var crewIds by rememberSaveable(
                        stateSaver = listSaver(save = { it }, restore = { it })
                    ) { mutableStateOf(emptyList<Long>()) }

                    when (screen) {
                        Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.TIMER })
                        Screen.HISTORY -> HistoryScreen(onBack = { screen = Screen.TIMER })
                        Screen.ROUTE -> RouteScreen(onBack = { screen = Screen.TIMER })
                        Screen.JOBS -> JobsScreen(onBack = { screen = Screen.TIMER })
                        Screen.TIMER -> TimerScreen(
                            jobName = jobName,
                            onJobNameChange = { jobName = it },
                            onOpenSettings = { screen = Screen.SETTINGS },
                            onOpenHistory = { screen = Screen.HISTORY },
                            onOpenRoute = { screen = Screen.ROUTE },
                            onOpenJobs = { screen = Screen.JOBS },
                            address = address,
                            onAddressChange = { address = it },
                            rows = rows,
                            onRowsChange = { rows = it },
                            crewIds = crewIds,
                            onCrewChange = { crewIds = it },
                        )
                    }
                }
            }
        }
    }
}

