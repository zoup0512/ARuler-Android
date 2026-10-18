package com.aruler.android

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.aruler.android.ui.UnitSegmentedControl

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setupPage(getString(R.string.settings_title))

        val unitControl = findViewById<UnitSegmentedControl>(R.id.unit_control)
        unitControl.setUnit(Prefs.lengthUnit(this))
        unitControl.onUnitChanged = { unit -> Prefs.setLengthUnit(this, unit) }

        bindRow(R.id.row_guide, R.string.settings_tutorial) {
            startActivity(Intent(this, GuideActivity::class.java))
        }
        bindRow(R.id.row_faq, R.string.settings_faq) {
            startActivity(Intent(this, FaqActivity::class.java))
        }
        bindRow(R.id.row_support, R.string.settings_support) {
            startActivity(Intent(this, SupportActivity::class.java))
        }
        bindRow(R.id.row_feedback, R.string.settings_feedback) {
            WebViewActivity.start(
                this,
                Links.GITHUB_ISSUES,
                getString(R.string.feedback_title),
            )
        }
        bindRow(R.id.row_about, R.string.settings_about) {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun bindRow(rowId: Int, titleRes: Int, action: () -> Unit) {
        val row = findViewById<View>(rowId)
        row.findViewById<TextView>(R.id.row_title).setText(titleRes)
        row.setOnClickListener { action() }
    }
}
