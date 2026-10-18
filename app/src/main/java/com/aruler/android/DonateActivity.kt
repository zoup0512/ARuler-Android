package com.aruler.android

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.io.File
import java.io.FileOutputStream

class DonateActivity : BaseActivity() {

    private var pendingQr: Pair<Int, String>? = null

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingQr
        pendingQr = null
        if (granted && pending != null) {
            saveToGallery(pending.first, pending.second)
        } else if (!granted) {
            Toast.makeText(this, R.string.qr_permission_needed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_donate)
        setupPage(getString(R.string.donate_title))

        val wechatQr = findViewById<ImageView>(R.id.wechat_qr)
        val alipayQr = findViewById<ImageView>(R.id.alipay_qr)
        val wechatButton = findViewById<MaterialButton>(R.id.wechat_button)
        val alipayButton = findViewById<MaterialButton>(R.id.alipay_button)

        wechatQr.setOnClickListener { requestSave(R.drawable.qr_wechat, "aruler_wechat_qr") }
        wechatQr.setOnLongClickListener {
            requestSave(R.drawable.qr_wechat, "aruler_wechat_qr")
            true
        }
        alipayQr.setOnClickListener { requestSave(R.drawable.qr_alipay, "aruler_alipay_qr") }
        alipayQr.setOnLongClickListener {
            requestSave(R.drawable.qr_alipay, "aruler_alipay_qr")
            true
        }

        wechatButton.setOnClickListener {
            requestSave(R.drawable.qr_wechat, "aruler_wechat_qr")
        }
        alipayButton.setOnClickListener {
            requestSave(R.drawable.qr_alipay, "aruler_alipay_qr")
        }
    }

    private fun requestSave(resId: Int, fileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToGallery(resId, fileName)
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            saveToGallery(resId, fileName)
        } else {
            pendingQr = resId to fileName
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun saveToGallery(resId: Int, fileName: String) {
        Thread {
            val success = try {
                val bitmap = BitmapFactory.decodeResource(resources, resId)
                if (bitmap == null) {
                    false
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveWithMediaStore(bitmap, fileName)
                } else {
                    saveToLegacyStorage(bitmap, fileName)
                }
            } catch (_: Exception) {
                false
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                Toast.makeText(
                    this,
                    if (success) R.string.qr_saved else R.string.qr_save_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }.start()
    }

    private fun saveWithMediaStore(bitmap: Bitmap, fileName: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/ARuler",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false

        var success = false
        try {
            contentResolver.openOutputStream(uri)?.use { stream ->
                success = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            }
            if (success) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            }
        } finally {
            if (!success) {
                contentResolver.delete(uri, null, null)
            }
        }
        return success
    }

    @Suppress("DEPRECATION")
    private fun saveToLegacyStorage(bitmap: Bitmap, fileName: String): Boolean {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "ARuler",
        )
        if (!directory.exists() && !directory.mkdirs()) return false

        val file = File(directory, "$fileName.jpg")
        val success = try {
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            }
        } catch (_: Exception) {
            false
        }

        if (success) {
            MediaScannerConnection.scanFile(
                this,
                arrayOf(file.absolutePath),
                arrayOf("image/jpeg"),
                null,
            )
        }
        return success
    }
}
