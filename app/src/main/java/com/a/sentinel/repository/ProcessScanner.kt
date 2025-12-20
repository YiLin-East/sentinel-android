package com.a.sentinel.repository

import com.a.sentinel.data.ProcessInfo
import kotlinx.coroutines.*

object ProcessScanner {

    /**
     * 扫描进程，使用并发方式提高效率
     */
    suspend fun scan(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ProcessInfo>()
        val output = RootShell.exec("ls /proc | grep '^[0-9]'")

        // 使用并发方式处理每个进程，提高扫描速度
        val processJobs = mutableListOf<Deferred<ProcessInfo?>>()
        
        output.lines().forEach { pidStr ->
            try {
                val pid = pidStr.toInt()
                // 启动协程异步处理每个进程
                val job = async {
                    try {
                        val status = RootShell.exec("cat /proc/$pid/status")
                        val cmdline = RootShell.exec("cat /proc/$pid/cmdline")

                        val uidLine = status.lines().firstOrNull { it.startsWith("Uid:") }
                        val uid = uidLine?.split("\\s+".toRegex())?.getOrNull(1)?.toInt() ?: return@async null

                        // 包含系统进程(UID < 10000)和第三方App进程(UID >= 10000)
                        val name = cmdline.replace('\u0000', ' ').trim()
                        if (name.isBlank()) return@async null

                        ProcessInfo(
                            pid = pid,
                            uid = uid,
                            processName = name,
                            packageName = extractPackage(name)
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
                processJobs.add(job)
            } catch (_: Exception) {}
        }

        // 等待所有协程完成并收集结果
        processJobs.forEach { job ->
            try {
                job.await()?.let { processInfo ->
                    result.add(processInfo)
                }
            } catch (_: Exception) {}
        }

        result
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
     * 判断是否为系统进程
     */
    fun isSystemProcess(process: ProcessInfo): Boolean {
        return process.uid < 10000
    }
}