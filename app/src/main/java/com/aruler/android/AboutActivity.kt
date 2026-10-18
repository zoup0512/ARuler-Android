package com.aruler.android

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.aruler.android.ui.makeUnderlined

class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        setupPage(getString(R.string.about_title))

        findViewById<TextView>(R.id.version_label).text = getString(
            R.string.about_version,
            BuildConfig.VERSION_NAME,
        )

        findViewById<TextView>(R.id.support_link).apply {
            makeUnderlined()
            setOnClickListener {
                startActivity(Intent(this@AboutActivity, SupportActivity::class.java))
            }
        }

        findViewById<TextView>(R.id.license_link).apply {
            makeUnderlined()
            setOnClickListener {
                startActivity(Intent(this@AboutActivity, LicenseActivity::class.java))
            }
        }
    }
}
