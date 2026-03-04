package com.dbeagle.test

import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * Base test class providing common lifecycle management for all tests.
 * Subclasses can override beforeTest() and afterTest() for custom initialization.
 */
open class BaseTest {
    @BeforeTest
    open fun beforeTest() {
        // Override in subclasses for custom setup
    }

    @AfterTest
    open fun afterTest() {
        // Override in subclasses for custom teardown
    }
}
