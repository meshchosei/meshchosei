package com.meshcore.repeater.ui

import com.meshcore.repeater.ui.components.DropdownHeightRules
import org.junit.Assert.assertEquals
import org.junit.Test

/** v1.27: Repeater-Klappmenü reicht bis knapp vor den unteren Bildschirmrand. */
class DropdownHeightRulesTest {
    @Test fun largePhoneUsesSpaceDownToTheBottomEdge() {
        // 2400 px Fenster, Dichte 3, Liste beginnt bei 900 px, Navigationsleiste 144 px, 16 dp Abstand
        // (2400 - 900 - 144 - 48) / 3 = 436 dp – statt der früheren festen 200/240 dp
        assertEquals(436f, DropdownHeightRules.maxHeightDp(2400, 900f, 144, 3f), 0.01f)
    }

    @Test fun keyboardOrLittleSpaceKeepsAMinimum() {
        assertEquals(DropdownHeightRules.MIN_DP, DropdownHeightRules.maxHeightDp(2400, 1500f, 900, 3f), 0.01f)
        assertEquals(DropdownHeightRules.MIN_DP, DropdownHeightRules.maxHeightDp(1080, 1000f, 0, 2.75f), 0.01f)
    }

    @Test fun beforeFirstLayoutTheOldHeightIsUsed() {
        assertEquals(DropdownHeightRules.FALLBACK_DP, DropdownHeightRules.maxHeightDp(2400, null, 144, 3f), 0.01f)
        assertEquals(DropdownHeightRules.FALLBACK_DP, DropdownHeightRules.maxHeightDp(0, 100f, 0, 3f), 0.01f)
    }

    @Test fun marginIsInDp() {
        // ohne Inset: (2000 - 400 - 16*2) / 2 = 784 dp
        assertEquals(784f, DropdownHeightRules.maxHeightDp(2000, 400f, 0, 2f), 0.01f)
    }
}
