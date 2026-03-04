package com.dbeagle.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.onClick
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.unit.dp

sealed class SchemaTreeNode(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    open val children: List<SchemaTreeNode> = emptyList()

    class Section(id: String, label: String, override val children: List<SchemaTreeNode>) : SchemaTreeNode(id, label, Icons.AutoMirrored.Filled.List)

    class Table(id: String, label: String, override val children: List<SchemaTreeNode>) : SchemaTreeNode(id, label, Icons.Default.Menu)

    class Column(id: String, label: String, val type: String) : SchemaTreeNode(id, label, Icons.Default.Info)

    class View(id: String, label: String) : SchemaTreeNode(id, label, Icons.Default.PlayArrow)

    class Index(id: String, label: String) : SchemaTreeNode(id, label, Icons.Default.Search)
}

@Composable
fun SchemaTree(
    nodes: List<SchemaTreeNode>,
    modifier: Modifier = Modifier,
    onNodeExpansionChanged: (SchemaTreeNode, Boolean) -> Unit = { _, _ -> },
    onCopyName: (String) -> Unit = {},
    onViewData: (String) -> Unit = {},
) {
    var expandedIds by remember { mutableStateOf(setOf<String>()) }

    fun toggleExpanded(id: String) {
        expandedIds = if (expandedIds.contains(id)) {
            expandedIds - id
        } else {
            expandedIds + id
        }
    }

    val flattenNodes = remember(nodes, expandedIds) {
        val result = mutableListOf<Pair<SchemaTreeNode, Int>>()

        fun traverse(nodeList: List<SchemaTreeNode>, depth: Int) {
            for (node in nodeList) {
                result.add(node to depth)
                if (expandedIds.contains(node.id) && node.children.isNotEmpty()) {
                    traverse(node.children, depth + 1)
                }
            }
        }

        traverse(nodes, 0)
        result
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(flattenNodes, key = { it.first.id }) { (node, depth) ->
            SchemaTreeNodeItem(
                node = node,
                depth = depth,
                isExpanded = expandedIds.contains(node.id),
                onToggle = { toggleExpanded(node.id) },
                onNodeExpansionChanged = onNodeExpansionChanged,
                onCopyName = onCopyName,
                onViewData = onViewData,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SchemaTreeNodeItem(
    node: SchemaTreeNode,
    depth: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onNodeExpansionChanged: (SchemaTreeNode, Boolean) -> Unit,
    onCopyName: (String) -> Unit,
    onViewData: (String) -> Unit,
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onClick {
                    if (node is SchemaTreeNode.Table || node.children.isNotEmpty()) {
                        val willExpand = !isExpanded
                        onToggle()
                        onNodeExpansionChanged(node, willExpand)
                    }
                }
                .onClick(
                    matcher = PointerMatcher.mouse(PointerButton.Secondary),
                    onClick = {
                        if (node is SchemaTreeNode.Table) {
                            showContextMenu = true
                        }
                    },
                )
                .padding(vertical = 4.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width((depth * 16).dp))

            val isExpandable = node is SchemaTreeNode.Table || node.children.isNotEmpty()
            if (isExpandable) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(modifier = Modifier.width(16.dp))
            }

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = node.icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = node.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (node is SchemaTreeNode.Column) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = node.type,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("Copy Name") },
                onClick = {
                    onCopyName(node.label)
                    showContextMenu = false
                },
            )
            DropdownMenuItem(
                text = { Text("View Data") },
                onClick = {
                    onViewData(node.label)
                    showContextMenu = false
                },
            )
        }
    }
}
