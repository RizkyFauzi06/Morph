package com.morph.app

import com.morph.app.core.UpdateChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun testVersionComparison() {
        assertTrue(UpdateChecker.isNewerVersion("v1.0.1", "1.0.0"))
        assertTrue(UpdateChecker.isNewerVersion("v1.1.0", "1.0.1"))
        assertFalse(UpdateChecker.isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewerVersion("v1.0.0", "1.0.1"))
    }
}