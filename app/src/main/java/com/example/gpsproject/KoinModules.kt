package com.example.gpsproject

import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    viewModel {
        ActivityViewModel(
            app = androidApplication()
        )
    }
}

val modules = listOf(
    appModule,
    homeModule
)
