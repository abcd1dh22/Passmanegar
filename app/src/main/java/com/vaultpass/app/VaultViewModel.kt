package com.vaultpass.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.vaultpass.app.data.Entry
import com.vaultpass.app.data.VaultStore

class VaultViewModel(app: Application) : AndroidViewModel(app) {
    private val store = VaultStore.get(app)
    var entries by mutableStateOf<List<Entry>>(emptyList())
        private set

    fun load() {
        entries = runCatching { store.load() }.getOrDefault(emptyList())
            .sortedBy { it.title.lowercase() }
    }

    fun upsert(e: Entry) = persist(entries.filter { it.id != e.id } + e)

    fun delete(id: String) = persist(entries.filter { it.id != id })

    private fun persist(list: List<Entry>) {
        store.save(list)
        load()
    }
}
