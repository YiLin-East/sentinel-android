package com.a.sentinel.repository

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RootShell {

    fun hasRoot(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec("su -c id")
            p.waitFor()
            p.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun exec(cmd: String): String {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            val input = BufferedReader(InputStreamReader(process.inputStream))
            val error = BufferedReader(InputStreamReader(process.errorStream))

            os.writeBytes("$cmd\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()

            val result = StringBuilder()
            var line: String?
            
            // 读取标准输出
            while (input.readLine().also { line = it } != null) {
                result.append(line).append('\n')
            }
            
            // 读取错误输出
            while (error.readLine().also { line = it } != null) {
                result.append("ERROR: ").append(line).append('\n')
            }

            process.waitFor()
            result.toString()
        } catch (e: Exception) {
            "Exception occurred: ${e.message}"
        } finally {
            process?.destroy()
        }
    }
}