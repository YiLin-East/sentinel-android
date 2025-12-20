package com.a.sentinel.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a.sentinel.data.MainViewEvent
import com.a.sentinel.data.MainViewState
import com.a.sentinel.data.ProcessInfo
import com.a.sentinel.repository.ProcessKiller
import com.a.sentinel.repository.ProcessScanner
import com.a.sentinel.repository.RootShell
import com.a.sentinel.repository.SystemWhitelist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {
    private val _viewState = MutableStateFlow(MainViewState())
    val viewState: StateFlow<MainViewState> = _viewState.asStateFlow()

    init {
        handleEvent(MainViewEvent.CheckRootStatus)
    }

    fun handleEvent(event: MainViewEvent) {
        when (event) {
            is MainViewEvent.CheckRootStatus -> checkRootStatus()
            is MainViewEvent.RefreshProcesses -> refreshProcesses()
            is MainViewEvent.ShowAddWhitelistDialog -> showAddWhitelistDialog()
            is MainViewEvent.HideAddWhitelistDialog -> hideAddWhitelistDialog()
            is MainViewEvent.AddToWhitelist -> addToWhitelist(event.packageName)
            is MainViewEvent.RemoveFromWhitelist -> removeFromWhitelist(event.packageName)
            is MainViewEvent.ToggleProcessSelection -> toggleProcessSelection(event.process)
            is MainViewEvent.ToggleProcessExpansion -> toggleProcessExpansion(event.process)
            is MainViewEvent.KillSelectedProcesses -> killSelectedProcesses(event.processes)
            is MainViewEvent.KillProcess -> killProcess(event.process)
            is MainViewEvent.UpdateNewPackageName -> updateNewPackageName(event.name)
            is MainViewEvent.ToggleSystemProcessSection -> toggleSystemProcessSection()
            is MainViewEvent.ToggleSystemServiceProcessSection -> toggleSystemServiceProcessSection()
            is MainViewEvent.ToggleUserProcessSection -> toggleUserProcessSection()
        }
    }

    private fun checkRootStatus() {
        viewModelScope.launch {
            _viewState.value = _viewState.value.copy(isLoading = true)
            try {
                val hasRoot = withContext(Dispatchers.IO) {
                    RootShell.hasRoot()
                }
                
                val newState = if (hasRoot) {
                    // 加载白名单
                    val whitelist = withContext(Dispatchers.IO) {
                        SystemWhitelist.getAll()
                    }
                    // 扫描进程
                    val processList = ProcessScanner.scan()
                    MainViewState(
                        isLoading = false,
                        rootStatus = "Device is rooted!",
                        hasRoot = true,
                        processList = processList,
                        whitelist = whitelist,
                        // 默认系统进程折叠，用户进程展开
                        isSystemProcessSectionExpanded = false,
                        isSystemServiceProcessSectionExpanded = false,
                        isUserProcessSectionExpanded = true
                    )
                } else {
                    MainViewState(
                        isLoading = false,
                        rootStatus = "Device is not rooted or root access denied.",
                        hasRoot = false
                    )
                }
                _viewState.value = newState
            } catch (e: Exception) {
                _viewState.value = _viewState.value.copy(
                    isLoading = false,
                    rootStatus = "Error checking root status: ${e.message}"
                )
            }
        }
    }

    private fun refreshProcesses() {
        viewModelScope.launch {
            if (!_viewState.value.hasRoot) return@launch
            
            _viewState.value = _viewState.value.copy(isLoading = true)
            try {
                val processList = ProcessScanner.scan()
                _viewState.value = _viewState.value.copy(
                    isLoading = false,
                    processList = processList
                )
            } catch (e: Exception) {
                _viewState.value = _viewState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to refresh processes: ${e.message}"
                )
            }
        }
    }

    private fun showAddWhitelistDialog() {
        _viewState.value = _viewState.value.copy(showAddWhitelistDialog = true)
    }

    private fun hideAddWhitelistDialog() {
        _viewState.value = _viewState.value.copy(
            showAddWhitelistDialog = false,
            newPackageName = ""
        )
    }

    private fun addToWhitelist(packageName: String) {
        if (packageName.isNotBlank()) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    SystemWhitelist.addToUserWhitelist(packageName)
                }
                val whitelist = withContext(Dispatchers.IO) {
                    SystemWhitelist.getAll()
                }
                _viewState.value = _viewState.value.copy(
                    whitelist = whitelist,
                    showAddWhitelistDialog = false,
                    newPackageName = ""
                )
            }
        }
    }

    private fun removeFromWhitelist(packageName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                SystemWhitelist.removeFromUserWhitelist(packageName)
            }
            val whitelist = withContext(Dispatchers.IO) {
                SystemWhitelist.getAll()
            }
            _viewState.value = _viewState.value.copy(whitelist = whitelist)
        }
    }

    private fun toggleProcessSelection(process: ProcessInfo) {
        val currentSelection = _viewState.value.selectedProcesses
        val newSelection = if (currentSelection.contains(process)) {
            currentSelection - process
        } else {
            currentSelection + process
        }
        _viewState.value = _viewState.value.copy(selectedProcesses = newSelection)
    }

    private fun toggleProcessExpansion(process: ProcessInfo) {
        val currentExpanded = _viewState.value.expandedProcess
        val newExpanded = if (currentExpanded == process) null else process
        _viewState.value = _viewState.value.copy(expandedProcess = newExpanded)
    }

    private fun toggleSystemProcessSection() {
        val currentState = _viewState.value.isSystemProcessSectionExpanded
        _viewState.value = _viewState.value.copy(isSystemProcessSectionExpanded = !currentState)
    }

    private fun toggleSystemServiceProcessSection() {
        val currentState = _viewState.value.isSystemServiceProcessSectionExpanded
        _viewState.value = _viewState.value.copy(isSystemServiceProcessSectionExpanded = !currentState)
    }

    private fun toggleUserProcessSection() {
        val currentState = _viewState.value.isUserProcessSectionExpanded
        _viewState.value = _viewState.value.copy(isUserProcessSectionExpanded = !currentState)
    }

    private fun killSelectedProcesses(processes: Set<ProcessInfo>) {
        viewModelScope.launch {
            _viewState.value = _viewState.value.copy(isLoading = true)
            try {
                withContext(Dispatchers.IO) {
                    processes.forEach { process ->
                        ProcessKiller.killPid(process.pid)
                    }
                }
                // 刷新进程列表
                val processList = ProcessScanner.scan()
                _viewState.value = _viewState.value.copy(
                    isLoading = false,
                    processList = processList,
                    selectedProcesses = emptySet()
                )
            } catch (e: Exception) {
                _viewState.value = _viewState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to kill processes: ${e.message}"
                )
            }
        }
    }

    private fun killProcess(process: ProcessInfo) {
        viewModelScope.launch {
            _viewState.value = _viewState.value.copy(isLoading = true)
            try {
                withContext(Dispatchers.IO) {
                    ProcessKiller.killPid(process.pid)
                }
                // 刷新进程列表
                val processList = ProcessScanner.scan()
                _viewState.value = _viewState.value.copy(
                    isLoading = false,
                    processList = processList
                )
            } catch (e: Exception) {
                _viewState.value = _viewState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to kill process: ${e.message}"
                )
            }
        }
    }

    private fun updateNewPackageName(name: String) {
        _viewState.value = _viewState.value.copy(newPackageName = name)
    }
}