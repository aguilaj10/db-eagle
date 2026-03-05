package com.dbeagle.di

import org.koin.dsl.module

/**
 * Application module for Koin dependency injection.
 * Combines all sub-modules (core, data) for top-level wiring.
 * Main entry point for Koin configuration.
 */
val appModule =
    module {
        includes(coreModule, dataModule, viewModelModule)
    }
