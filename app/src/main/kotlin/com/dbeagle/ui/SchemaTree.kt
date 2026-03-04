package com.dbeagle.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.onClick
import androidx.compose.foundation.PointerMatcher
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

sealed class SchemaTreeNode(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    open val children: List<SchemaTreeNode> = emptyList()

    class Section(id: String, label: String, override val children: List<SchemaTreeNode>) :
        SchemaTreeNode(id, label, Icons.AutoMirrored.Filled.List)

    class Table(id: String, label: String, override val children: List<SchemaTreeNode>) :
        SchemaTreeNode(id, label, Icons.Default.Menu)

    class Column(id: String, label: String, val type: String) :
        SchemaTreeNode(id, label, Icons.Default.Info)

    class View(id: String, label: String) : SchemaTreeNode(id, label, Icons.Default.PlayArrow)

    class Index(id: String, label: String) : SchemaTreeNode(id, label, Icons.Default.Search)
}

@Composable
fun SchemaTree(
    nodes: List<SchemaTreeNode>,
    modifier: Modifier = Modifier,
    onCopyName: (String) -> Unit = {},
    onViewData: (String) -> Unit = {}
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
                onCopyName = onCopyName,
                onViewData = onViewData
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
    onCopyName: (String) -> Unit,
    onViewData: (String) -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onClick {
                    if (node.children.isNotEmpty()) {
                        onToggle()
                    }
                }
                .onClick(
                    matcher = PointerMatcher.mouse(PointerButton.Secondary),
                    onClick = {
                        if (node is SchemaTreeNode.Table) {
                            showContextMenu = true
                        }
                    }
                )
                .padding(vertical = 4.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width((depth * 16).dp))

            if (node.children.isNotEmpty()) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.width(16.dp))
            }

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = node.icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = node.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (node is SchemaTreeNode.Column) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = node.type,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Copy Name") },
                onClick = {
                    onCopyName(node.label)
                    showContextMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("View Data") },
                onClick = {
                    onViewData(node.label)
                    showContextMenu = false
                }
            )
        }
    }
}
