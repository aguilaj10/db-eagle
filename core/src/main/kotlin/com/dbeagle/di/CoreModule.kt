package com.dbeagle.di

import com.dbeagle.crypto.CredentialEncryption
import com.dbeagle.profile.ConnectionProfileRepository
import com.dbeagle.profile.PreferencesBackedConnectionProfileRepository
import com.dbeagle.settings.AppPreferencesRepository
import com.dbeagle.settings.SettingsProvider
import org.koin.dsl.module

val coreModule =
    module {
        single { CredentialEncryption }
        single { AppPreferencesRepository(SettingsProvider.createAppSettings()) }
        factory<ConnectionProfileRepository> { (masterPassword: String) ->
            PreferencesBackedConnectionProfileRepository(
                masterPasswordProvider = { masterPassword },
                settings = SettingsProvider.createProfileSettings(),
            )
        }
    }
