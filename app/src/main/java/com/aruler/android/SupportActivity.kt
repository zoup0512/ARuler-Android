package com.aruler.android

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast

class SupportActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_support)
        setupPage(getString(R.string.support_title))

        bindRow(R.id.row_review, R.string.support_review) { openStore() }
        bindRow(R.id.row_share, R.string.support_share) { shareApp() }
        bindRow(R.id.row_star, R.string.support_star) {
            WebViewActivity.start(this, Links.GITHUB_REPO, "GitHub")
        }
        bindRow(R.id.row_donate, R.string.support_donate) {
            startActivity(Intent(this, DonateActivity::class.java))
        }
    }

    private fun bindRow(rowId: Int, titleRes: Int, action: () -> Unit) {
        val row = findViewById<View>(rowId)
        row.findViewById<TextView>(R.id.row_title).setText(titleRes)
        row.setOnClickListener { action() }
    }

    private fun openStore() {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageName"),
        )
        try {
            startActivity(marketIntent)
            return
        } catch (_: ActivityNotFoundException) {
            // Fall through to the web listing.
        }

        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
        )
        try {
            startActivity(webIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.store_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareApp() {
        val message = getString(R.string.share_message, Links.GITHUB_REPO)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        startActivity(Intent.createChooser(sendIntent, getString(R.string.share_title)))
    }
}
