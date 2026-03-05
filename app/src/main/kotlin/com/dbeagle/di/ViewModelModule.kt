package com.dbeagle.di

import com.dbeagle.viewmodel.ConnectionListViewModel
import com.dbeagle.viewmodel.QueryEditorViewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

val viewModelModule =
    module {
        factory { (masterPassword: String) ->
            ConnectionListViewModel(get { parametersOf(masterPassword) })
        }
        factory { QueryEditorViewModel(get()) }
    }
