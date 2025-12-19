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
}
