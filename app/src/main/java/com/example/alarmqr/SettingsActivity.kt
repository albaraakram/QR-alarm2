package com.example.alarmqr

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.title = "الإعدادات"

        val themeGroup: RadioGroup = findViewById(R.id.themeGroup)
        val langSpinner: Spinner = findViewById(R.id.langSpinner)
        val applyBtn: Button = findViewById(R.id.applyBtn)

        // Preselect theme
        val prefs = getSharedPreferences("alarmqr", MODE_PRIVATE)
        when (prefs.getString(KEY_THEME_MODE, MODE_SYSTEM)) {
            MODE_LIGHT -> themeGroup.check(R.id.themeLight)
            MODE_DARK -> themeGroup.check(R.id.themeDark)
            else -> themeGroup.check(R.id.themeSystem)
        }

        // Language list
        val names = resources.getStringArray(R.array.lang_names)
        val codes = resources.getStringArray(R.array.lang_codes)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        langSpinner.adapter = adapter
        val savedCode = prefs.getString(KEY_LANG, "ar")
        val idx = codes.indexOf(savedCode)
        if (idx >= 0) langSpinner.setSelection(idx)

        applyBtn.setOnClickListener {
            val mode = when (themeGroup.checkedRadioButtonId) {
                R.id.themeLight -> MODE_LIGHT
                R.id.themeDark -> MODE_DARK
                else -> MODE_SYSTEM
            }
            prefs.edit().putString(KEY_THEME_MODE, mode).apply()
            when (mode) {
                MODE_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                MODE_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }

            val sel = langSpinner.selectedItemPosition
            val code = if (sel in codes.indices) codes[sel] else "ar"
            prefs.edit().putString(KEY_LANG, code).apply()
            val locales = LocaleListCompat.forLanguageTags(code)
            AppCompatDelegate.setApplicationLocales(locales)

            recreate()
        }
    }

    companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val MODE_SYSTEM = "system"
        const val MODE_LIGHT = "light"
        const val MODE_DARK = "dark"
        const val KEY_LANG = "lang_code"
    }
}

