package com.a.sentinel.repository

import com.a.sentinel.data.ProcessInfo
import kotlinx.coroutines.*

object ProcessScanner {

    /**
     * 扫描进程，使用优化方式提高效率
     * 一次性获取所有必要信息，避免多次执行shell命令
     */
    suspend fun scan(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ProcessInfo>()
        
        try {
            // 一次性获取所有进程的基本信息
            val psOutput = RootShell.exec("ps -A -o pid,uid,cmd")
            
            // 解析ps命令输出
            psOutput.lines().drop(1).forEach { line ->  // 跳过标题行
                try {
                    val trimmedLine = line.trim()
                    if (trimmedLine.isEmpty()) return@forEach
                    
                    val parts = trimmedLine.split("\\s+".toRegex())
                    if (parts.size < 3) return@forEach
                    
                    val pid = parts[0].toIntOrNull() ?: return@forEach
                    val uid = parts[1].toIntOrNull() ?: return@forEach
                    
                    // 获取命令行参数
                    val cmdStartIndex = trimmedLine.indexOf(parts[2])
                    val cmd = trimmedLine.substring(cmdStartIndex).trim()
                    if (cmd.isEmpty()) return@forEach
                    
                    result.add(
                        ProcessInfo(
                            pid = pid,
                            uid = uid,
                            processName = cmd,
                            packageName = extractPackage(cmd)
                        )
                    )
                } catch (_: Exception) {
                    // 忽略解析错误的行
                }
            }
        } catch (e: Exception) {
            // 如果ps命令失败，回退到原来的方法
            return@withContext scanFallback()
        }
        
        result
    }
    
    /**
     * 回退扫描方法（原来的实现）
     */
    private fun scanFallback(): List<ProcessInfo> {
        val result = mutableListOf<ProcessInfo>()
        
        try {
            // 一次性获取所有进程ID
            val pidOutput = RootShell.exec("ls /proc | grep '^[0-9]'")
            val pids = pidOutput.lines().mapNotNull { 
                try {
                    it.toInt()
                } catch (_: NumberFormatException) {
                    null
                }
            }
            
            if (pids.isEmpty()) return result
            
            // 对于少量进程，我们仍然使用原始方法
            if (pids.size < 50) {
                pids.forEach { pid ->
                    try {
                        val status = RootShell.exec("cat /proc/$pid/status")
                        val cmdline = RootShell.exec("tr '\\0' ' ' < /proc/$pid/cmdline")
                        
                        val uidLine = status.lines().firstOrNull { it.startsWith("Uid:") }
                        val uid = uidLine?.split("\\s+".toRegex())?.getOrNull(1)?.toInt() ?: return@forEach
                        
                        val name = cmdline.trim()
                        if (name.isBlank()) return@forEach
                        
                        result.add(
                            ProcessInfo(
                                pid = pid,
                                uid = uid,
                                processName = name,
                                packageName = extractPackage(name)
                            )
                        )
                    } catch (_: Exception) {
                        // 忽略单个进程的错误
                    }
                }
            } else {
                // 对于大量进程，使用批处理方式
                // 批量获取所有进程的状态信息，减少shell执行次数
                val statusOutput = RootShell.exec("for pid in ${pids.joinToString(" ")}; do echo \"PID:\$pid\"; cat /proc/\$pid/status 2>/dev/null | grep -E 'Uid:'; done")
                val cmdlineOutput = RootShell.exec("for pid in ${pids.joinToString(" ")}; do echo \"PID:\$pid\"; tr '\\0' ' ' < /proc/\$pid/cmdline 2>/dev/null; echo; done")
                
                // 解析状态信息
                val uidMap = parseUidInfo(statusOutput)
                
                // 解析命令行信息
                val cmdlineMap = parseCmdlineInfo(cmdlineOutput)
                
                // 构建进程信息列表
                pids.forEach { pid ->
                    try {
                        val uid = uidMap[pid] ?: return@forEach
                        val cmdline = cmdlineMap[pid] ?: return@forEach
                        val name = cmdline.trim()
                        if (name.isBlank()) return@forEach
                        
                        result.add(
                            ProcessInfo(
                                pid = pid,
                                uid = uid,
                                processName = name,
                                packageName = extractPackage(name)
                            )
                        )
                    } catch (_: Exception) {
                        // 忽略解析错误的进程
                    }
                }
            }
        } catch (_: Exception) {
            // 忽略整体错误
        }
        
        return result
    }
    
    /**
     * 解析UID信息
     */
    private fun parseUidInfo(output: String): Map<Int, Int> {
        val uidMap = mutableMapOf<Int, Int>()
        val lines = output.lines()
        var currentPid: Int? = null
        
        lines.forEach { line ->
            if (line.startsWith("PID:")) {
                try {
                    currentPid = line.substring(4).toInt()
                } catch (_: NumberFormatException) {
                    currentPid = null
                }
            } else if (line.startsWith("Uid:") && currentPid != null) {
                try {
                    val uid = line.split("\\s+".toRegex())[1].toInt()
                    uidMap[currentPid!!] = uid
                } catch (_: Exception) {
                    // 忽略解析错误
                }
                currentPid = null
            }
        }
        
        return uidMap
    }
    
    /**
     * 解析命令行信息
     */
    private fun parseCmdlineInfo(output: String): Map<Int, String> {
        val cmdlineMap = mutableMapOf<Int, String>()
        val lines = output.lines()
        var currentPid: Int? = null
        var currentCmdline = ""
        
        lines.forEach { line ->
            if (line.startsWith("PID:")) {
                // 保存前一个PID的cmdline
                if (currentPid != null) {
                    cmdlineMap[currentPid!!] = currentCmdline.trim()
                }
                
                // 开始处理新的PID
                try {
                    currentPid = line.substring(4).toInt()
                    currentCmdline = ""
                } catch (_: NumberFormatException) {
                    currentPid = null
                    currentCmdline = ""
                }
            } else if (currentPid != null) {
                // 累积cmdline内容
                if (currentCmdline.isNotEmpty()) {
                    currentCmdline += "\n" + line
                } else {
                    currentCmdline = line
                }
            }
        }
        
        // 保存最后一个PID的cmdline
        if (currentPid != null) {
            cmdlineMap[currentPid!!] = currentCmdline.trim()
        }
        
        return cmdlineMap
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
    suspend fun scanByScenario(scenario: String): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val allProcesses = scan()
        
        return@withContext when (scenario) {
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
    
    /**
     * 判断是否为系统进程 (UID < 10000)
     */
    fun isSystemProcess(process: ProcessInfo): Boolean {
        return process.uid < 10000
    }
    
    /**
     * 判断是否为核心系统进程 (UID < 1000)
     */
    fun isCoreSystemProcess(process: ProcessInfo): Boolean {
        // 核心系统进程通常是 UID < 1000
        return process.uid < 1000
    }
    
    /**
     * 判断是否为系统服务进程 (UID 1000-9999)
     */
    fun isSystemServiceProcess(process: ProcessInfo): Boolean {
        // 系统服务进程通常是 UID 在 1000-9999 范围内
        return process.uid in 1000..9999
    }
    
    /**
     * 判断是否为普通应用进程 (UID >= 10000)
     */
    fun isUserAppProcess(process: ProcessInfo): Boolean {
        // 用户应用进程通常是 UID >= 10000
        return process.uid >= 10000
    }
}