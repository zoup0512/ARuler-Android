package com.aruler.android

import android.os.Bundle
import android.widget.TextView
import com.aruler.android.ui.makeUnderlined

class FaqActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faq)
        setupPage(getString(R.string.faq_title))

        findViewById<TextView>(R.id.more_info_button).apply {
            makeUnderlined()
            setOnClickListener {
                WebViewActivity.start(
                    this@FaqActivity,
                    Links.GITHUB_ISSUES,
                    getString(R.string.feedback_title),
                )
            }
        }
    }
}
