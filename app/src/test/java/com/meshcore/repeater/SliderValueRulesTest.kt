package com.meshcore.repeater

import com.meshcore.repeater.ui.components.SliderValueRules
import org.junit.Assert.assertEquals
import org.junit.Test

/** v1.23: Altersgrenze 0–120 Min. im 5er-Raster (Feineinstellung bis 10080), Hops 1–15 (bis 64). */
class SliderValueRulesTest {
    @Test fun snapRoundsInsteadOfTruncating() {
        assertEquals(35, SliderValueRules.snap(34.9999f, 0, 120, 5))   // toInt() hätte 34 ergeben
        assertEquals(35, SliderValueRules.snap(36.2f, 0, 120, 5))
        assertEquals(40, SliderValueRules.snap(37.6f, 0, 120, 5))
        assertEquals(0, SliderValueRules.snap(1.9f, 0, 120, 5))
        assertEquals(120, SliderValueRules.snap(119.7f, 0, 120, 5))
        assertEquals(7, SliderValueRules.snap(6.6f, 1, 15, 1))
    }

    @Test fun snapStaysInRange() {
        assertEquals(0, SliderValueRules.snap(-3f, 0, 120, 5))
        assertEquals(120, SliderValueRules.snap(130f, 0, 120, 5))
        assertEquals(1, SliderValueRules.snap(0.2f, 1, 15, 1))
    }

    @Test fun fineAdjustsBySingleInterval() {
        assertEquals(36, SliderValueRules.fine(35, +1, 0, 10080))
        assertEquals(34, SliderValueRules.fine(35, -1, 0, 10080))
        assertEquals(0, SliderValueRules.fine(0, -1, 0, 10080))
        assertEquals("über das Slider-Ende hinaus", 121, SliderValueRules.fine(120, +1, 0, 10080))
        assertEquals(10080, SliderValueRules.fine(10080, +1, 0, 10080))
        assertEquals(64, SliderValueRules.fine(64, +1, 1, 64))
        assertEquals(1, SliderValueRules.fine(1, -1, 1, 64))
    }
}
