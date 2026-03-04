package com.dbeagle.di

import com.dbeagle.crypto.CredentialEncryption
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KoinModuleTest {
    @Test
    fun `should start Koin with coreModule successfully`() {
        startKoin {
            modules(coreModule)
        }
        try {
            val resolved = GlobalContext.get().get<CredentialEncryption>()
            assertNotNull(resolved)
        } finally {
            stopKoin()
        }
    }

    @Test
    fun `should resolve CredentialEncryption from coreModule`() {
        startKoin {
            modules(coreModule)
        }
        try {
            val resolved = GlobalContext.get().get<CredentialEncryption>()
            assertNotNull(resolved)
            assertEquals(CredentialEncryption, resolved)
        } finally {
            stopKoin()
        }
    }

    @Test
    fun `should provide singleton CredentialEncryption instance`() {
        startKoin {
            modules(coreModule)
        }
        try {
            val first = GlobalContext.get().get<CredentialEncryption>()
            val second = GlobalContext.get().get<CredentialEncryption>()
            assertEquals(first, second)
        } finally {
            stopKoin()
        }
    }

    @Test
    fun `should resolve all DI bindings without errors`() {
        startKoin {
            modules(coreModule)
        }
        try {
            val credentialEncryption = GlobalContext.get().get<CredentialEncryption>()
            assertNotNull(credentialEncryption)
        } finally {
            stopKoin()
        }
    }
}
