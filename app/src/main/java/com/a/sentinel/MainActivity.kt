package com.a.sentinel

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.a.sentinel.data.MainViewEvent
import com.a.sentinel.data.ProcessInfo
import com.a.sentinel.repository.ProcessScanner
import com.a.sentinel.repository.SystemWhitelist
import com.a.sentinel.ui.theme.SentinelTheme
import com.a.sentinel.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 初始化白名单系统
        SystemWhitelist.initialize(this)
        
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
    }
    
    // 添加到白名单对话框
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
                        if (viewState.newPackageName.isNotBlank()) {
                            viewModel.handleEvent(
                                MainViewEvent.AddToWhitelist(viewState.newPackageName)
                            )
                            Toast.makeText(context, "Added to whitelist", Toast.LENGTH_SHORT).show()
                        }
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
    
    // 添加到黑名单对话框
    if (viewState.showAddBlacklistDialog) {
        var installedApps by remember { mutableStateOf<List<PackageInfo>>(emptyList()) }
        var filteredApps by remember { mutableStateOf<List<PackageInfo>>(emptyList()) }
        var searchQuery by remember { mutableStateOf("") }
        
        // 加载已安装的应用
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                try {
                    val apps = context.packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
                        .filter { packageInfo ->
                            // 检查 applicationInfo 是否为空并且不是系统应用
                            packageInfo.applicationInfo?.let { appInfo ->
                                appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0
                            } ?: false
                        }
                    installedApps = apps
                    filteredApps = apps
                } catch (e: Exception) {
                    Log.e("MainScreen", "Failed to load installed apps", e)
                }
            }
        }
        
        // 搜索过滤
        LaunchedEffect(searchQuery, installedApps) {
            filteredApps = if (searchQuery.isBlank()) {
                installedApps
            } else {
                installedApps.filter { app ->
                    val appName = try {
                        app.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                    appName.contains(searchQuery, ignoreCase = true) || 
                    app.packageName.contains(searchQuery, ignoreCase = true)
                }
            }
        }
        
        AlertDialog(
            onDismissRequest = { 
                viewModel.handleEvent(MainViewEvent.HideAddBlacklistDialog) 
            },
            title = { Text("Add to Blacklist") },
            text = {
                Column(
                    modifier = Modifier.height(400.dp)
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search apps") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredApps) { app ->
                            val appName = try {
                                app.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: app.packageName
                            } catch (e: Exception) {
                                app.packageName
                            }
                            
                            var appIcon by remember(app.packageName) { mutableStateOf<Drawable?>(null) }
                            
                            // 加载应用图标
                            LaunchedEffect(app.packageName) {
                                withContext(Dispatchers.IO) {
                                    try {
                                        val icon = app.applicationInfo?.loadIcon(context.packageManager)
                                        withContext(Dispatchers.Main) {
                                            appIcon = icon
                                        }
                                    } catch (e: Exception) {
                                        Log.e("MainScreen", "Failed to load app icon", e)
                                    }
                                }
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.handleEvent(
                                            MainViewEvent.AddToBlacklist(app.packageName)
                                        )
                                        Toast.makeText(context, "Added to blacklist", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (appIcon != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(appIcon)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "App Icon",
                                        modifier = Modifier
                                            .size(40.dp)
                                            .padding(end = 8.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .padding(end = 8.dp)
                                            .background(Color.LightGray)
                                    )
                                }
                                
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = appName,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                if (viewState.blacklist.contains(app.packageName)) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Already in blacklist",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        viewModel.handleEvent(MainViewEvent.HideAddBlacklistDialog) 
                    }
                ) {
                    Text("Close")
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
                            enabled = viewState.hasRoot && viewState.hasRootChecked && !viewState.isLoading
                        ) {
                            Text("Refresh")
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { 
                                    viewModel.handleEvent(MainViewEvent.ShowAddWhitelistDialog) 
                                },
                                enabled = viewState.hasRoot && viewState.hasRootChecked && !viewState.isLoading
                            ) {
                                Text("Add to Whitelist")
                            }
                            
                            Button(
                                onClick = { 
                                    viewModel.handleEvent(MainViewEvent.ShowAddBlacklistDialog) 
                                },
                                enabled = viewState.hasRoot && viewState.hasRootChecked && !viewState.isLoading
                            ) {
                                Text("Add to Blacklist")
                            }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Whitelist (${viewState.whitelist.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        if (viewState.whitelist.isNotEmpty()) {
                            Text(
                                text = "${viewState.whitelist.size} apps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
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
                                        },
                                        enabled = viewState.hasRoot && viewState.hasRootChecked
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
                            text = "Blacklist (${viewState.blacklist.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (viewState.blacklist.isNotEmpty()) {
                                Text(
                                    text = "${viewState.blacklist.size} apps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Button(
                                    onClick = { 
                                        viewModel.handleEvent(MainViewEvent.KillAllBlacklistedApps)
                                        Toast.makeText(context, "Killing blacklisted apps...", Toast.LENGTH_SHORT).show()
                                    },
                                    enabled = viewState.hasRoot && viewState.hasRootChecked && !viewState.isLoading,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Kill All")
                                }
                            }
                            
                            IconButton(
                                onClick = { 
                                    viewModel.handleEvent(MainViewEvent.ShowAddBlacklistDialog) 
                                },
                                enabled = viewState.hasRoot && viewState.hasRootChecked && !viewState.isLoading
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add to Blacklist")
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (viewState.blacklist.isEmpty()) {
                        Text("No packages in blacklist")
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(viewState.blacklist.toList()) { pkg ->
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
                                                MainViewEvent.RemoveFromBlacklist(pkg)
                                            )
                                            Toast.makeText(context, "Removed from blacklist", Toast.LENGTH_SHORT).show()
                                        },
                                        enabled = viewState.hasRoot && viewState.hasRootChecked
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
                                    enabled = viewState.hasRoot && viewState.hasRootChecked && !viewState.isLoading
                                ) {
                                    Text("Kill Selected")
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (!viewState.hasRootChecked) {
                        Text("Checking root permissions...")
                    } else if (!viewState.hasRoot) {
                        Text("Root permission not granted. Some features will be disabled.")
                    } else if (viewState.processList.isEmpty()) {
                        Text("No processes found")
                    } else {
                        // 分离不同类型进程
                        val coreSystemProcesses = viewState.processList.filter { ProcessScanner.isCoreSystemProcess(it) }
                        val systemServiceProcesses = viewState.processList.filter { ProcessScanner.isSystemServiceProcess(it) }
                        val userAppProcesses = viewState.processList.filter { ProcessScanner.isUserAppProcess(it) }
                        
                        // 核心系统进程部分
                        ProcessSection(
                            title = "Core System Processes (${coreSystemProcesses.size})",
                            isExpanded = viewState.isSystemProcessSectionExpanded,
                            onToggle = { viewModel.handleEvent(MainViewEvent.ToggleSystemProcessSection) },
                            processes = coreSystemProcesses,
                            viewState = viewState,
                            viewModel = viewModel,
                            context = context
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 系统服务进程部分
                        ProcessSection(
                            title = "System Service Processes (${systemServiceProcesses.size})",
                            isExpanded = viewState.isSystemServiceProcessSectionExpanded,
                            onToggle = { viewModel.handleEvent(MainViewEvent.ToggleSystemServiceProcessSection) },
                            processes = systemServiceProcesses,
                            viewState = viewState,
                            viewModel = viewModel,
                            context = context
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 用户应用进程部分
                        ProcessSection(
                            title = "User App Processes (${userAppProcesses.size})",
                            isExpanded = viewState.isUserProcessSectionExpanded,
                            onToggle = { viewModel.handleEvent(MainViewEvent.ToggleUserProcessSection) },
                            processes = userAppProcesses,
                            viewState = viewState,
                            viewModel = viewModel,
                            context = context
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProcessSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    processes: List<ProcessInfo>,
    viewState: com.a.sentinel.data.MainViewState,
    viewModel: com.a.sentinel.viewmodel.MainViewModel,
    context: android.content.Context
) {
    Column {
        // 吸顶标题栏
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(1f),
            shadowElevation = 4.dp,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(vertical = 8.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowDropDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${processes.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // 进程列表
        if (isExpanded && processes.isNotEmpty()) {
            // 使用Column替代嵌套的LazyColumn
            Column(modifier = Modifier.fillMaxWidth()) {
                processes.forEach { process ->
                    ProcessItem(
                        process = process,
                        isSelected = viewState.selectedProcesses.contains(process),
                        isWhitelisted = com.a.sentinel.repository.SystemWhitelist.isInWhitelist(process.packageName),
                        isBlacklisted = com.a.sentinel.repository.SystemWhitelist.isInBlacklist(process.packageName),
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
                        enabled = viewState.hasRoot && viewState.hasRootChecked && !viewState.isLoading
                    )
                }
            }
        } else if (isExpanded) {
            Text(
                text = "No processes in this section",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// 应用图标缓存
object AppIconCache {
    private val cache = ConcurrentHashMap<String, Drawable>()
    private var defaultIcon: Drawable? = null
    
    fun get(packageName: String): Drawable? = if (packageName == "default") defaultIcon else cache[packageName]
    
    fun put(packageName: String, icon: Drawable) {
        if (packageName == "default") {
            defaultIcon = icon
        } else {
            cache[packageName] = icon
        }
    }
    
    fun clear() {
        cache.clear()
        defaultIcon = null
    }
}

@Composable
fun ProcessItem(
    process: ProcessInfo,
    isSelected: Boolean,
    isWhitelisted: Boolean,
    isBlacklisted: Boolean,
    onProcessSelected: (Boolean) -> Unit,
    onExpandClicked: () -> Unit,
    isExpanded: Boolean,
    onKillProcess: () -> Unit,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    var appIcon by remember { mutableStateOf<Drawable?>(null) }
    
    // 为用户应用进程加载应用图标
    LaunchedEffect(process.packageName) {
        if (process.packageName != null && com.a.sentinel.repository.ProcessScanner.isUserAppProcess(process)) {
            // 首先检查缓存
            var cachedIcon = AppIconCache.get(process.packageName)
            if (cachedIcon != null) {
                appIcon = cachedIcon
                return@LaunchedEffect
            }
            
            // 检查是否有默认图标
            val defaultIcon = AppIconCache.get("default")
            if (defaultIcon != null) {
                appIcon = defaultIcon
            }
            
            // 缓存未命中，从PackageManager获取
            withContext(Dispatchers.IO) {
                try {
                    val applicationInfo = context.packageManager.getApplicationInfo(process.packageName, 0)
                    val icon = applicationInfo.loadIcon(context.packageManager)
                    withContext(Dispatchers.Main) {
                        appIcon = icon
                        // 存入缓存
                        AppIconCache.put(process.packageName, icon)
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.d("ProcessItem", "App not found: ${process.packageName}", e)
                    // 应用未找到，使用默认图标
                    var defaultIcon = AppIconCache.get("default")
                    if (defaultIcon == null) {
                        defaultIcon = ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
                        // 存入缓存
                        if (defaultIcon != null) {
                            AppIconCache.put("default", defaultIcon)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        appIcon = defaultIcon
                        // 存入特定应用的缓存
                        if (defaultIcon != null) {
                            AppIconCache.put(process.packageName, defaultIcon)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ProcessItem", "Error loading icon for: ${process.packageName}", e)
                    // 其他错误，使用默认图标
                    var defaultIcon = AppIconCache.get("default")
                    if (defaultIcon == null) {
                        defaultIcon = ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
                        // 存入缓存
                        if (defaultIcon != null) {
                            AppIconCache.put("default", defaultIcon)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        appIcon = defaultIcon
                        // 存入特定应用的缓存
                        if (defaultIcon != null) {
                            AppIconCache.put(process.packageName, defaultIcon)
                        }
                    }
                }
            }
        }
    }
    
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
                
                // 显示应用图标（仅对用户应用进程）
                if (com.a.sentinel.repository.ProcessScanner.isUserAppProcess(process) && appIcon != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(appIcon)
                            .crossfade(true)
                            .build(),
                        contentDescription = "App Icon",
                        modifier = Modifier
                            .size(40.dp)
                            .padding(end = 8.dp)
                    )
                } else if (com.a.sentinel.repository.ProcessScanner.isUserAppProcess(process)) {
                    // 加载中占位符 - 使用默认应用图标
                    val defaultIcon = AppIconCache.get("default")
                    if (defaultIcon != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(defaultIcon)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Default App Icon",
                            modifier = Modifier
                                .size(40.dp)
                                .padding(end = 8.dp)
                        )
                    } else {
                        // 如果还没有默认图标，显示灰色占位符
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .padding(end = 8.dp)
                                .background(Color.LightGray)
                        )
                    }
                }
                
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
                        
                        // 显示黑白名单标签
                        Row {
                            if (isWhitelisted) {
                                Text(
                                    text = "WHITELISTED",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (isBlacklisted) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "BLACKLISTED",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    // 显示UID信息
                    Text(
                        text = "UID: ${process.uid}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (!isWhitelisted) {
                    IconButton(onClick = onExpandClicked, enabled = enabled) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowDropDown,
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