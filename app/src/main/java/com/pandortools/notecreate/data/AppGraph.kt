package com.pandortools.notecreate.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow

/** Простой контейнер зависимостей без DI-фреймворка. */
object AppGraph {
    lateinit var repository: NotebookRepository
        private set
    lateinit var settings: AppSettings
        private set

    /** Картинка, пришедшая через «Поделиться» — вставится в открытую тетрадь. */
    val pendingSharedImage = MutableStateFlow<Uri?>(null)

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        repository = NotebookRepository(context)
        settings = AppSettings(context)
        initialized = true
    }
}
