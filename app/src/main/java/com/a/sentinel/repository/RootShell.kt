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
        val process = Runtime.getRuntime().exec("su")
        val os = DataOutputStream(process.outputStream)
        val input = BufferedReader(InputStreamReader(process.inputStream))

        os.writeBytes("$cmd\n")
        os.writeBytes("exit\n")
        os.flush()

        val result = StringBuilder()
        var line: String?
        while (input.readLine().also { line = it } != null) {
            result.append(line).append('\n')
        }

        process.waitFor()
        return result.toString()
    }
}