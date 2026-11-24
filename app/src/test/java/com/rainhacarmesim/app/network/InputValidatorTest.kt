package com.rainhacarmesim.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputValidatorTest {
    @Test fun acceptsValidTargets() {
        assertEquals("192.168.0.1", InputValidator.normalizeTarget(" 192.168.0.1 "))
        assertEquals("google.com", InputValidator.normalizeTarget("google.com."))
        assertEquals("router-1.lan", InputValidator.normalizeTarget("router-1.lan"))
        assertEquals("fe80::1", InputValidator.normalizeTarget("fe80::1"))
    }

    @Test fun rejectsInvalidTargets() {
        assertNull(InputValidator.normalizeTarget(""))
        assertNull(InputValidator.normalizeTarget("-c 1000 host"))
        assertNull(InputValidator.normalizeTarget("999.1.1.1"))
        assertNull(InputValidator.normalizeTarget("host name"))
        assertNull(InputValidator.normalizeTarget("a;rm -rf"))
        assertNull(InputValidator.normalizeTarget("-host"))
    }
}
