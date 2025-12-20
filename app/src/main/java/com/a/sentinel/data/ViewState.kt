package com.a.sentinel.data

data class MainViewState(
    val isLoading: Boolean = false,
    val rootStatus: String = "Checking root status...",
    val hasRoot: Boolean = false,
    val processList: List<ProcessInfo> = emptyList(),
    val whitelist: Set<String> = emptySet(),
    val selectedProcesses: Set<ProcessInfo> = emptySet(),
    val expandedProcess: ProcessInfo? = null,
    val showAddWhitelistDialog: Boolean = false,
    val newPackageName: String = "",
    val errorMessage: String? = null
)