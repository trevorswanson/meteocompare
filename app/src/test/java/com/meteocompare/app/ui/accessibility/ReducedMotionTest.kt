package com.meteocompare.app.ui.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReducedMotionTest {
    @Test
    fun zero_animator_scale_requests_reduced_motion() {
        assertTrue(isReducedMotion(0f))
    }

    @Test
    fun normal_or_slowed_scales_keep_animations() {
        assertFalse(isReducedMotion(1f))
        assertFalse(isReducedMotion(0.5f))
        assertFalse(isReducedMotion(10f))
    }

    @Test
    fun missing_or_invalid_scale_never_disables_animations() {
        assertFalse(isReducedMotion(null))
        assertFalse(isReducedMotion(Float.NaN))
    }
}
