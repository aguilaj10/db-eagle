package com.dbeagle.di

import com.dbeagle.crypto.CredentialEncryption
import org.koin.dsl.module

/**
 * Core module for Koin dependency injection.
 * Provides bindings for core business logic and services.
 */
val coreModule =
    module {
        single { CredentialEncryption }
    }
