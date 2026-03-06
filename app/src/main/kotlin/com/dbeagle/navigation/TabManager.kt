package com.dbeagle.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

class TabManager {
    private val _tabs: SnapshotStateList<TabItem> = mutableStateListOf()
    val tabs: List<TabItem> get() = _tabs

    var selectedTabId: String? by mutableStateOf(null)
        private set

    val selectedTab: TabItem?
        get() = selectedTabId?.let { id -> _tabs.find { it.id == id } }

    fun addTab(tab: TabItem) {
        if (_tabs.none { it.id == tab.id }) {
            _tabs.add(tab)
            selectedTabId = tab.id
        }
    }

    fun closeTab(tabId: String) {
        val index = _tabs.indexOfFirst { it.id == tabId }
        if (index == -1) return

        val wasSelected = selectedTabId == tabId
        _tabs.removeAt(index)

        if (wasSelected && _tabs.isNotEmpty()) {
            selectedTabId =
                if (index < _tabs.size) {
                    _tabs[index].id
                } else {
                    _tabs.lastOrNull()?.id
                }
        } else if (_tabs.isEmpty()) {
            selectedTabId = null
        }
    }

    fun selectTab(tabId: String) {
        if (_tabs.any { it.id == tabId }) {
            selectedTabId = tabId
        }
    }

    fun moveTab(
        fromIndex: Int,
        toIndex: Int,
    ) {
        if (fromIndex !in _tabs.indices || toIndex !in _tabs.indices) return
        if (fromIndex == toIndex) return

        val tab = _tabs.removeAt(fromIndex)
        _tabs.add(toIndex, tab)
    }
}
