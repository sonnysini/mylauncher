package app.mylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import kotlinx.coroutines.flow.MutableSharedFlow
import app.mylauncher.ui.HomeScreen
import app.mylauncher.ui.LauncherTheme

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    /** Fires when Home is pressed while the launcher is already in front. */
    private val homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = viewModel.reloadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerReceiver(
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            },
        )
        setContent {
            LauncherTheme {
                HomeScreen(
                    viewModel = viewModel,
                    homePressed = homePressed,
                    onLaunch = { intent -> runCatching { startActivity(intent) } },
                    onOpenSettings = ::openSystemSettings,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePressed.tryEmit(Unit)
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    private fun openSystemSettings() {
        val fire = packageManager.getLeanbackLaunchIntentForPackage(FIRE_SETTINGS)
            ?: packageManager.getLaunchIntentForPackage(FIRE_SETTINGS)
        runCatching { startActivity(fire ?: Intent(Settings.ACTION_SETTINGS)) }
    }

    private companion object {
        const val FIRE_SETTINGS = "com.amazon.tv.settings.v2"
    }
}
