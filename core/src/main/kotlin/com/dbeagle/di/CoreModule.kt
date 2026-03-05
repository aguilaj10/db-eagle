package com.dbeagle.di

import com.dbeagle.crypto.CredentialEncryption
import com.dbeagle.profile.ConnectionProfileRepository
import com.dbeagle.profile.PreferencesBackedConnectionProfileRepository
import com.dbeagle.settings.SettingsProvider
import org.koin.dsl.module

val coreModule =
    module {
        single { CredentialEncryption }
        factory<ConnectionProfileRepository> { (masterPassword: String) ->
            PreferencesBackedConnectionProfileRepository(
                masterPasswordProvider = { masterPassword },
                settings = SettingsProvider.createProfileSettings(),
            )
        }
    }
