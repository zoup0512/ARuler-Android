package com.aruler.android

import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActivityInflationTest {

    @Test
    fun mainActivityInflatesAllControls() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()

        assertNotNull(activity.findViewById(R.id.surface_view))
        assertNotNull(activity.findViewById(R.id.distance_label))
        assertNotNull(activity.findViewById(R.id.indicator))
        assertNotNull(activity.findViewById(R.id.place_button))
        assertNotNull(activity.findViewById(R.id.trash_button))
        assertNotNull(activity.findViewById(R.id.reset_button))
        assertNotNull(activity.findViewById(R.id.coach_overlay))
        assertNotNull(activity.findViewById(R.id.hud))
    }

    @Test
    fun settingsActivityShowsRowsAndUnits() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).create().get()

        assertNotNull(activity.findViewById(R.id.unit_control))
        val guideRow = activity.findViewById<android.view.View>(R.id.row_guide)
        assertEquals(
            "新手教程",
            guideRow.findViewById<TextView>(R.id.row_title).text.toString(),
        )

        val control = activity.findViewById<com.aruler.android.ui.UnitSegmentedControl>(
            R.id.unit_control,
        )
        control.setUnit(LengthUnit.METER)
        assertEquals(LengthUnit.METER, control.getUnit())
    }

    @Test
    fun guideActivityBuildsStepsAndLegend() {
        val activity = Robolectric.buildActivity(GuideActivity::class.java).create().get()

        val steps = activity.findViewById<LinearLayout>(R.id.guide_steps)
        assertEquals(6, steps.childCount)

        val legend = activity.findViewById<android.view.View>(R.id.legend_add)
        assertNotNull(legend)
        val icon = legend.findViewById<ImageView>(R.id.legend_icon)
        assertNotNull(icon.drawable)
    }

    @Test
    fun faqAboutSupportAndLicenseInflate() {
        Robolectric.buildActivity(FaqActivity::class.java).create().get()
        val about = Robolectric.buildActivity(AboutActivity::class.java).create().get()
        assertEquals(
            "ARuler v${BuildConfig.VERSION_NAME}",
            about.findViewById<TextView>(R.id.version_label).text.toString(),
        )
        Robolectric.buildActivity(SupportActivity::class.java).create().get()

        val license = Robolectric.buildActivity(LicenseActivity::class.java).create().get()
        val container = license.findViewById<LinearLayout>(R.id.license_container)
        assertTrue(container.childCount > 0)
    }

    @Test
    fun donateActivityShowsBothQrCodes() {
        val activity = Robolectric.buildActivity(DonateActivity::class.java).create().get()

        val wechat = activity.findViewById<ImageView>(R.id.wechat_qr)
        val alipay = activity.findViewById<ImageView>(R.id.alipay_qr)
        assertNotNull(wechat.drawable)
        assertNotNull(alipay.drawable)
    }
}
