package com.a.sentinel

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.a.sentinel.data.MainViewEvent
import com.a.sentinel.data.ProcessInfo
import com.a.sentinel.ui.theme.SentinelTheme
import com.a.sentinel.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 初始化白名单系统
        com.a.sentinel.repository.SystemWhitelist.initialize(this)
        
        setContent {
            SentinelTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewState by viewModel.viewState.collectAsState()
    
    // 显示错误消息
    viewState.errorMessage?.let { errorMessage ->
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
        // 清除错误消息
        viewModel.handleEvent(MainViewEvent.CheckRootStatus) // 重新检查状态以清除错误
    }
    
    if (viewState.showAddWhitelistDialog) {
        AlertDialog(
            onDismissRequest = { 
                viewModel.handleEvent(MainViewEvent.HideAddWhitelistDialog) 
            },
            title = { Text("Add to Whitelist") },
            text = {
                Column {
                    Text("Enter package name:")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = viewState.newPackageName,
                        onValueChange = { 
                            viewModel.handleEvent(MainViewEvent.UpdateNewPackageName(it)) 
                        },
                        label = { Text("Package Name") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.handleEvent(
                            MainViewEvent.AddToWhitelist(viewState.newPackageName)
                        )
                        Toast.makeText(context, "Added to whitelist", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        viewModel.handleEvent(MainViewEvent.HideAddWhitelistDialog) 
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Sentinel Process Manager",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = viewState.rootStatus)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { 
                                viewModel.handleEvent(MainViewEvent.RefreshProcesses) 
                            },
                            enabled = viewState.hasRoot && !viewState.isLoading
                        ) {
                            Text("Refresh")
                        }
                        
                        Button(
                            onClick = { 
                                viewModel.handleEvent(MainViewEvent.ShowAddWhitelistDialog) 
                            },
                            enabled = viewState.hasRoot && !viewState.isLoading
                        ) {
                            Text("Add to Whitelist")
                        }
                    }
                    
                    if (viewState.isLoading) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Whitelist (${viewState.whitelist.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (viewState.whitelist.isEmpty()) {
                        Text("No packages in whitelist")
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(viewState.whitelist.toList()) { pkg ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(pkg)
                                    IconButton(
                                        onClick = { 
                                            viewModel.handleEvent(
                                                MainViewEvent.RemoveFromWhitelist(pkg)
                                            )
                                            Toast.makeText(context, "Removed from whitelist", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Running Processes (${viewState.processList.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        if (viewState.selectedProcesses.isNotEmpty()) {
                            Row {
                                Text("${viewState.selectedProcesses.size} selected", modifier = Modifier.padding(end = 8.dp))
                                Button(
                                    onClick = { 
                                        viewModel.handleEvent(
                                            MainViewEvent.KillSelectedProcesses(viewState.selectedProcesses)
                                        )
                                        Toast.makeText(context, "Killed selected processes", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    enabled = !viewState.isLoading
                                ) {
                                    Text("Kill Selected")
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (viewState.processList.isEmpty()) {
                        Text("No processes found")
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(viewState.processList) { process ->
                                ProcessItem(
                                    process = process,
                                    isSelected = viewState.selectedProcesses.contains(process),
                                    isWhitelisted = com.a.sentinel.repository.SystemWhitelist.isInWhitelist(process.packageName),
                                    onProcessSelected = { selected ->
                                        viewModel.handleEvent(
                                            MainViewEvent.ToggleProcessSelection(process)
                                        )
                                    },
                                    onExpandClicked = {
                                        viewModel.handleEvent(
                                            MainViewEvent.ToggleProcessExpansion(process)
                                        )
                                    },
                                    isExpanded = viewState.expandedProcess == process,
                                    onKillProcess = {
                                        viewModel.handleEvent(
                                            MainViewEvent.KillProcess(process)
                                        )
                                        Toast.makeText(context, "Killed process", Toast.LENGTH_SHORT).show()
                                    },
                                    enabled = !viewState.isLoading
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProcessItem(
    process: ProcessInfo,
    isSelected: Boolean,
    isWhitelisted: Boolean,
    onProcessSelected: (Boolean) -> Unit,
    onExpandClicked: () -> Unit,
    isExpanded: Boolean,
    onKillProcess: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onProcessSelected,
                    enabled = !isWhitelisted && enabled
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = process.processName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isWhitelisted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    process.packageName?.let { pkg ->
                        Text(
                            text = pkg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isWhitelisted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (isWhitelisted) {
                    Text(
                        text = "WHITELISTED",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                } else {
                    IconButton(onClick = onExpandClicked, enabled = enabled) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.Delete else Icons.Default.Delete,
                            contentDescription = if (isExpanded) "Collapse" else "Expand"
                        )
                    }
                }
            }
            
            if (isExpanded && !isWhitelisted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onKillProcess,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = enabled
                    ) {
                        Text("Kill Process")
                    }
                }
            }
            
            if (isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text("PID: ${process.pid}", style = MaterialTheme.typography.bodySmall)
                    Text("UID: ${process.uid}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}