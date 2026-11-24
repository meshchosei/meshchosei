package com.meshcore.repeater.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidatorTest {
    @Test
    fun privateKeyMustBeExactly128HexChars() {
        assertTrue(InputValidator.isValidPrivateKeyHex("a1".repeat(64)))
        assertTrue(InputValidator.isValidPrivateKeyHex("  " + "AB".repeat(64) + " "))
        assertFalse(InputValidator.isValidPrivateKeyHex("a1".repeat(32)))   // 64 Zeichen = nur 32 Byte
        assertFalse(InputValidator.isValidPrivateKeyHex("a1".repeat(65)))
        assertFalse(InputValidator.isValidPrivateKeyHex("zz" + "a1".repeat(63)))
    }

    @Test
    fun coordinatesAreRangeChecked() {
        assertTrue(InputValidator.isValidLatitude("51.881"))
        assertTrue(InputValidator.isValidLatitude("-90"))
        assertTrue(InputValidator.isValidLatitude("51,881"))
        assertFalse(InputValidator.isValidLatitude("90.5"))
        assertFalse(InputValidator.isValidLatitude("abc"))
        assertFalse(InputValidator.isValidLatitude("NaN"))
        assertTrue(InputValidator.isValidLongitude("-179.9"))
        assertFalse(InputValidator.isValidLongitude("181"))
    }

    @Test
    fun txLimitFollowsBoardProfile() {
        assertTrue(InputValidator.validateTxPowerDbm(22.0, 30.0).isOk)
        assertTrue(InputValidator.validateTxPowerDbm(-9.0, 30.0).isOk)
        assertTrue(InputValidator.validateTxPowerDbm(-10.0, 30.0).isError)
        assertTrue(InputValidator.validateTxPowerDbm(31.0, 30.0).isError)
        assertTrue(InputValidator.validateTxPowerDbm(35.0, 35.0).isOk)        // Station G2 (PA)
    }
}
