package com.dbeagle.di

import com.dbeagle.session.SessionViewModel
import com.dbeagle.theme.ThemeManager
import com.dbeagle.viewmodel.ConnectionListViewModel
import com.dbeagle.viewmodel.FavoritesViewModel
import com.dbeagle.viewmodel.HistoryViewModel
import com.dbeagle.viewmodel.IndexEditorViewModel
import com.dbeagle.viewmodel.LogViewerViewModel
import com.dbeagle.viewmodel.QueryEditorViewModel
import com.dbeagle.viewmodel.SchemaBrowserViewModel
import com.dbeagle.viewmodel.SequenceEditorViewModel
import com.dbeagle.viewmodel.SettingsViewModel
import com.dbeagle.viewmodel.TableDataEditorViewModel
import com.dbeagle.viewmodel.TableEditorViewModel
import com.dbeagle.viewmodel.ViewEditorViewModel
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
        factory { IndexEditorViewModel() }
        factory { (existingTable: com.dbeagle.ddl.TableDefinition?, existingIndexes: List<com.dbeagle.ddl.IndexDefinition>, allTables: List<String>, databaseType: String?) ->
            TableEditorViewModel(existingTable, existingIndexes, allTables, databaseType)
        }
        factory { (dialect: com.dbeagle.ddl.DDLDialect) ->
            ViewEditorViewModel(dialect)
        }
        factory { (existingSequence: com.dbeagle.model.SequenceMetadata?) ->
            SequenceEditorViewModel(existingSequence)
        }
    }
