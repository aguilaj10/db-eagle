package com.dbeagle.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TabManagerTest {
    @Test
    fun `addTab adds tab to list and selects it`() {
        val manager = TabManager()
        val tab = TabItem(id = "tab1", type = TabType.QueryEditor, title = "Query 1")

        manager.addTab(tab)

        assertEquals(1, manager.tabs.size)
        assertEquals("tab1", manager.tabs[0].id)
        assertEquals("tab1", manager.selectedTabId)
        assertEquals(tab, manager.selectedTab)
    }

    @Test
    fun `addTab does not add duplicate tab by id`() {
        val manager = TabManager()
        val tab = TabItem(id = "tab1", type = TabType.QueryEditor, title = "Query 1")

        manager.addTab(tab)
        manager.addTab(tab)

        assertEquals(1, manager.tabs.size)
    }

    @Test
    fun `addTab with multiple tabs selects most recent`() {
        val manager = TabManager()
        val tab1 = TabItem(id = "tab1", type = TabType.QueryEditor, title = "Query 1")
        val tab2 = TabItem(id = "tab2", type = TabType.Favorites, title = "Favorites")

        manager.addTab(tab1)
        manager.addTab(tab2)

        assertEquals(2, manager.tabs.size)
        assertEquals("tab2", manager.selectedTabId)
    }

    @Test
    fun `closeTab removes tab from list`() {
        val manager = TabManager()
        val tab1 = TabItem(id = "tab1", type = TabType.QueryEditor, title = "Query 1")
        val tab2 = TabItem(id = "tab2", type = TabType.Favorites, title = "Favorites")

        manager.addTab(tab1)
        manager.addTab(tab2)
        manager.closeTab("tab1")

        assertEquals(1, manager.tabs.size)
        assertEquals("tab2", manager.tabs[0].id)
    }

    @Test
    fun `closeTab selects next tab when closing selected tab`() {
        val manager = TabManager()
        val tab1 = TabItem(id = "tab1", type = TabType.QueryEditor, title = "Query 1")
        val tab2 = TabItem(id = "tab2", type = TabType.Favorites, title = "Favorites")
        val tab3 = TabItem(id = "tab3", type = TabType.History, title = "History")

        manager.addTab(tab1)
        manager.addTab(tab2)
        manager.addTab(tab3)
        manager.selectTab("tab2")

        manager.closeTab("tab2")

        assertEquals(2, manager.tabs.size)
        assertEquals("tab3", manager.selectedTabId)
    }

    @Test
    fun `closeTab selects previous tab when closing last selected tab`() {
        val manager = TabManager()
        val tab1 = TabItem(id = "tab1", type = TabType.QueryEditor, title = "Query 1")
        val tab2 = TabItem(id = "tab2", type = TabType.Favorites, title = "Favorites")
        val tab3 = TabItem(id = "tab3", type = TabType.History, title = "History")

        manager.addTab(tab1)
        manager.addTab(tab2)
        manager.addTab(tab3)

        manager.closeTab("tab3")

        assertEquals(2, manager.tabs.size)
        assertEquals("tab2", manager.selectedTabId)
    }

    @Test
    fun `closeTab sets selectedTabId to null when closing last tab`() {
        val manager = TabManager()
        val tab = TabItem(id = "tab1", type = TabType.QueryEditor, title = "Query 1")

        manager.addTab(tab)
        manager.closeTab("tab1")

        assertTrue(manager.tabs.isEmpty())
        assertNull(manager.selectedTabId)
        assertNull(manager.selectedTab)
    }

    @Test
    fun `closeTab does nothing when tab id does not exist`() {
        val manager = TabManager()
        val tab = TabItem(id = "tab1", type = TabType.QueryEditor, title = "Query 1")

        manager.addTab(tab)
        manager.closeTab("nonexistent")

        assertEquals(1, manager.tabs.size)
        assertEquals("tab1", manager.selectedTabId)
    }

    @Test
    fun `closeTab does not change selection when closing non-selected tab`() {
        val manager = TabManager()
        val tab1 = TabItem(id = "tab1", type = TabType.QueryEditor, title = "Query 1")
        val tab2 = TabItem(id = "tab2", type = TabType.Favorites, title = "Favorites")

        manager.addTab(tab1)
        manager.addTab(tab2)
        manager.selectTab("tab1")

        manager.closeTab("tab2")

        assertEquals(1, manager.tabs.size)
        assertEquals("tab1", manager.selectedTabId)
    }

    @Test
    fun `selectTab changes selected tab`() {
        val manager = TabManager()
        val tab1 = TabItem(id = "tab1", type = TabType.QueryEditor, title = "Query 1")
        val tab2 = TabItem(id = "tab2", type = TabType.Favorites, title = "Favorites")

        manager.addTab(tab1)
        manager.addTab(tab2)
        manager.selectTab("tab1")

        assertEquals("tab1", manager.selectedTabId)
        assertEquals(tab1, manager.selectedTab)
    }

    @Test
    fun `selectTab does nothing when tab id does not exist`() {
        val manager = TabManager()
        val tab = TabItem(id = "tab1", type = TabType.QueryEditor, title = "Query 1")

        manager.addTab(tab)
        manager.selectTab("nonexistent")

        assertEquals("tab1", manager.selectedTabId)
    }

    @Test
    fun `moveTab reorders tabs correctly`() {
        val manager = TabManager()
        val tab1 = TabItem(id = "tab1", type = TabType.QueryEditor, title = "Query 1")
        val tab2 = TabItem(id = "tab2", type = TabType.Favorites, title = "Favorites")
        val tab3 = TabItem(id = "tab3", type = TabType.History, title = "History")

        manager.addTab(tab1)
        manager.addTab(tab2)
        manager.addTab(tab3)

        manager.moveTab(fromIndex = 0, toIndex = 2)

        assertEquals("tab2", manager.tabs[0].id)
        assertEquals("tab3", manager.tabs[1].id)
        assertEquals("tab1", manager.tabs[2].id)
    }

    @Test
    fun `moveTab does nothing when fromIndex is out of bounds`() {
        val manager = TabManager()
        val tab1 = TabItem(id = "tab1", type = TabType.QueryEditor, title = "Query 1")
        val tab2 = TabItem(id = "tab2", type = TabType.Favorites, title = "Favorites")

        manager.addTab(tab1)
        manager.addTab(tab2)

        manager.moveTab(fromIndex = 5, toIndex = 1)

        assertEquals("tab1", manager.tabs[0].id)
        assertEquals("tab2", manager.tabs[1].id)
    }

    @Test
    fun `moveTab does nothing when toIndex is out of bounds`() {
        val manager = TabManager()
        val tab1 = TabItem(id = "tab1", type = TabType.QueryEditor, title = "Query 1")
        val tab2 = TabItem(id = "tab2", type = TabType.Favorites, title = "Favorites")

        manager.addTab(tab1)
        manager.addTab(tab2)

        manager.moveTab(fromIndex = 0, toIndex = 5)

        assertEquals("tab1", manager.tabs[0].id)
        assertEquals("tab2", manager.tabs[1].id)
    }

    @Test
    fun `moveTab does nothing when fromIndex equals toIndex`() {
        val manager = TabManager()
        val tab1 = TabItem(id = "tab1", type = TabType.QueryEditor, title = "Query 1")
        val tab2 = TabItem(id = "tab2", type = TabType.Favorites, title = "Favorites")

        manager.addTab(tab1)
        manager.addTab(tab2)

        manager.moveTab(fromIndex = 1, toIndex = 1)

        assertEquals("tab1", manager.tabs[0].id)
        assertEquals("tab2", manager.tabs[1].id)
    }

    @Test
    fun `selectedTab returns null when selectedTabId is null`() {
        val manager = TabManager()

        assertNull(manager.selectedTab)
    }

    @Test
    fun `selectedTab returns null when selectedTabId does not match any tab`() {
        val manager = TabManager()
        val tab = TabItem(id = "tab1", type = TabType.QueryEditor, title = "Query 1")

        manager.addTab(tab)
        manager.closeTab("tab1")

        assertNull(manager.selectedTab)
    }

    @Test
    fun `tabItem supports connectionId and tableName fields`() {
        val tab =
            TabItem(
                id = "tab1",
                type = TabType.TableEditor,
                title = "Edit users",
                connectionId = "conn123",
                tableName = "users",
            )

        assertEquals("conn123", tab.connectionId)
        assertEquals("users", tab.tableName)
    }

    @Test
    fun `tabType has exactly five values`() {
        val types = TabType.entries

        assertEquals(5, types.size)
        assertTrue(types.contains(TabType.QueryEditor))
        assertTrue(types.contains(TabType.Favorites))
        assertTrue(types.contains(TabType.History))
        assertTrue(types.contains(TabType.QueryLog))
        assertTrue(types.contains(TabType.TableEditor))
    }
}
