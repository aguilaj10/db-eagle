package com.dbeagle.di

import com.dbeagle.session.SessionViewModel
import com.dbeagle.theme.ThemeManager
import com.dbeagle.viewmodel.ConnectionListViewModel
import com.dbeagle.viewmodel.FavoritesViewModel
import com.dbeagle.viewmodel.HistoryViewModel
import com.dbeagle.viewmodel.LogViewerViewModel
import com.dbeagle.viewmodel.QueryEditorViewModel
import com.dbeagle.viewmodel.SchemaBrowserViewModel
import com.dbeagle.viewmodel.SettingsViewModel
import com.dbeagle.viewmodel.TableDataEditorViewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

val viewModelModule =
    module {
        single { ThemeManager(get()) }
        single { SessionViewModel() }
        factory { (masterPassword: String) ->
            ConnectionListViewModel(get { parametersOf(masterPassword) })
        }
        factory { QueryEditorViewModel(get()) }
        factory { SettingsViewModel(get(), get()) }
        factory { HistoryViewModel(get()) }
        factory { FavoritesViewModel(get()) }
        factory { SchemaBrowserViewModel() }
        factory { LogViewerViewModel() }
        factory { TableDataEditorViewModel() }
    }
