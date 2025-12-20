package com.a.sentinel.data

data class MainViewState(
    val isLoading: Boolean = false,
    val rootStatus: String = "Checking root status...",
    val hasRoot: Boolean = false,
    val hasRootChecked: Boolean = false, // 标记是否已经检查过root权限
    val processList: List<ProcessInfo> = emptyList(),
    val whitelist: Set<String> = emptySet(),
    val blacklist: Set<String> = emptySet(), // 黑名单
    val selectedProcesses: Set<ProcessInfo> = emptySet(),
    val expandedProcess: ProcessInfo? = null,
    val showAddWhitelistDialog: Boolean = false,
    val showAddBlacklistDialog: Boolean = false, // 显示添加黑名单对话框
    val newPackageName: String = "",
    val errorMessage: String? = null,
    val isSystemProcessSectionExpanded: Boolean = false,
    val isSystemServiceProcessSectionExpanded: Boolean = false,
    val isUserProcessSectionExpanded: Boolean = true
)