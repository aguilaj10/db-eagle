package com.dbeagle.di

import org.koin.dsl.module

/**
 * Data module for Koin dependency injection.
 * Provides bindings for data layer services (database drivers, repositories).
 * Placeholder for future bindings: database drivers, connection pools, persistence services.
 */
val dataModule =
    module {
        single<com.dbeagle.favorites.FavoritesRepository> { com.dbeagle.favorites.FileFavoritesRepository() }
        single<com.dbeagle.history.QueryHistoryRepository> { com.dbeagle.history.FileQueryHistoryRepository() }
    }
