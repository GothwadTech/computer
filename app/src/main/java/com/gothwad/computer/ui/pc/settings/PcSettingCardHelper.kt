package com.gothwad.computer.ui.pc.settings

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gothwad.computer.R
import com.gothwad.computer.databinding.ItemPcSettingCardBinding

/**
 * Common card and dialog helper for Windows 11 PC Settings pages.
 */
object PcSettingCardHelper {

    fun bindCard(
        binding: ItemPcSettingCardBinding,
        @DrawableRes iconRes: Int,
        title: String,
        subtitle: String? = null,
        value: String? = null,
        switchChecked: Boolean? = null,
        onSwitchChanged: ((Boolean) -> Unit)? = null,
        onClick: (() -> Unit)? = null
    ) {
        binding.cardRowIcon.setImageResource(iconRes)
        binding.cardRowTitle.text = title

        if (!subtitle.isNullOrEmpty()) {
            binding.cardRowSubtitle.visibility = View.VISIBLE
            binding.cardRowSubtitle.text = subtitle
        } else {
            binding.cardRowSubtitle.visibility = View.GONE
        }

        if (!value.isNullOrEmpty()) {
            binding.cardRowValue.visibility = View.VISIBLE
            binding.cardRowValue.text = value
        } else {
            binding.cardRowValue.visibility = View.GONE
        }

        if (onSwitchChanged != null) {
            binding.cardRowSwitch.visibility = View.VISIBLE
            binding.cardRowChevron.visibility = View.GONE
            // Remove listener before setting checked state to avoid unwanted triggers
            binding.cardRowSwitch.setOnCheckedChangeListener(null)
            binding.cardRowSwitch.isChecked = switchChecked == true
            binding.cardRowSwitch.setOnCheckedChangeListener { _, isChecked ->
                onSwitchChanged(isChecked)
            }
            binding.cardRowRoot.setOnClickListener {
                binding.cardRowSwitch.toggle()
            }
        } else if (onClick != null) {
            binding.cardRowSwitch.visibility = View.GONE
            binding.cardRowChevron.visibility = View.VISIBLE
            binding.cardRowRoot.setOnClickListener { onClick() }
        } else {
            binding.cardRowSwitch.visibility = View.GONE
            binding.cardRowChevron.visibility = View.GONE
            binding.cardRowRoot.setOnClickListener(null)
            binding.cardRowRoot.isClickable = false
        }
    }

    fun addCard(
        parent: ViewGroup,
        context: Context,
        @DrawableRes iconRes: Int,
        title: String,
        subtitle: String? = null,
        value: String? = null,
        switchChecked: Boolean? = null,
        onSwitchChanged: ((Boolean) -> Unit)? = null,
        onClick: (() -> Unit)? = null
    ): ItemPcSettingCardBinding {
        val binding = ItemPcSettingCardBinding.inflate(LayoutInflater.from(context), parent, false)
        bindCard(binding, iconRes, title, subtitle, value, switchChecked, onSwitchChanged, onClick)
        parent.addView(binding.root)
        return binding
    }

    fun showSingleChoiceDialog(
        context: Context,
        title: String,
        options: List<String>,
        selectedIndex: Int,
        onSelected: (Int) -> Unit
    ) {
        MaterialAlertDialogBuilder(context, R.style.Theme_LiteTV_Dialog)
            .setTitle(title)
            .setSingleChoiceItems(options.toTypedArray(), selectedIndex) { dialog, which ->
                onSelected(which)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showInputDialog(
        context: Context,
        title: String,
        currentValue: String,
        hint: String,
        onConfirm: (String) -> Unit
    ) {
        val editText = EditText(context).apply {
            setText(currentValue)
            setHint(hint)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#8E95A5"))
            setBackgroundResource(R.drawable.bg_win11_search_box)
            setPadding(36, 24, 36, 24)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(48, 24, 48, 24)
            }
            layoutParams = lp
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(editText)
        }

        MaterialAlertDialogBuilder(context, R.style.Theme_LiteTV_Dialog)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val text = editText.text.toString().trim()
                if (text.isNotEmpty()) {
                    onConfirm(text)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showConfirmDialog(
        context: Context,
        title: String,
        message: String,
        positiveButton: String = "Confirm",
        onConfirm: () -> Unit
    ) {
        MaterialAlertDialogBuilder(context, R.style.Theme_LiteTV_Dialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButton) { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showInfoDialog(
        context: Context,
        title: String,
        message: String
    ) {
        MaterialAlertDialogBuilder(context, R.style.Theme_LiteTV_Dialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
