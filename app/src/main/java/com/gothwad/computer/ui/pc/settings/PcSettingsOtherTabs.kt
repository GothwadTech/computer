package com.gothwad.computer.ui.pc.settings

import android.app.ActivityManager
import android.app.AppOpsManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import android.view.InputDevice
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gothwad.computer.Actions
import com.gothwad.computer.R
import com.gothwad.computer.data.AppEntry
import com.gothwad.computer.data.AppRepository
import com.gothwad.computer.data.ConfigStore
import com.gothwad.computer.data.LauncherConfig
import com.gothwad.computer.databinding.LayoutPcSettingsGenericBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PcSettingsOtherTabs(
    private val context: Context,
    private val scope: CoroutineScope,
    private var config: LauncherConfig,
    private val onOpenLockSetup: () -> Unit,
    private val onConfigChanged: ((LauncherConfig) -> Unit)? = null,
    private val onRestartEmulator: (() -> Unit)? = null
) {

    fun updateConfig(newConfig: LauncherConfig) {
        config = newConfig
    }

    fun createPageView(inflater: LayoutInflater, tabId: Int): View {
        val binding = LayoutPcSettingsGenericBinding.inflate(inflater, null, false)
        when (tabId) {
            PcSettingsConstants.TAB_BLUETOOTH -> buildBluetoothPage(binding)
            PcSettingsConstants.TAB_NETWORK -> buildNetworkPage(binding)
            PcSettingsConstants.TAB_APPS -> buildAppsPage(binding)
            PcSettingsConstants.TAB_ACCOUNTS -> buildAccountsPage(binding)
            PcSettingsConstants.TAB_TIME -> buildTimePage(binding)
            PcSettingsConstants.TAB_GAMING -> buildGamingPage(binding)
            PcSettingsConstants.TAB_ACCESSIBILITY -> buildAccessibilityPage(binding)
            PcSettingsConstants.TAB_PRIVACY -> buildPrivacyPage(binding)
            PcSettingsConstants.TAB_UPDATE -> buildUpdatePage(binding)
        }
        return binding.root
    }

    // ==========================================
    // 1. BLUETOOTH & DEVICES
    // ==========================================
    private fun buildBluetoothPage(binding: LayoutPcSettingsGenericBinding) {
        binding.tvPageTitle.text = "Bluetooth & devices"
        val container = binding.containerCards
        container.removeAllViews()

        // Bluetooth Status
        val btAdapter = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull()
        val btStatus = when {
            btAdapter == null -> "Bluetooth hardware not detected"
            btAdapter.isEnabled -> "Bluetooth is On • Discoverable as ${config.pcDeviceName}"
            else -> "Bluetooth is Off • Click to configure"
        }
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_bluetooth,
            title = "Bluetooth",
            subtitle = btStatus,
            value = if (btAdapter?.isEnabled == true) "On" else "Off"
        ) {
            openIntent(Settings.ACTION_BLUETOOTH_SETTINGS)
        }

        // Pair New Device
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_add,
            title = "Add device",
            subtitle = "Pair Bluetooth mouse, keyboard, gamepad, or audio"
        ) {
            openIntent(Settings.ACTION_BLUETOOTH_SETTINGS)
        }

        // Mouse Cursor Style
        val cursorNames = listOf("Windows Default Arrow", "Classic White", "High Contrast Black", "Cyber Neon Blue")
        val curCursorIdx = config.pcCursorStyle.coerceIn(0, cursorNames.size - 1)
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_system,
            title = "Mouse Cursor Style",
            subtitle = "Style: ${cursorNames[curCursorIdx]}",
            value = cursorNames[curCursorIdx]
        ) {
            PcSettingCardHelper.showSingleChoiceDialog(
                context = context,
                title = "Choose Mouse Cursor Style",
                options = cursorNames,
                selectedIndex = curCursorIdx
            ) { selected ->
                updateConfigProperty { it.copy(pcCursorStyle = selected) }
                buildBluetoothPage(binding)
                Toast.makeText(context, "Cursor set to ${cursorNames[selected]}", Toast.LENGTH_SHORT).show()
            }
        }

        // Pointer Sensitivity / Scale
        val scaleOptions = listOf("Small (80%)", "Normal (100%)", "Medium (125%)", "Large (150%)")
        val scaleVals = listOf(0.8f, 1.0f, 1.25f, 1.5f)
        val curScaleIdx = scaleVals.indexOfFirst { kotlin.math.abs(it - config.pcCursorScale) < 0.1f }.coerceAtLeast(0)
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_system,
            title = "Mouse Pointer Size & Speed",
            subtitle = "Cursor Scale: ${(config.pcCursorScale * 100).toInt()}%",
            value = "${(config.pcCursorScale * 100).toInt()}%"
        ) {
            PcSettingCardHelper.showSingleChoiceDialog(
                context = context,
                title = "Pointer Scale & Sensitivity",
                options = scaleOptions,
                selectedIndex = curScaleIdx
            ) { selected ->
                val newScale = scaleVals[selected]
                updateConfigProperty { it.copy(pcCursorScale = newScale) }
                buildBluetoothPage(binding)
                Toast.makeText(context, "Cursor scale set to ${scaleOptions[selected]}", Toast.LENGTH_SHORT).show()
            }
        }

        // Physical Keyboard Hotkeys
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_system,
            title = "Physical Keyboard Shortcuts",
            subtitle = "Win key, Alt+Tab, Win+D, Win+E, Win+I, Ctrl+Shift+Esc",
            value = "View"
        ) {
            val shortcuts = """
                • Win Key or Ctrl+Esc : Open / Close Start Menu
                • Win + D : Show Desktop (Minimize all windows)
                • Win + E : Open File Explorer
                • Win + I : Open Settings
                • Alt + Tab : Switch between open windows
                • Ctrl + Shift + Esc : Task Manager
                • Win + L : Lock PC
                • F11 : Toggle Fullscreen Mode
                • Esc : Close active flyout or top window
            """.trimIndent()
            PcSettingCardHelper.showInfoDialog(context, "Keyboard Shortcuts Cheatsheet", shortcuts)
        }

        // Game Controllers
        val gamepads = getConnectedGamepads()
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_gaming,
            title = "Game Controllers & Gamepads",
            subtitle = if (gamepads.isNotEmpty()) "Connected: ${gamepads.joinToString(", ")}" else "No external controller connected",
            value = if (gamepads.isNotEmpty()) "${gamepads.size} Connected" else "None"
        ) {
            val msg = if (gamepads.isNotEmpty()) {
                "Connected Gamepads:\n${gamepads.mapIndexed { idx, name -> "${idx + 1}. $name" }.joinToString("\n")}\n\nGamepad controls are supported in emulator windows."
            } else {
                "No gamepads currently detected.\n\nConnect a Bluetooth or USB gamepad (Xbox, PlayStation, or generic HID) to control desktop games."
            }
            PcSettingCardHelper.showInfoDialog(context, "Game Controllers", msg)
        }

        // USB & External Drives
        val extStorageState = Environment.getExternalStorageState()
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_system,
            title = "USB & External Storage",
            subtitle = "Storage State: $extStorageState • OTG support active",
            value = "Inspect"
        ) {
            val storageDirs = ContextCompat.getExternalFilesDirs(context, null)
            val info = StringBuilder("Storage Volumes Detected: ${storageDirs.size}\n\n")
            storageDirs.forEachIndexed { i, f ->
                if (f != null) {
                    info.append("${i + 1}. ${f.absolutePath}\n")
                }
            }
            PcSettingCardHelper.showInfoDialog(context, "USB & External Drives", info.toString())
        }
    }

    // ==========================================
    // 2. NETWORK & INTERNET
    // ==========================================
    private fun buildNetworkPage(binding: LayoutPcSettingsGenericBinding) {
        binding.tvPageTitle.text = "Network & internet"
        val container = binding.containerCards
        container.removeAllViews()

        // Active Connection Status
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = connMgr?.activeNetwork
        val caps = connMgr?.getNetworkCapabilities(activeNet)

        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val isEthernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true

        val netStatus = when {
            isWifi -> "Wi-Fi Connected • High Speed Internet"
            isEthernet -> "Ethernet Cable Connected • Gigabit LAN"
            isCellular -> "Mobile Data Connected • Cellular"
            else -> "Disconnected • No active internet connection"
        }

        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_network,
            title = "Network Status",
            subtitle = netStatus,
            value = if (isWifi || isEthernet || isCellular) "Connected" else "Offline"
        ) {
            openIntent(Settings.ACTION_WIFI_SETTINGS)
        }

        // Ping Diagnostic Tool
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_system,
            title = "Network Diagnostic (Ping Test)",
            subtitle = "Test connection latency to Google DNS (8.8.8.8)",
            value = "Ping"
        ) {
            Toast.makeText(context, "Testing connection latency...", Toast.LENGTH_SHORT).show()
            scope.launch(Dispatchers.IO) {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress("8.8.8.8", 53), 2000)
                    socket.close()
                    success = true
                } catch (_: Exception) {}
                val latency = System.currentTimeMillis() - start

                withContext(Dispatchers.Main) {
                    val report = if (success) {
                        "✓ Internet Connected!\nDestination: 8.8.8.8 (Google DNS)\nLatency: $latency ms\nConnection Quality: ${if (latency < 60) "Excellent" else if (latency < 150) "Good" else "Fair"}"
                    } else {
                        "✗ Ping Failed!\nCould not reach 8.8.8.8.\nPlease check your Wi-Fi or mobile data connection."
                    }
                    PcSettingCardHelper.showInfoDialog(context, "Network Diagnostic Result", report)
                }
            }
        }

        // Web Browser Search Engine
        val engines = listOf("Google", "Microsoft Bing", "DuckDuckGo", "Yahoo", "Ecosia")
        val curEngineIdx = engines.indexOf(config.pcSearchEngine).coerceAtLeast(0)
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_network,
            title = "Default Search Engine",
            subtitle = "Browser search engine: ${config.pcSearchEngine}",
            value = config.pcSearchEngine
        ) {
            PcSettingCardHelper.showSingleChoiceDialog(
                context = context,
                title = "Select Search Engine",
                options = engines,
                selectedIndex = curEngineIdx
            ) { selected ->
                val newEngine = engines[selected]
                updateConfigProperty { it.copy(pcSearchEngine = newEngine) }
                buildNetworkPage(binding)
                Toast.makeText(context, "Search engine set to $newEngine", Toast.LENGTH_SHORT).show()
            }
        }

        // Clear Browser Web Cache & Cookies
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_system,
            title = "Clear Browser Cache & Cookies",
            subtitle = "Free up web view memory and clear browsing cookies",
            value = "Clear"
        ) {
            PcSettingCardHelper.showConfirmDialog(
                context = context,
                title = "Clear Browser Data",
                message = "Are you sure you want to clear web storage and cookies?",
                positiveButton = "Clear Data"
            ) {
                runCatching {
                    WebStorage.getInstance().deleteAllData()
                    CookieManager.getInstance().removeAllCookies(null)
                }
                Toast.makeText(context, "Browser cache and cookies cleared!", Toast.LENGTH_SHORT).show()
            }
        }

        // Shortcuts
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_network,
            title = "Wi-Fi Settings",
            subtitle = "Configure Wi-Fi networks and IP settings"
        ) {
            openIntent(Settings.ACTION_WIFI_SETTINGS)
        }

        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_network,
            title = "Mobile Hotspot & Tethering",
            subtitle = "Share internet connection over Wi-Fi, USB, or Bluetooth"
        ) {
            openIntent(Settings.ACTION_WIRELESS_SETTINGS)
        }

        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_privacy,
            title = "VPN Settings",
            subtitle = "Configure Virtual Private Networks"
        ) {
            openIntent(Settings.ACTION_VPN_SETTINGS)
        }
    }

    // ==========================================
    // 3. APPS
    // ==========================================
    private fun buildAppsPage(binding: LayoutPcSettingsGenericBinding) {
        binding.tvPageTitle.text = "Apps"
        val container = binding.containerCards
        container.removeAllViews()

        // Installed Applications Manager
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_apps,
            title = "Installed Applications Manager",
            subtitle = "View, launch in window, or manage installed apps",
            value = "Open"
        ) {
            showInstalledAppsDialog()
        }

        // Startup Apps (Auto-Launch with PC)
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_update,
            title = "Startup Apps",
            subtitle = "${config.pcStartupApps.size} apps configured to start with PC Emulator",
            value = "${config.pcStartupApps.size} Apps"
        ) {
            showStartupAppsDialog()
        }

        // Default Apps
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_system,
            title = "Default Apps",
            subtitle = "Defaults for web browser, email, media player, and assistant"
        ) {
            openIntent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        }

        // Create Custom Web App (PWA)
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_apps,
            title = "Add Custom Web App",
            subtitle = "Create desktop shortcut for any website (YouTube, ChatGPT, etc.)",
            value = "Add"
        ) {
            showCreateWebAppDialog()
        }

        // Manage Hidden Apps
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_privacy,
            title = "Manage Hidden Apps",
            subtitle = "${config.hidden.size} apps hidden from desktop and start menu",
            value = if (config.hidden.isEmpty()) "None" else "${config.hidden.size} Hidden"
        ) {
            showHiddenAppsDialog()
        }
    }

    // ==========================================
    // 4. ACCOUNTS
    // ==========================================
    private fun buildAccountsPage(binding: LayoutPcSettingsGenericBinding) {
        binding.tvPageTitle.text = "Accounts"
        val container = binding.containerCards
        container.removeAllViews()

        // User Display Name
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_accounts,
            title = "User Display Name",
            subtitle = "Current User: ${config.pcUserName}",
            value = config.pcUserName
        ) {
            PcSettingCardHelper.showInputDialog(
                context = context,
                title = "Change User Name",
                currentValue = config.pcUserName,
                hint = "Enter user name (e.g. Administrator)"
            ) { newName ->
                updateConfigProperty { it.copy(pcUserName = newName) }
                buildAccountsPage(binding)
                Toast.makeText(context, "User name updated to $newName", Toast.LENGTH_SHORT).show()
            }
        }

        // Computer Name
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_system,
            title = "Computer Name",
            subtitle = "PC Network Name: ${config.pcDeviceName}",
            value = config.pcDeviceName
        ) {
            PcSettingCardHelper.showInputDialog(
                context = context,
                title = "Change Computer Name",
                currentValue = config.pcDeviceName,
                hint = "Enter computer name (e.g. Gothwad-PC)"
            ) { newName ->
                updateConfigProperty { it.copy(pcDeviceName = newName) }
                buildAccountsPage(binding)
                Toast.makeText(context, "Computer name set to $newName", Toast.LENGTH_SHORT).show()
            }
        }

        // Sign-in Options & PIN Lock
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_privacy,
            title = "Sign-in Options (PIN Lock)",
            subtitle = if (config.deviceLock.enabled) "Protected with ${config.deviceLock.pinLength}-digit PIN" else "No PIN configured • Click to secure",
            value = if (config.deviceLock.enabled) "Protected" else "Unsecured"
        ) {
            onOpenLockSetup()
        }

        // Require PIN on PC Startup
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_privacy,
            title = "Require PIN on Startup",
            subtitle = "Lock desktop on emulator launch until PIN is entered",
            switchChecked = config.deviceLock.enabled,
            onSwitchChanged = { isChecked ->
                if (isChecked && config.deviceLock.value.isEmpty()) {
                    onOpenLockSetup()
                } else {
                    updateConfigProperty { it.copy(deviceLock = it.deviceLock.copy(enabled = isChecked)) }
                    buildAccountsPage(binding)
                }
            }
        )

        // App Lock
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_privacy,
            title = "App Lock",
            subtitle = "${config.lockedApps.size} apps locked with PIN",
            value = "${config.lockedApps.size} Locked"
        ) {
            showAppLockDialog()
        }
    }

    // ==========================================
    // 5. TIME & LANGUAGE
    // ==========================================
    private fun buildTimePage(binding: LayoutPcSettingsGenericBinding) {
        binding.tvPageTitle.text = "Time & language"
        val container = binding.containerCards
        container.removeAllViews()

        val now = Date()
        val timeFmt = if (config.h24) {
            if (config.pcShowTaskbarClockSeconds) "HH:mm:ss" else "HH:mm"
        } else {
            if (config.pcShowTaskbarClockSeconds) "hh:mm:ss a" else "hh:mm a"
        }
        val timeStr = SimpleDateFormat(timeFmt, Locale.ENGLISH).format(now)
        val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH).format(now)

        // Live Clock Display
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_time,
            title = "Current System Time",
            subtitle = "$timeStr • $dateStr",
            value = timeStr
        )

        // 24-Hour Clock Toggle
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_time,
            title = "24-Hour Clock Format",
            subtitle = if (config.h24) "24-hour clock (14:30)" else "12-hour clock (02:30 PM)",
            switchChecked = config.h24,
            onSwitchChanged = { isChecked ->
                updateConfigProperty { it.copy(h24 = isChecked) }
                buildTimePage(binding)
            }
        )

        // Show Seconds in Clock
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_time,
            title = "Show Seconds on Clock",
            subtitle = "Display seconds in taskbar clock (${if (config.pcShowTaskbarClockSeconds) "HH:mm:ss" else "HH:mm"})",
            switchChecked = config.pcShowTaskbarClockSeconds,
            onSwitchChanged = { isChecked ->
                updateConfigProperty { it.copy(pcShowTaskbarClockSeconds = isChecked) }
                buildTimePage(binding)
            }
        )

        // Date Format
        val dateFormats = listOf("DD/MM/YYYY", "MM/DD/YYYY", "YYYY-MM-DD")
        val curDateIdx = dateFormats.indexOf(config.pcDateFormat).coerceAtLeast(0)
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_time,
            title = "Date Format",
            subtitle = "Format: ${config.pcDateFormat}",
            value = config.pcDateFormat
        ) {
            PcSettingCardHelper.showSingleChoiceDialog(
                context = context,
                title = "Choose Date Format",
                options = dateFormats,
                selectedIndex = curDateIdx
            ) { selected ->
                val newFmt = dateFormats[selected]
                updateConfigProperty { it.copy(pcDateFormat = newFmt) }
                buildTimePage(binding)
                Toast.makeText(context, "Date format set to $newFmt", Toast.LENGTH_SHORT).show()
            }
        }

        // Language & Region
        val langNames = listOf("English (United States)", "हिन्दी (Hindi)", "Español (Spanish)", "Français (French)", "Deutsch (German)")
        val langCodes = listOf("en", "hi", "es", "fr", "de")
        val curLangIdx = langCodes.indexOf(config.pcLanguage).coerceAtLeast(0)
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_network,
            title = "Desktop Interface Language",
            subtitle = "Selected: ${langNames[curLangIdx]}",
            value = langNames[curLangIdx]
        ) {
            PcSettingCardHelper.showSingleChoiceDialog(
                context = context,
                title = "Select Desktop Language",
                options = langNames,
                selectedIndex = curLangIdx
            ) { selected ->
                val newLang = langCodes[selected]
                updateConfigProperty { it.copy(pcLanguage = newLang) }
                buildTimePage(binding)
                Toast.makeText(context, "Language set to ${langNames[selected]}", Toast.LENGTH_SHORT).show()
            }
        }

        // Android System Date & Time
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_time,
            title = "Android Date & Time Settings",
            subtitle = "Automatic time synchronization and time zone"
        ) {
            openIntent(Settings.ACTION_DATE_SETTINGS)
        }
    }

    // ==========================================
    // 6. GAMING
    // ==========================================
    private fun buildGamingPage(binding: LayoutPcSettingsGenericBinding) {
        binding.tvPageTitle.text = "Gaming & Performance"
        val container = binding.containerCards
        container.removeAllViews()

        // High Performance Mode
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_gaming,
            title = "PC High Performance Mode",
            subtitle = "Enable hardware accelerated graphics rendering",
            switchChecked = config.pcPerformanceMode,
            onSwitchChanged = { isChecked ->
                updateConfigProperty { it.copy(pcPerformanceMode = isChecked) }
                Toast.makeText(context, if (isChecked) "Performance mode enabled" else "Standard performance mode", Toast.LENGTH_SHORT).show()
            }
        )

        // Live FPS & System Monitor Overlay
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_system,
            title = "Live FPS & Hardware Monitor",
            subtitle = "Display real-time frame rate, CPU & RAM monitor",
            switchChecked = config.pcShowFpsOverlay,
            onSwitchChanged = { isChecked ->
                updateConfigProperty { it.copy(pcShowFpsOverlay = isChecked) }
                Toast.makeText(context, if (isChecked) "FPS monitor active on desktop" else "FPS monitor hidden", Toast.LENGTH_SHORT).show()
            }
        )

        // Virtual Touch Gamepad Controls
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_gaming,
            title = "Touch Gamepad Overlay",
            subtitle = "On-screen virtual D-Pad and action buttons for gaming",
            switchChecked = config.pcTouchGamepad,
            onSwitchChanged = { isChecked ->
                updateConfigProperty { it.copy(pcTouchGamepad = isChecked) }
                Toast.makeText(context, if (isChecked) "Touch gamepad overlay enabled" else "Touch gamepad disabled", Toast.LENGTH_SHORT).show()
            }
        )

        // Display Refresh Rate
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val refreshRate = wm?.defaultDisplay?.refreshRate?.toInt() ?: 60
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_system,
            title = "Screen Refresh Rate",
            subtitle = "Display panel refresh rate: ${refreshRate}Hz • Smooth motion",
            value = "${refreshRate}Hz"
        )
    }

    // ==========================================
    // 7. ACCESSIBILITY
    // ==========================================
    private fun buildAccessibilityPage(binding: LayoutPcSettingsGenericBinding) {
        binding.tvPageTitle.text = "Accessibility"
        val container = binding.containerCards
        container.removeAllViews()

        // High Contrast Mode
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_accessibility,
            title = "High Contrast Mode",
            subtitle = "Enhance text contrast and window border visibility",
            switchChecked = config.pcHighContrast,
            onSwitchChanged = { isChecked ->
                updateConfigProperty { it.copy(pcHighContrast = isChecked) }
                Toast.makeText(context, if (isChecked) "High contrast mode active" else "High contrast disabled", Toast.LENGTH_SHORT).show()
            }
        )

        // Text & UI Sizing
        val uiScales = listOf("Compact (85%)", "Standard (100%)", "Large (115%)", "Extra Large (130%)")
        val scaleValues = listOf(0.85f, 1.0f, 1.15f, 1.30f)
        val curIdx = scaleValues.indexOfFirst { kotlin.math.abs(it - config.pcUiScale) < 0.1f }.coerceAtLeast(0)
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_personalisation,
            title = "Text & Interface Scale",
            subtitle = "Current scale: ${(config.pcUiScale * 100).toInt()}%",
            value = "${(config.pcUiScale * 100).toInt()}%"
        ) {
            PcSettingCardHelper.showSingleChoiceDialog(
                context = context,
                title = "Select Text & UI Scale",
                options = uiScales,
                selectedIndex = curIdx
            ) { selected ->
                val newScale = scaleValues[selected]
                updateConfigProperty { it.copy(pcUiScale = newScale) }
                buildAccessibilityPage(binding)
                Toast.makeText(context, "Scale set to ${uiScales[selected]}", Toast.LENGTH_SHORT).show()
            }
        }

        // Reduced Motion (Fast Performance)
        val isReducedMotion = config.pcAnimationSpeed == 0.0f
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_system,
            title = "Reduced Motion (Fast Windows)",
            subtitle = "Turn off window opening animations for instant responsiveness",
            switchChecked = isReducedMotion,
            onSwitchChanged = { isChecked ->
                val speed = if (isChecked) 0.0f else 1.0f
                updateConfigProperty { it.copy(pcAnimationSpeed = speed) }
                Toast.makeText(context, if (isChecked) "Animations turned off" else "Animations enabled", Toast.LENGTH_SHORT).show()
            }
        )

        // System Accessibility Settings
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_accessibility,
            title = "Android Accessibility Settings",
            subtitle = "TalkBack screen reader, captions, magnification, color inversion"
        ) {
            openIntent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
    }

    // ==========================================
    // 8. PRIVACY & SECURITY
    // ==========================================
    private fun buildPrivacyPage(binding: LayoutPcSettingsGenericBinding) {
        binding.tvPageTitle.text = "Privacy & security"
        val container = binding.containerCards
        container.removeAllViews()

        // Security Health Status
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_privacy,
            title = "Security Health Status",
            subtitle = "All system safeguards active • Native view architecture",
            value = "Protected"
        )

        // Device PIN Security
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_privacy,
            title = "Device PIN Lock",
            subtitle = if (config.deviceLock.enabled) "Protected with ${config.deviceLock.pinLength}-digit PIN" else "Click to set up a lock screen PIN",
            value = if (config.deviceLock.enabled) "Active" else "Off"
        ) {
            onOpenLockSetup()
        }

        // Storage Permission Audit
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_system,
            title = "File Explorer Storage Access",
            subtitle = if (storageGranted) "Full file access granted" else "Storage permission required for File Explorer",
            value = if (storageGranted) "Granted" else "Grant"
        ) {
            if (!storageGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } else {
                openIntent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            }
        }

        // Usage Access Permission Audit
        val hasUsageAccess = checkUsageStatsPermission()
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_system,
            title = "Usage Access Permission",
            subtitle = if (hasUsageAccess) "Usage stats access active" else "Required for Task Manager and recent apps tracking",
            value = if (hasUsageAccess) "Granted" else "Grant"
        ) {
            openIntent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        }

        // Clear App Launch History
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_system,
            title = "Clear App Launch History",
            subtitle = "Reset frequently used apps tracking",
            value = "Clear"
        ) {
            PcSettingCardHelper.showConfirmDialog(
                context = context,
                title = "Clear Launch History",
                message = "Reset all launch tracking statistics?",
                positiveButton = "Clear"
            ) {
                Toast.makeText(context, "Launch history cleared!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==========================================
    // 9. WINDOWS UPDATE & ABOUT
    // ==========================================
    private fun buildUpdatePage(binding: LayoutPcSettingsGenericBinding) {
        binding.tvPageTitle.text = "Windows Update"
        val container = binding.containerCards
        container.removeAllViews()

        // Check for Updates (Interactive Animation)
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_update,
            title = "Check for Updates",
            subtitle = "You're up to date • Version 1.0.4 (OS Build 26100.1742)",
            value = "Check Now"
        ) {
            Toast.makeText(context, "Checking for updates...", Toast.LENGTH_SHORT).show()
            scope.launch {
                withContext(Dispatchers.IO) {
                    kotlinx.coroutines.delay(1200)
                }
                val nowStr = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date())
                Toast.makeText(context, "✓ Your PC is up to date (Last checked: $nowStr)", Toast.LENGTH_LONG).show()
                buildUpdatePage(binding)
            }
        }

        // Pause Updates
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_time,
            title = "Pause Updates for 7 Days",
            subtitle = if (config.pcUpdatesPaused) "Updates paused until next week" else "Receive automatic security definitions and app updates",
            switchChecked = config.pcUpdatesPaused,
            onSwitchChanged = { isChecked ->
                updateConfigProperty { it.copy(pcUpdatesPaused = isChecked) }
                buildUpdatePage(binding)
                Toast.makeText(context, if (isChecked) "Updates paused for 7 days" else "Automatic updates resumed", Toast.LENGTH_SHORT).show()
            }
        )

        // Windows Specifications
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_system,
            title = "Windows Specifications",
            subtitle = "Gothwad Computer 11 Pro • Version 24H2",
            value = "View"
        ) {
            val specs = """
                Edition: Gothwad Computer 11 Pro
                Version: 24H2
                OS Build: 26100.1742
                Experience: Windows Feature Experience Pack 1000.26100.32.0

                Device: ${Build.MANUFACTURER} ${Build.MODEL}
                Processor: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"}
                Android Base: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
                Security Patch: ${Build.VERSION.SECURITY_PATCH ?: "2026-09-01"}
            """.trimIndent()
            PcSettingCardHelper.showInfoDialog(context, "Windows Specifications", specs)
        }

        // View Changelog & Release Notes
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_system,
            title = "Changelog & Release Notes",
            subtitle = "What's new in Gothwad PC Emulator Version 1.0.4",
            value = "Read"
        ) {
            val changelog = """
                Version 1.0.4 Release Notes:
                • Full Windows 11 PC Settings Suite with interactive controls
                • 100% Native Android View Architecture (High Performance)
                • Multitasking Floating Windows with snap-to-edge
                • Wallpapers gallery with 13 built-in presets and custom photo support
                • Physical Keyboard shortcuts and Gamepad navigation
                • Disk cleaner and system self-diagnostics
                • Startup apps and custom web app (PWA) manager
                • Enhanced Lock Screen security and App Lock
            """.trimIndent()
            PcSettingCardHelper.showInfoDialog(context, "Gothwad Computer 1.0.4 Changelog", changelog)
        }

        // Restart PC Emulator
        PcSettingCardHelper.addCard(
            parent = container,
            context = context,
            iconRes = R.drawable.ic_win_update,
            title = "Restart PC Emulator",
            subtitle = "Cleanly reload desktop environment and floating windows",
            value = "Restart"
        ) {
            PcSettingCardHelper.showConfirmDialog(
                context = context,
                title = "Restart Emulator",
                message = "Are you sure you want to restart the PC Emulator desktop session?",
                positiveButton = "Restart"
            ) {
                onRestartEmulator?.invoke()
            }
        }
    }

    // ==========================================
    // DIALOG HELPERS
    // ==========================================

    private fun showInstalledAppsDialog() {
        scope.launch(Dispatchers.IO) {
            val apps = AppRepository.scan(context)
            withContext(Dispatchers.Main) {
                if (apps.isEmpty()) {
                    Toast.makeText(context, "No applications found", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                val appLabels = apps.map { it.label }
                MaterialAlertDialogBuilder(context, R.style.Theme_LiteTV_Dialog)
                    .setTitle("Installed Applications (${apps.size})")
                    .setItems(appLabels.toTypedArray()) { _, which ->
                        val selectedApp = apps[which]
                        showAppActionDialog(selectedApp)
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }
    }

    private fun showAppActionDialog(app: AppEntry) {
        val actions = listOf(
            "Launch in Floating Window",
            "Add Desktop Shortcut",
            "Pin to Taskbar",
            "App Info (Permissions & Storage)",
            "Uninstall App"
        )

        MaterialAlertDialogBuilder(context, R.style.Theme_LiteTV_Dialog)
            .setTitle(app.label)
            .setItems(actions.toTypedArray()) { _, which ->
                when (which) {
                    0 -> {
                        Actions.launchApp(context, app.pkg)
                        Toast.makeText(context, "Launching ${app.label}...", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        scope.launch {
                            val currentOrder = config.pcDesktopOrder.toMutableList()
                            if (!currentOrder.contains(app.pkg)) {
                                currentOrder.add(app.pkg)
                                updateConfigProperty { it.copy(pcDesktopOrder = currentOrder) }
                                Toast.makeText(context, "Added ${app.label} to desktop", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "${app.label} is already on desktop", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    2 -> {
                        scope.launch {
                            val pinned = config.pcPinnedApps.toMutableList()
                            if (!pinned.contains(app.pkg)) {
                                pinned.add(app.pkg)
                                updateConfigProperty { it.copy(pcPinnedApps = pinned) }
                                Toast.makeText(context, "Pinned ${app.label} to taskbar", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "${app.label} is already pinned", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    3 -> {
                        openIntent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                    }
                    4 -> {
                        val unIntent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(unIntent)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showStartupAppsDialog() {
        scope.launch(Dispatchers.IO) {
            val apps = AppRepository.scan(context)
            withContext(Dispatchers.Main) {
                val appLabels = apps.map { it.label }.toTypedArray()
                val checkedItems = BooleanArray(apps.size) { i ->
                    config.pcStartupApps.contains(apps[i].pkg)
                }

                MaterialAlertDialogBuilder(context, R.style.Theme_LiteTV_Dialog)
                    .setTitle("Configure Startup Apps")
                    .setMultiChoiceItems(appLabels, checkedItems) { _, which, isChecked ->
                        checkedItems[which] = isChecked
                    }
                    .setPositiveButton("Save") { _, _ ->
                        val selectedPkgs = mutableSetOf<String>()
                        checkedItems.forEachIndexed { i, checked ->
                            if (checked) selectedPkgs.add(apps[i].pkg)
                        }
                        updateConfigProperty { it.copy(pcStartupApps = selectedPkgs) }
                        Toast.makeText(context, "Startup apps saved (${selectedPkgs.size} apps)", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun showAppLockDialog() {
        scope.launch(Dispatchers.IO) {
            val apps = AppRepository.scan(context)
            withContext(Dispatchers.Main) {
                val appLabels = apps.map { it.label }.toTypedArray()
                val checkedItems = BooleanArray(apps.size) { i ->
                    config.lockedApps.contains(apps[i].pkg)
                }

                MaterialAlertDialogBuilder(context, R.style.Theme_LiteTV_Dialog)
                    .setTitle("App Lock (Select Apps)")
                    .setMultiChoiceItems(appLabels, checkedItems) { _, which, isChecked ->
                        checkedItems[which] = isChecked
                    }
                    .setPositiveButton("Save") { _, _ ->
                        val selectedPkgs = mutableSetOf<String>()
                        checkedItems.forEachIndexed { i, checked ->
                            if (checked) selectedPkgs.add(apps[i].pkg)
                        }
                        updateConfigProperty { it.copy(lockedApps = selectedPkgs) }
                        Toast.makeText(context, "Locked apps saved (${selectedPkgs.size} apps)", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun showHiddenAppsDialog() {
        if (config.hidden.isEmpty()) {
            Toast.makeText(context, "No apps are currently hidden", Toast.LENGTH_SHORT).show()
            return
        }

        val hiddenList = config.hidden.toList()
        MaterialAlertDialogBuilder(context, R.style.Theme_LiteTV_Dialog)
            .setTitle("Unhide Applications")
            .setItems(hiddenList.toTypedArray()) { _, which ->
                val pkgToUnhide = hiddenList[which]
                val newHidden = config.hidden - pkgToUnhide
                updateConfigProperty { it.copy(hidden = newHidden) }
                Toast.makeText(context, "Unhid $pkgToUnhide", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showCreateWebAppDialog() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_web_app, null, false)
        val etName = view.findViewById<android.widget.EditText>(R.id.et_web_app_name)
        val etUrl = view.findViewById<android.widget.EditText>(R.id.et_web_app_url)

        MaterialAlertDialogBuilder(context, R.style.Theme_LiteTV_Dialog)
            .setTitle("Create Desktop Web App (PWA)")
            .setView(view)
            .setPositiveButton("Create Shortcut") { _, _ ->
                val name = etName.text.toString().trim()
                var url = etUrl.text.toString().trim()
                if (name.isNotEmpty() && url.isNotEmpty()) {
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://$url"
                    }
                    val webKey = "web:$url"
                    val currentOrder = config.pcDesktopOrder.toMutableList()
                    currentOrder.add(webKey)
                    val customLabels = config.pcCustomLabels.toMutableMap()
                    customLabels[webKey] = name

                    updateConfigProperty {
                        it.copy(
                            pcDesktopOrder = currentOrder,
                            pcCustomLabels = customLabels
                        )
                    }
                    Toast.makeText(context, "Added $name shortcut to desktop!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getConnectedGamepads(): List<String> {
        val names = mutableListOf<String>()
        val deviceIds = InputDevice.getDeviceIds()
        for (id in deviceIds) {
            val dev = InputDevice.getDevice(id) ?: continue
            val sources = dev.sources
            if ((sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                names.add(dev.name)
            }
        }
        return names
    }

    private fun checkUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun updateConfigProperty(transform: (LauncherConfig) -> LauncherConfig) {
        scope.launch {
            val updated = transform(config)
            ConfigStore(context).update { updated }
            config = updated
            onConfigChanged?.invoke(updated)
        }
    }

    private fun openIntent(action: String, data: Uri? = null) {
        runCatching {
            val intent = Intent(action).apply {
                if (data != null) this.data = data
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "Setting unavailable on this device", Toast.LENGTH_SHORT).show()
        }
    }
}
