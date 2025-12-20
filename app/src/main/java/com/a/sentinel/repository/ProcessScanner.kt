package com.a.sentinel.repository

import com.a.sentinel.data.ProcessInfo

object ProcessScanner {

    fun scan(): List<ProcessInfo> {
        val result = mutableListOf<ProcessInfo>()
        val output = RootShell.exec("ls /proc | grep '^[0-9]'")

        output.lines().forEach { pidStr ->
            try {
                val pid = pidStr.toInt()
                val status = RootShell.exec("cat /proc/$pid/status")
                val cmdline = RootShell.exec("cat /proc/$pid/cmdline")

                val uidLine = status.lines().firstOrNull { it.startsWith("Uid:") }
                val uid = uidLine?.split("\\s+".toRegex())?.getOrNull(1)?.toInt() ?: return@forEach

                if (uid < 10000) return@forEach // 非三方 App

                val name = cmdline.replace('\u0000', ' ').trim()
                if (name.isBlank()) return@forEach

                result.add(
                    ProcessInfo(
                        pid = pid,
                        uid = uid,
                        processName = name,
                        packageName = extractPackage(name)
                    )
                )
            } catch (_: Exception) {}
        }

        return result
    }

    private fun extractPackage(cmd: String): String? {
        return cmd.split(" ")
            .firstOrNull { it.contains('.') && !it.contains("/") }
    }
    
    /**
     * 根据使用场景过滤进程列表
     * @param scenario 场景类型： "screen_off", "game_mode" 等
     * @return 过滤后的进程列表
     */
    fun scanByScenario(scenario: String): List<ProcessInfo> {
        val allProcesses = scan()
        
        return when (scenario) {
            "screen_off" -> {
                // 锁屏时可以考虑清理更多后台应用
                allProcesses.filter { process ->
                    // 移除白名单中的进程
                    !SystemWhitelist.isInWhitelist(process.packageName)
                }
            }
            "game_mode" -> {
                // 游戏模式下清理其他非必要的后台应用
                allProcesses.filter { process ->
                    // 移除白名单中的进程
                    !SystemWhitelist.isInWhitelist(process.packageName)
                }
            }
            else -> allProcesses
        }
    }
}