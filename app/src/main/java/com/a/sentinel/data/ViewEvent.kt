package com.a.sentinel.data

import com.a.sentinel.data.ProcessInfo

sealed class MainViewEvent {
    data object CheckRootStatus : MainViewEvent()
    data object RefreshProcesses : MainViewEvent()
    data object ShowAddWhitelistDialog : MainViewEvent()
    data object HideAddWhitelistDialog : MainViewEvent()
    data class AddToWhitelist(val packageName: String) : MainViewEvent()
    data class RemoveFromWhitelist(val packageName: String) : MainViewEvent()
    data class ToggleProcessSelection(val process: ProcessInfo) : MainViewEvent()
    data class ToggleProcessExpansion(val process: ProcessInfo) : MainViewEvent()
    data class KillSelectedProcesses(val processes: Set<ProcessInfo>) : MainViewEvent()
    data class KillProcess(val process: ProcessInfo) : MainViewEvent()
    data class UpdateNewPackageName(val name: String) : MainViewEvent()
    data object ToggleSystemProcessSection : MainViewEvent()
    data object ToggleUserProcessSection : MainViewEvent()
}