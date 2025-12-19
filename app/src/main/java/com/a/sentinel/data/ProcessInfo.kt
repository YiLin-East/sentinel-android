package com.a.sentinel.data

data class ProcessInfo(
    val pid: Int,
    val uid: Int,
    val processName: String,
    val packageName: String?
)
