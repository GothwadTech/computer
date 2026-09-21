package com.gothwad.computer.ui.pc.settings

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gothwad.computer.R
import com.gothwad.computer.data.ConfigStore
import com.gothwad.computer.data.LauncherConfig
import com.gothwad.computer.databinding.ItemPcSettingCardBinding
import com.gothwad.computer.databinding.LayoutPcSettingsPersonalisationBinding
import com.gothwad.computer.ui.PC_WALLPAPERS
import com.gothwad.computer.ui.PcWallpaperPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

class PcSettingsPersonalisationPage(
    private val context: Context,
    private val scope: CoroutineScope,
    private var config: LauncherConfig,
    private val pickWallpaperLauncher: ActivityResultLauncher<String>,
    private val onOpenLockSetup: () -> Unit,
    private val onConfigChanged: ((LauncherConfig) -> Unit)? = null
) {
    private var _binding: LayoutPcSettingsPersonalisationBinding? = null
    val binding get() = _binding!!

    private var wallpaperAdapter: PcWallpaperAdapter? = null

    private val accentColors = listOf(
        0xFF0078D4.toInt(), // Windows Blue
        0xFF00B294.toInt(), // Mint Teal
        0xFF107C41.toInt(), // Forest Green
        0xFFD83B01.toInt(), // Sunset Orange
        0xFFE81123.toInt(), // Crimson Red
        0xFF8E8CD8.toInt(), // Lavender Purple
        0xFFB146C2.toInt(), // Orchid Magenta
        0xFFFF8C00.toInt()  // Amber Gold
    )

    fun createView(inflater: LayoutInflater): View {
        _binding = LayoutPcSettingsPersonalisationBinding.inflate(inflater, null, false)
        setupTopPreview()
        setupWallpaperRecycler()
        setupAccentColors()
        setupCards()
        return binding.root
    }

    fun updateConfig(newConfig: LauncherConfig) {
        config = newConfig
        setupTopPreview()
        wallpaperAdapter?.setSelection(config.pcWallpaper, config.pcUseCustomWallpaper)
        setupCards()
    }

    private fun setupTopPreview() {
        val b = _binding ?: return
        if (config.pcUseCustomWallpaper) {
            val file = File(context.filesDir, "wallpaper_pc.jpg")
            val fallback = File(context.filesDir, "wallpaper.jpg")
            val target = if (file.exists()) file else fallback
            if (target.exists()) {
                val bmp = BitmapFactory.decodeFile(target.absolutePath)
                if (bmp != null) {
                    b.imgHeroPreview.setImageBitmap(bmp)
                    b.tvCurrentWallpaperName.text = "Custom User Wallpaper"
                } else {
                    applyPresetPreview(b)
                }
            } else {
                applyPresetPreview(b)
            }
        } else {
            applyPresetPreview(b)
        }

        b.btnBrowseCustomWallpaper.setOnClickListener {
            pickWallpaperLauncher.launch("image/*")
        }

        b.btnResetDefaultWallpaper.setOnClickListener {
            updateConfigProperty { it.copy(pcWallpaper = 0, pcUseCustomWallpaper = false) }
            Toast.makeText(context, "Default Windows 11 wallpaper restored", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyPresetPreview(b: LayoutPcSettingsPersonalisationBinding) {
        val idx = config.pcWallpaper.coerceIn(0, PC_WALLPAPERS.size - 1)
        val preset = PC_WALLPAPERS[idx]
        b.imgHeroPreview.setImageResource(preset.resId)
        b.tvCurrentWallpaperName.text = preset.name
    }

    private fun setupWallpaperRecycler() {
        val b = _binding ?: return
        wallpaperAdapter = PcWallpaperAdapter(
            selectedId = config.pcWallpaper,
            isCustomSelected = config.pcUseCustomWallpaper
        ) { preset ->
            updateConfigProperty { it.copy(pcWallpaper = preset.id, pcUseCustomWallpaper = false) }
            Toast.makeText(context, "Applied ${preset.name}", Toast.LENGTH_SHORT).show()
        }
        b.recyclerWallpapers.adapter = wallpaperAdapter
    }

    private fun setupAccentColors() {
        val b = _binding ?: return
        val container = b.layoutAccentDots
        container.removeAllViews()

        for ((index, color) in accentColors.withIndex()) {
            val dot = View(context).apply {
                val size = 34
                val lp = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = 14
                }
                layoutParams = lp

                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    if (config.accent == index) {
                        setStroke(4, Color.WHITE)
                    }
                }
                background = drawable

                setOnClickListener {
                    updateConfigProperty { it.copy(accent = index) }
                    setupAccentColors()
                    Toast.makeText(context, "Accent colour updated", Toast.LENGTH_SHORT).show()
                }
            }
            container.addView(dot)
        }
    }

    private fun setupCards() {
        val b = _binding ?: return

        // 1. Background
        val currentPresetName = PC_WALLPAPERS.getOrNull(config.pcWallpaper)?.name ?: "Preset ${config.pcWallpaper}"
        bindCard(
            root = b.cardBackground,
            iconRes = R.drawable.ic_win_personalisation,
            title = "Background",
            subtitle = "Choose wallpaper, fit mode, and custom photos",
            value = if (config.pcUseCustomWallpaper) "Custom" else currentPresetName
        ) {
            b.recyclerWallpapers.smoothScrollToPosition(0)
            Toast.makeText(context, "Select wallpaper above or browse custom photo", Toast.LENGTH_SHORT).show()
        }

        // 2. Colours
        bindCard(
            root = b.cardColours,
            iconRes = R.drawable.ic_win_personalisation,
            title = "Colours & Transparency",
            subtitle = "Accent colours, Mica effects, and transparency",
            value = "Customize"
        ) {
            b.containerAccentColors.visibility =
                if (b.containerAccentColors.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // 3. Themes (Light / Dark Mode)
        bindCard(
            root = b.cardThemes,
            iconRes = R.drawable.ic_win_personalisation,
            title = "Windows Theme Mode",
            subtitle = if (config.pcThemeMode == 1) "Light Theme" else "Dark Theme (Mica acrylic)",
            value = if (config.pcThemeMode == 1) "Light" else "Dark"
        ) {
            val modes = listOf("Dark Theme (Default Windows 11)", "Light Theme")
            PcSettingCardHelper.showSingleChoiceDialog(
                context = context,
                title = "Theme Mode",
                options = modes,
                selectedIndex = config.pcThemeMode.coerceIn(0, 1)
            ) { selected ->
                updateConfigProperty { it.copy(pcThemeMode = selected) }
                Toast.makeText(context, "Theme set to ${modes[selected]}", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Lock Screen
        bindCard(
            root = b.cardLockScreen,
            iconRes = R.drawable.ic_win_privacy,
            title = "Lock screen",
            subtitle = if (config.deviceLock.enabled) "PIN Lock Active (${config.deviceLock.pinLength}-digit)" else "Unprotected • Click to set PIN",
            value = if (config.deviceLock.enabled) "Active" else "Off"
        ) {
            onOpenLockSetup()
        }

        // 5. Touch Controls & Virtual Gamepad
        bindCard(
            root = b.cardTouchKeyboard,
            iconRes = R.drawable.ic_win_accessibility,
            title = "Touchscreen Controls",
            subtitle = "Show on-screen touch navigation & virtual controls",
            switchChecked = config.pcTouchGamepad,
            onSwitchChanged = { isChecked ->
                updateConfigProperty { it.copy(pcTouchGamepad = isChecked) }
                Toast.makeText(context, if (isChecked) "Touch gamepad enabled" else "Touch gamepad disabled", Toast.LENGTH_SHORT).show()
            }
        )

        // 6. Start Menu Style
        bindCard(
            root = b.cardStart,
            iconRes = R.drawable.ic_win_apps,
            title = "Start Menu",
            subtitle = "Layout: ${if (config.pcStartMenuClassic) "Windows 10 Classic List" else "Windows 11 Centered Grid"} • Recent files: ${if (config.pcShowRecentInStart) "On" else "Off"}",
            value = if (config.pcStartMenuClassic) "Win 10" else "Win 11"
        ) {
            showStartMenuDialog()
        }

        // 7. Taskbar Behaviours & Pins
        bindCard(
            root = b.cardTaskbar,
            iconRes = R.drawable.ic_win_system,
            title = "Taskbar Behaviours & Pins",
            subtitle = "Alignment: ${if (config.pcTaskbarCenter) "Center" else "Left"} • Height: ${config.pcTaskbarHeight}dp • Search: ${if (config.pcShowTaskbarSearch) "Visible" else "Hidden"}",
            value = if (config.pcTaskbarCenter) "Center" else "Left"
        ) {
            showTaskbarDialog()
        }

        // 8. Fonts
        bindCard(
            root = b.cardFonts,
            iconRes = R.drawable.ic_win_personalisation,
            title = "Fonts & Typography",
            subtitle = "Segoe UI Variable • Subpixel glyph rendering",
            value = "Segoe UI"
        ) {
            PcSettingCardHelper.showInfoDialog(
                context = context,
                title = "Installed Typography",
                message = "Font Family: Segoe UI Variable\nWeights: Regular, Medium, SemiBold, Bold\nRendering: Native Skia Vector Rendering\n\nOptimized for high-DPI displays and desktop clarity."
            )
        }

        // 9. Desktop System Icons
        bindCard(
            root = b.cardDeviceUsage,
            iconRes = R.drawable.ic_win_system,
            title = "Desktop System Icons",
            subtitle = "Show This PC, Recycle Bin, and File Explorer on desktop",
            switchChecked = config.pcShowSystemIconsOnDesktop,
            onSwitchChanged = { isChecked ->
                updateConfigProperty { it.copy(pcShowSystemIconsOnDesktop = isChecked) }
                Toast.makeText(context, if (isChecked) "System icons visible on desktop" else "System icons hidden", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showStartMenuDialog() {
        val options = listOf(
            "Windows 11 Centered Grid Layout",
            "Windows 10 Classic List Layout"
        )
        val selectedIdx = if (config.pcStartMenuClassic) 1 else 0

        PcSettingCardHelper.showSingleChoiceDialog(
            context = context,
            title = "Start Menu Style",
            options = options,
            selectedIndex = selectedIdx
        ) { selected ->
            val classic = (selected == 1)
            updateConfigProperty { it.copy(pcStartMenuClassic = classic) }
            Toast.makeText(context, "Start menu set to ${options[selected]}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTaskbarDialog() {
        val options = listOf(
            "Taskbar Alignment: ${if (config.pcTaskbarCenter) "Left Align (Win 10)" else "Center Align (Win 11)"}",
            "Taskbar Search Box: ${if (config.pcShowTaskbarSearch) "Hide Search Box" else "Show Search Box"}",
            "Taskbar Clock Seconds: ${if (config.pcShowTaskbarClockSeconds) "Hide Seconds" else "Show Seconds (HH:mm:ss)"}",
            "Taskbar Height: Cycle Size (${config.pcTaskbarHeight}dp)"
        )

        MaterialAlertDialogBuilder(context, R.style.Theme_LiteTV_Dialog)
            .setTitle("Taskbar Customization")
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> {
                        val newCenter = !config.pcTaskbarCenter
                        updateConfigProperty { it.copy(pcTaskbarCenter = newCenter) }
                    }
                    1 -> {
                        val newSearch = !config.pcShowTaskbarSearch
                        updateConfigProperty { it.copy(pcShowTaskbarSearch = newSearch) }
                    }
                    2 -> {
                        val newSec = !config.pcShowTaskbarClockSeconds
                        updateConfigProperty { it.copy(pcShowTaskbarClockSeconds = newSec) }
                    }
                    3 -> {
                        val newHeight = when (config.pcTaskbarHeight) {
                            38 -> 44
                            44 -> 50
                            else -> 38
                        }
                        updateConfigProperty { it.copy(pcTaskbarHeight = newHeight) }
                        Toast.makeText(context, "Taskbar height set to ${newHeight}dp", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun updateConfigProperty(transform: (LauncherConfig) -> LauncherConfig) {
        scope.launch {
            val updated = transform(config)
            ConfigStore(context).update { updated }
            config = updated
            onConfigChanged?.invoke(updated)
            setupTopPreview()
            wallpaperAdapter?.setSelection(updated.pcWallpaper, updated.pcUseCustomWallpaper)
            setupCards()
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
}
