package com.a.sentinel.repository

object ProcessKiller {

    fun forceStop(pkg: String) {
        RootShell.exec("am force-stop $pkg")
    }

    fun killUid(uid: Int) {
        RootShell.exec("killall -u $uid")
    }

    fun killPid(pid: Int) {
        RootShell.exec("kill -9 $pid")
    }
}
