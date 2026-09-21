package com.gothwad.computer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gothwad.computer.data.AppEntry
import com.gothwad.computer.data.AppRepository
import com.gothwad.computer.data.BluetoothDeviceStatus
import com.gothwad.computer.data.ConfigStore
import com.gothwad.computer.data.LauncherConfig
import com.gothwad.computer.data.MODE_PC
import com.gothwad.computer.data.NetStatus
import com.gothwad.computer.databinding.ActivityMainBinding
import com.gothwad.computer.ui.dialogs.NotificationBottomSheetFragment
import com.gothwad.computer.ui.dialogs.PinEntryDialogFragment
import com.gothwad.computer.ui.dialogs.QuickDashboardDialogFragment
import com.gothwad.computer.ui.dialogs.SearchDialogFragment
import com.gothwad.computer.ui.pc.settings.PcSettingsConstants
import com.gothwad.computer.ui.pc.settings.PcSettingsDialogFragment
import com.gothwad.computer.ui.view.DeviceLockViewController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Bumped whenever a package is installed/removed so the app grid rescans. */
    var rescanTick: Int = 0
        private set

    private var currentConfig: LauncherConfig = LauncherConfig(launcherMode = MODE_PC)
    private var currentNetStatus: NetStatus = NetStatus()
    private var currentBtStatus: BluetoothDeviceStatus = BluetoothDeviceStatus()
    private var allApps: List<AppEntry> = emptyList()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart
            val launchable = pkg != null && packageManager.getLaunchIntentForPackage(pkg) != null
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED || launchable) {
                rescanTick++
                refreshAppsList()
            }
        }
    }

    private fun refreshAppsList() {
        lifecycleScope.launch(Dispatchers.IO) {
            allApps = AppRepository.scan(this@MainActivity)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = newBase.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
            .getString(LOCALE_KEY, "").orEmpty()
        val contextWithLocale = if (lang.isEmpty()) newBase else applyLocale(newBase, lang)
        val contextWithDpi = com.gothwad.computer.data.DpiHelper.wrapContext(contextWithLocale)
        super.attachBaseContext(contextWithDpi)
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (overrideConfiguration != null && com.gothwad.computer.data.DpiHelper.isCustomDpiEnabled(this)) {
            overrideConfiguration.densityDpi = com.gothwad.computer.data.DpiHelper.getCustomDpiValue(this)
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (com.gothwad.computer.data.DpiHelper.isCustomDpiEnabled(this)) {
            com.gothwad.computer.data.DpiHelper.applyToResources(
                resources,
                com.gothwad.computer.data.DpiHelper.getCustomDpiValue(this)
            )
        }

        // Handle Back when device is locked so it cannot be bypassed
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
                    // Stay on device lock screen
                    return
                }
                // Allow standard back navigation
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })

        ContextCompat.registerReceiver(
            this,
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Device Lock on cold start
        checkDeviceLockOnColdStart()

        observeConfig()
        refreshAppsList()
    }

    private var deviceLockController: DeviceLockViewController? = null

    private fun checkDeviceLockOnColdStart() {
        if (GothwadApplication.hasUnlockedDeviceThisProcess) {
            binding.deviceLockContainer.visibility = View.GONE
            return
        }

        binding.deviceLockContainer.visibility = View.VISIBLE
        binding.deviceLockContainer.bringToFront()

        lifecycleScope.launch {
            val store = ConfigStore(this@MainActivity)
            val config = store.flow.first()
            currentConfig = config
            if (config.deviceLock.enabled && config.deviceLock.value.isNotEmpty()) {
                deviceLockController = DeviceLockViewController(
                    container = binding.deviceLockContainer,
                    credential = config.deviceLock,
                    onUnlocked = {
                        GothwadApplication.hasUnlockedDeviceThisProcess = true
                        deviceLockController = null
                    }
                )
                deviceLockController?.show()
            } else {
                GothwadApplication.hasUnlockedDeviceThisProcess = true
                binding.deviceLockContainer.visibility = View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentConfig.pcOverlayTaskbarEnabled &&
            com.gothwad.computer.service.FloatingTaskbarService.canDrawOverlays(this)) {
            com.gothwad.computer.service.FloatingTaskbarService.startIfEnabled(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.action) {
            "ACTION_SEARCH" -> {
                SearchDialogFragment.newInstance(
                    apps = allApps,
                    config = currentConfig,
                    onLaunch = { app -> handleAppLaunch(app) }
                ).show(supportFragmentManager, SearchDialogFragment.TAG)
            }
            "ACTION_NOTIFICATIONS" -> {
                NotificationBottomSheetFragment.newInstance()
                    .show(supportFragmentManager, NotificationBottomSheetFragment.TAG)
            }
            "ACTION_SETTINGS" -> {
                openSettingsDialog()
            }
            "ACTION_QUICK_SETTINGS" -> {
                QuickDashboardDialogFragment.newInstance(
                    net = currentNetStatus,
                    bt = currentBtStatus,
                    onOpenSettings = { openSettingsDialog() }
                ).show(supportFragmentManager, QuickDashboardDialogFragment.TAG)
            }
            "OPEN_APP" -> {
                val pkg = intent.getStringExtra("EXTRA_PKG")
                if (!pkg.isNullOrEmpty()) {
                    Actions.launchApp(this, pkg)
                }
            }
        }
    }

    private fun openSettingsDialog() {
        if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
            return
        }
        PcSettingsDialogFragment.newInstance(
            initialTab = PcSettingsConstants.TAB_SYSTEM,
            onWallpaperChanged = { /* handled by config observer in PcLauncherFragment */ }
        ).show(supportFragmentManager, PcSettingsDialogFragment.TAG)
    }

    private fun handleAppLaunch(app: AppEntry, skipLock: Boolean = false) {
        if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
            return
        }
        if (!skipLock && currentConfig.appLock.enabled && currentConfig.appLock.value.isNotEmpty() && app.pkg in currentConfig.lockedApps) {
            PinEntryDialogFragment.newInstance(
                title = "App Locked",
                subtitle = "Enter PIN/Password to launch ${app.label}",
                credential = currentConfig.appLock,
                isCancelable = true,
                onSuccess = {
                    handleAppLaunch(app, skipLock = true)
                }
            ).show(supportFragmentManager, PinEntryDialogFragment.TAG)
        } else {
            Actions.launchApp(this, app.pkg)
        }
    }

    private fun observeConfig() {
        val configStore = ConfigStore(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                configStore.flow.collectLatest { config ->
                    currentConfig = config
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
            if (deviceLockController?.handleKeyEvent(event.keyCode, event) == true) {
                return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(packageReceiver) }
        super.onDestroy()
    }

    companion object {
        private const val LOCALE_PREFS = "locale"
        private const val LOCALE_KEY = "lang"

        fun persistLocale(context: Context, lang: String) {
            context.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
                .edit().putString(LOCALE_KEY, lang).apply()
        }

        fun currentLocalePref(context: Context): String =
            context.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
                .getString(LOCALE_KEY, "").orEmpty()

        fun applyLocale(context: Context, lang: String): Context {
            val locale = if (lang.contains('-')) {
                val parts = lang.split('-')
                Locale(parts[0], parts.getOrElse(1) { "" })
            } else Locale(lang)
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        }
    }
}
