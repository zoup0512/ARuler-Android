package com.aruler.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.View.MeasureSpec
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.aruler.android.ui.CoachMarkOverlayView
import com.aruler.android.ui.MeasurementLabelView
import com.aruler.android.ui.ScreenLabel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotPreviewTest {

    @Test
    fun capturePagePreviews() {
        val output = File("build/reports/preview").apply { mkdirs() }

        val main = Robolectric.buildActivity(MainActivity::class.java).create().start().visible().get()
        main.findViewById<AppCompatTextView>(R.id.distance_label).text = "44.9cm"
        main.findViewById<AppCompatTextView>(R.id.distance_label).setTextColor(Color.parseColor("#1F1F1F"))
        main.findViewById<ImageButton>(R.id.place_button).apply {
            isEnabled = true
            alpha = 1f
            setImageResource(R.drawable.ic_check_place)
        }
        main.findViewById<AppCompatImageView>(R.id.indicator).apply {
            imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#95D26B"))
        }
        main.findViewById<MeasurementLabelView>(R.id.label_overlay).setLabels(
            listOf(ScreenLabel(560f, 1240f, "44.90cm")),
        )
        capture(main, output, "main.png")

        val coach = main.findViewById<CoachMarkOverlayView>(R.id.coach_overlay)
        coach.start(
            listOf(
                CoachMarkOverlayView.Step(
                    "将指示器的圆心对准要测量的起点",
                    main.findViewById(R.id.indicator),
                    CoachMarkOverlayView.Shape.CIRCLE,
                ),
            ),
        ) {}
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)
        coach.alpha = 1f
        capture(main, output, "coach_target.png")

        capture(Robolectric.buildActivity(SettingsActivity::class.java).create().get(), output, "settings.png")
        capture(Robolectric.buildActivity(GuideActivity::class.java).create().get(), output, "guide.png")
        capture(Robolectric.buildActivity(FaqActivity::class.java).create().get(), output, "faq.png")
        capture(Robolectric.buildActivity(AboutActivity::class.java).create().get(), output, "about.png")
        capture(Robolectric.buildActivity(SupportActivity::class.java).create().get(), output, "support.png")
        capture(Robolectric.buildActivity(DonateActivity::class.java).create().get(), output, "donate.png")
        capture(Robolectric.buildActivity(LicenseActivity::class.java).create().get(), output, "license.png")
    }

    private fun capture(activity: AppCompatActivity, output: File, name: String) {
        val root = activity.findViewById<View>(android.R.id.content)
        val width = 1080
        val height = 2280
        root.forceLayout()
        root.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, width, height)
        root.translationY = 0f

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        root.draw(canvas)

        FileOutputStream(File(output, name)).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}
