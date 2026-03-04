package com.dbeagle

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.dbeagle.di.appModule
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(appModule)
    }
    application {
        Window(onCloseRequest = ::exitApplication, title = "DB Eagle") {
            Column(modifier = Modifier.fillMaxSize()) {
                Text("Welcome to DB Eagle")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Multi-module Kotlin/Compose Desktop Application")
            }
        }
    }
}
