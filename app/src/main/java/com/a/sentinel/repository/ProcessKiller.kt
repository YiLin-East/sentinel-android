package com.a.sentinel.repository

object ProcessKiller {
    
    fun forceStop(pkg: String) {
        if (SystemWhitelist.isInWhitelist(pkg)) {
            // 不杀死白名单中的进程
            return
        }
        RootShell.exec("am force-stop $pkg")
    }

    fun killUid(uid: Int) {
        // 注意：这里需要额外检查UID对应的包是否在白名单中
        RootShell.exec("killall -u $uid")
    }

    fun killPid(pid: Int) {
        RootShell.exec("kill -9 $pid")
    }
}