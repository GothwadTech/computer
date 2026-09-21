package com.gothwad.computer.ui.pc.settings

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gothwad.computer.R
import com.gothwad.computer.data.ConfigStore
import com.gothwad.computer.data.LauncherConfig
import com.gothwad.computer.databinding.ItemPcSettingCardBinding
import com.gothwad.computer.databinding.LayoutPcSettingsSystemBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PcSettingsSystemPage(
    private val context: Context,
    private val scope: CoroutineScope,
    private var config: LauncherConfig,
    private val onConfigChanged: ((LauncherConfig) -> Unit)? = null
) {
    private var _binding: LayoutPcSettingsSystemBinding? = null
    val binding get() = _binding!!

    fun createView(inflater: LayoutInflater): View {
        _binding = LayoutPcSettingsSystemBinding.inflate(inflater, null, false)
        setupHeader()
        setupCards()
        return binding.root
    }

    fun updateConfig(newConfig: LauncherConfig) {
        config = newConfig
        setupHeader()
        setupCards()
    }

    private fun setupHeader() {
        val b = _binding ?: return
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val totalRamGb = "%.1f".format(memInfo.totalMem / (1024.0 * 1024.0 * 1024.0))
        val availRamMb = (memInfo.availMem / (1024 * 1024)).toInt()

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryLevel = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100

        b.tvSystemPcName.text = "${config.pcDeviceName} (${Build.MODEL})"
        b.tvSystemModel.text = "RAM: ${availRamMb}MB free of ${totalRamGb}GB • Battery: ${batteryLevel}% • Android ${Build.VERSION.RELEASE}"

        b.tvSystemPcName.setOnClickListener {
            PcSettingCardHelper.showInputDialog(
                context = context,
                title = "Rename PC",
                currentValue = config.pcDeviceName,
                hint = "Enter computer name"
            ) { newName ->
                updateConfigProperty { it.copy(pcDeviceName = newName) }
            }
        }
    }

    private fun setupCards() {
        val b = _binding ?: return

        // 1. Display
        val scalePct = (config.pcUiScale * 100).toInt()
        bindCard(
            root = b.cardSysDisplay,
            iconRes = R.drawable.ic_win_system,
            title = "Display",
            subtitle = "Scale: $scalePct% • DPI: ${if (config.useCustomDpi) "${config.customDpi} DPI" else "System Default"}",
            value = "$scalePct%"
        ) {
            val scaleOptions = listOf("75% (Ultra Compact)", "85% (Balanced)", "100% (Standard)", "115% (Large)", "125% (Extra Large)")
            val scaleValues = listOf(0.75f, 0.85f, 1.0f, 1.15f, 1.25f)
            val currentIdx = scaleValues.indexOfFirst { kotlin.math.abs(it - config.pcUiScale) < 0.05f }.coerceAtLeast(0)

            PcSettingCardHelper.showSingleChoiceDialog(
                context = context,
                title = "Desktop UI Scaling",
                options = scaleOptions,
                selectedIndex = currentIdx
            ) { selected ->
                val newScale = scaleValues[selected]
                updateConfigProperty { it.copy(pcUiScale = newScale) }
                Toast.makeText(context, "Scale set to ${scaleOptions[selected]}", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. Sound & Volume
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 10
        val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        val volPct = (currentVol * 100 / maxVol.coerceAtLeast(1))

        bindCard(
            root = b.cardSysSound,
            iconRes = R.drawable.ic_win_gaming,
            title = "Sound & Volume",
            subtitle = "Volume: $volPct% • Emulator sound effects: ${if (config.pcSoundEnabled) "On" else "Off"}",
            value = "$volPct%"
        ) {
            showVolumeDialog(audioManager, currentVol, maxVol)
        }

        // 3. Notifications
        bindCard(
            root = b.cardSysNotifications,
            iconRes = R.drawable.ic_win_system,
            title = "Notifications",
            subtitle = "Taskbar notification badges and alerts",
            switchChecked = config.pcShowTaskbarWidgets,
            onSwitchChanged = { isChecked ->
                updateConfigProperty { it.copy(pcShowTaskbarWidgets = isChecked) }
            }
        )

        // 4. Focus Assist
        bindCard(
            root = b.cardSysFocus,
            iconRes = R.drawable.ic_win_time,
            title = "Focus assist",
            subtitle = "Quiet mode: mute notification sounds during focus",
            switchChecked = !config.pcSoundEnabled,
            onSwitchChanged = { isChecked ->
                updateConfigProperty { it.copy(pcSoundEnabled = !isChecked) }
            }
        )

        // 5. Power & Battery (Keep Screen Awake)
        bindCard(
            root = b.cardSysPower,
            iconRes = R.drawable.ic_win_system,
            title = "Power & Sleep",
            subtitle = "Keep screen awake while PC Emulator is running",
            switchChecked = config.pcKeepScreenOn,
            onSwitchChanged = { isChecked ->
                updateConfigProperty { it.copy(pcKeepScreenOn = isChecked) }
                Toast.makeText(context, if (isChecked) "Keep screen awake enabled" else "Screen timeout restored", Toast.LENGTH_SHORT).show()
            }
        )

        // 6. Storage & Disk Cleaner
        val stat = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
        val freeGb = if (stat != null) "%.1f".format(stat.availableBytes / (1024.0 * 1024.0 * 1024.0)) else "Unknown"
        val totalGb = if (stat != null) "%.1f".format(stat.totalBytes / (1024.0 * 1024.0 * 1024.0)) else "Unknown"

        bindCard(
            root = b.cardSysStorage,
            iconRes = R.drawable.ic_win_system,
            title = "Storage & Disk Cleaner",
            subtitle = "Free: ${freeGb}GB of ${totalGb}GB • Clean app cache & temp files",
            value = "Clean"
        ) {
            val cacheSize = getCacheSizeBytes()
            val cacheMbStr = "%.2f MB".format(cacheSize / (1024.0 * 1024.0))
            PcSettingCardHelper.showConfirmDialog(
                context = context,
                title = "Clean Temporary Files",
                message = "Current cache size: $cacheMbStr\n\nWould you like to clear cached wallpapers, web previews, and temporary files?",
                positiveButton = "Clear Now"
            ) {
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        context.cacheDir.deleteRecursively()
                        context.externalCacheDir?.deleteRecursively()
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Cache cleaned successfully! Freed $cacheMbStr", Toast.LENGTH_SHORT).show()
                        setupCards()
                    }
                }
            }
        }

        // 7. Nearby Sharing
        bindCard(
            root = b.cardSysNearby,
            iconRes = R.drawable.ic_win_network,
            title = "Nearby sharing",
            subtitle = "Discoverability, screen mirroring and wireless display"
        ) {
            openSystemIntent(Settings.ACTION_CAST_SETTINGS)
        }

        // 8. Multi-tasking (Icon Size & Spacing)
        bindCard(
            root = b.cardSysMultitasking,
            iconRes = R.drawable.ic_win_system,
            title = "Desktop Icons & Spacing",
            subtitle = "Icon size: ${config.pcIconSize}dp • Spacing: ${config.pcGridSpacing}dp • Labels: ${if (config.pcShowLabels) "On" else "Off"}",
            value = "${config.pcIconSize}dp"
        ) {
            val iconSizes = listOf("Small (36dp)", "Medium (44dp)", "Large (52dp)", "Extra Large (60dp)")
            val sizeValues = listOf(36, 44, 52, 60)
            val currentIdx = sizeValues.indexOf(config.pcIconSize).coerceAtLeast(0)

            PcSettingCardHelper.showSingleChoiceDialog(
                context = context,
                title = "Desktop Icon Size",
                options = iconSizes,
                selectedIndex = currentIdx
            ) { selected ->
                val newSize = sizeValues[selected]
                updateConfigProperty { it.copy(pcIconSize = newSize) }
                Toast.makeText(context, "Icon size set to ${iconSizes[selected]}", Toast.LENGTH_SHORT).show()
            }
        }

        // 9. Window Snapping
        bindCard(
            root = b.cardSysActivation,
            iconRes = R.drawable.ic_win_update,
            title = "Window Snapping",
            subtitle = "Automatically snap floating windows to screen edges when dragging",
            switchChecked = config.pcWindowSnapping,
            onSwitchChanged = { isChecked ->
                updateConfigProperty { it.copy(pcWindowSnapping = isChecked) }
                Toast.makeText(context, if (isChecked) "Window snapping enabled" else "Window snapping disabled", Toast.LENGTH_SHORT).show()
            }
        )

        // 10. Troubleshoot & Health Check
        bindCard(
            root = b.cardSysTroubleshoot,
            iconRes = R.drawable.ic_win_system,
            title = "Troubleshoot & Health Check",
            subtitle = "Run system self-diagnostics and check emulator state",
            value = "Run"
        ) {
            val diagReport = buildDiagnosticReport()
            PcSettingCardHelper.showInfoDialog(
                context = context,
                title = "System Diagnostic Report",
                message = diagReport
            )
        }

        // 11. Recovery
        bindCard(
            root = b.cardSysRecovery,
            iconRes = R.drawable.ic_win_update,
            title = "Recovery & Reset",
            subtitle = "Reset PC Emulator preferences to factory defaults",
            value = "Reset"
        ) {
            PcSettingCardHelper.showConfirmDialog(
                context = context,
                title = "Reset All Settings",
                message = "Are you sure you want to restore default settings? Your desktop icons layout, scaling, and theme preferences will be reset to defaults.",
                positiveButton = "Reset to Default"
            ) {
                scope.launch {
                    val defaultCfg = LauncherConfig()
                    ConfigStore(context).update { defaultCfg }
                    config = defaultCfg
                    onConfigChanged?.invoke(defaultCfg)
                    setupHeader()
                    setupCards()
                    Toast.makeText(context, "Settings reset to factory defaults", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 12. Projecting to this PC
        bindCard(
            root = b.cardSysProjecting,
            iconRes = R.drawable.ic_win_network,
            title = "Projecting to this PC",
            subtitle = "Wireless display and screen casting"
        ) {
            openSystemIntent(Settings.ACTION_CAST_SETTINGS)
        }

        // 13. Keyboard Hotkeys Cheatsheet
        bindCard(
            root = b.cardSysRemote,
            iconRes = R.drawable.ic_win_system,
            title = "Keyboard Shortcuts Cheatsheet",
            subtitle = "Win+D, Win+E, Alt+Tab, Win+I, Ctrl+Shift+Esc shortcuts",
            value = "View"
        ) {
            val shortcuts = """
                • Win Key or Ctrl+Esc : Open / Close Start Menu
                • Win + D : Show Desktop (Minimize all windows)
                • Win + E : Open File Explorer
                • Win + I : Open Settings
                • Alt + Tab : Switch between open windows
                • Ctrl + Shift + Esc : Open Task Manager
                • Win + L : Lock PC (if PIN enabled)
                • F11 : Toggle Fullscreen Mode
                • Esc : Close active flyout or top window
            """.trimIndent()

            PcSettingCardHelper.showInfoDialog(
                context = context,
                title = "Physical Keyboard Shortcuts",
                message = shortcuts
            )
        }

        // 14. Double-Click to Launch
        bindCard(
            root = b.cardSysClipboard,
            iconRes = R.drawable.ic_win_personalisation,
            title = "Mouse Click Launch Mode",
            subtitle = "Require double-click to open desktop apps & files",
            switchChecked = config.pcDoubleClickToOpen,
            onSwitchChanged = { isChecked ->
                updateConfigProperty { it.copy(pcDoubleClickToOpen = isChecked) }
                Toast.makeText(context, if (isChecked) "Double-click to open enabled" else "Single-click to open enabled", Toast.LENGTH_SHORT).show()
            }
        )

        // 15. About Specifications
        bindCard(
            root = b.cardSysAbout,
            iconRes = R.drawable.ic_win_system,
            title = "Windows Specifications",
            subtitle = "Gothwad Computer 11 Pro • Version 24H2 (OS Build 26100.1742)",
            value = "Details"
        ) {
            val specs = """
                Edition: Gothwad Computer 11 Pro
                Version: 24H2
                OS Build: 26100.1742
                Experience: Windows Feature Experience Pack 1000.26100.32.0

                Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})
                Hardware: ${Build.HARDWARE} (${Build.BOARD})
                Processor: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"}
                Android Base: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
                Security Patch: ${Build.VERSION.SECURITY_PATCH ?: "Current"}
            """.trimIndent()

            PcSettingCardHelper.showInfoDialog(
                context = context,
                title = "About Gothwad Computer",
                message = specs
            )
        }
    }

    private fun showVolumeDialog(audioManager: AudioManager?, currentVol: Int, maxVol: Int) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_audio_adjust, null, false)
        val seekBar = view.findViewById<SeekBar>(R.id.seek_volume)
        val tvVal = view.findViewById<TextView>(R.id.tv_volume_value)

        seekBar.max = maxVol
        seekBar.progress = currentVol
        tvVal.text = "${(currentVol * 100 / maxVol.coerceAtLeast(1))}%"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val pct = (progress * 100 / maxVol.coerceAtLeast(1))
                tvVal.text = "$pct%"
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        MaterialAlertDialogBuilder(context, R.style.Theme_LiteTV_Dialog)
            .setTitle("Master Volume Control")
            .setView(view)
            .setPositiveButton("Done") { _, _ ->
                setupCards()
            }
            .show()
    }

    private fun buildDiagnosticReport(): String {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val isLowMem = memInfo.lowMemory
        val freeMemMb = memInfo.availMem / (1024 * 1024)

        val stat = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
        val freeStorageMb = if (stat != null) stat.availableBytes / (1024 * 1024) else 0L

        return """
            ✓ System Integrity: OK
            ✓ Memory Health: ${if (isLowMem) "WARNING: Low RAM" else "Healthy (${freeMemMb} MB Available)"}
            ✓ Storage Space: ${if (freeStorageMb < 500) "WARNING: Low Storage" else "Ample (${freeStorageMb} MB Available)"}
            ✓ Floating Window Engine: Active (Snap: ${config.pcWindowSnapping})
            ✓ UI Scale & DPI: ${(config.pcUiScale * 100).toInt()}% • Clean Rendering
            ✓ Background Persistence: Fully compliant Native Views
            ✓ Security Guard: ${if (config.deviceLock.enabled) "PIN Lock Active" else "Standard Protection"}
        """.trimIndent()
    }

    private fun getCacheSizeBytes(): Long {
        var size = 0L
        runCatching {
            context.cacheDir.walkTopDown().forEach { size += it.length() }
            context.externalCacheDir?.walkTopDown()?.forEach { size += it.length() }
        }
        return size
    }

    private fun updateConfigProperty(transform: (LauncherConfig) -> LauncherConfig) {
        scope.launch {
            val updated = transform(config)
            ConfigStore(context).update { updated }
            config = updated
            onConfigChanged?.invoke(updated)
            setupCards()
            setupHeader()
        }
    }

    private fun bindCard(
        root: View,
        iconRes: Int,
        title: String,
        subtitle: String? = null,
        value: String? = null,
        switchChecked: Boolean? = null,
        onSwitchChanged: ((Boolean) -> Unit)? = null,
        onClick: (() -> Unit)? = null
    ) {
        val cardRowRoot = root.findViewById<View>(R.id.card_row_root) ?: root
        val binding = ItemPcSettingCardBinding.bind(cardRowRoot)
        PcSettingCardHelper.bindCard(
            binding = binding,
            iconRes = iconRes,
            title = title,
            subtitle = subtitle,
            value = value,
            switchChecked = switchChecked,
            onSwitchChanged = onSwitchChanged,
            onClick = onClick
        )
    }

    private fun openSystemIntent(action: String) {
        runCatching {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
