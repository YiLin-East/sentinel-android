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
    data object ToggleSystemServiceProcessSection : MainViewEvent()
    data object ToggleUserProcessSection : MainViewEvent()
    
    // 黑名单相关事件
    data object ShowAddBlacklistDialog : MainViewEvent()
    data object HideAddBlacklistDialog : MainViewEvent()
    data class AddToBlacklist(val packageName: String) : MainViewEvent()
    data class RemoveFromBlacklist(val packageName: String) : MainViewEvent()
    data object KillAllBlacklistedApps : MainViewEvent()
}