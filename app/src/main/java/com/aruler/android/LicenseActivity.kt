package com.aruler.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.aruler.android.ui.dpInt

class LicenseActivity : BaseActivity() {

    private data class LicenseEntry(val titleRes: Int, val url: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license)
        setupPage(getString(R.string.license_title))

        val entries = listOf(
            LicenseEntry(R.string.license_aruler_ios, Links.GITHUB_REPO),
            LicenseEntry(R.string.license_arcore, Links.ARCORE_SDK),
            LicenseEntry(R.string.license_androidx, Links.ANDROIDX),
            LicenseEntry(R.string.license_material, Links.MATERIAL_COMPONENTS),
            LicenseEntry(R.string.license_opengl, Links.OPENGL_ES),
            LicenseEntry(R.string.license_gpl, Links.GPL_V2),
        )

        val container = findViewById<LinearLayout>(R.id.license_container)
        val inflater = LayoutInflater.from(this)
        val dividerHeight = dpInt(0.5f).coerceAtLeast(1)

        entries.forEachIndexed { index, entry ->
            val row = inflater.inflate(R.layout.view_plain_row, container, false)
            row.findViewById<TextView>(R.id.row_title).setText(entry.titleRes)
            row.setOnClickListener {
                WebViewActivity.start(this, entry.url, getString(entry.titleRes))
            }
            container.addView(row)

            if (index != entries.lastIndex) {
                val divider = View(this).apply {
                    setBackgroundColor(ContextCompat.getColor(this@LicenseActivity, R.color.divider))
                }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dividerHeight,
                ).apply {
                    marginStart = dpInt(16f)
                }
                container.addView(divider, params)
            }
        }
    }
}
