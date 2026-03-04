package com.dbeagle

import kotlin.test.Test
import kotlin.test.assertTrue

class DataModuleTest {
    @Test
    fun testDataModuleInitializes() {
        DataModule.initialize()
        assertTrue(true)
    }
}
