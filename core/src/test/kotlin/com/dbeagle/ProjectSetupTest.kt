package com.dbeagle

import kotlin.test.Test
import kotlin.test.assertTrue

class ProjectSetupTest {
    @Test
    fun testCoreModuleInitializes() {
        // Smoke test: verify core module can be initialized
        CoreModule.initialize()
        assertTrue(true)
    }
}
