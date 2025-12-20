package com.a.sentinel.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SystemWhitelist {
    private const val TAG = "SystemWhitelist"
    private const val PREF_NAME = "whitelist_prefs"
    private const val USER_WHITELIST_KEY = "user_whitelist"
    private const val USER_BLACKLIST_KEY = "user_blacklist"
    
    private var sharedPreferences: SharedPreferences? = null
    
    private val defaultWhitelist = setOf(
        "android",
        "system",
        "com.android.systemui",
        "com.android.phone"
    )
    
    private val userWhitelist = mutableSetOf<String>()
    private val userBlacklist = mutableSetOf<String>()
    
    fun initialize(context: Context) {
        try {
            val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            sharedPreferences = EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted shared preferences, falling back to regular", e)
            sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
        
        loadUserWhitelist()
        loadUserBlacklist()
    }
    
    private fun getSharedPreferences(): SharedPreferences? {
        return sharedPreferences
    }
    
    fun getAll(): Set<String> {
        val all = mutableSetOf<String>()
        all.addAll(defaultWhitelist)
        all.addAll(userWhitelist)
        return all
    }
    
    fun addToUserWhitelist(packageName: String) {
        userWhitelist.add(packageName)
        saveUserWhitelist()
    }
    
    fun removeFromUserWhitelist(packageName: String) {
        userWhitelist.remove(packageName)
        saveUserWhitelist()
    }
    
    fun isInWhitelist(packageName: String?): Boolean {
        if (packageName == null) return false
        return getAll().contains(packageName)
    }
    
    // 黑名单相关方法
    fun getUserBlacklist(): Set<String> {
        return userBlacklist.toSet()
    }
    
    fun addToUserBlacklist(packageName: String) {
        userBlacklist.add(packageName)
        saveUserBlacklist()
    }
    
    fun removeFromUserBlacklist(packageName: String) {
        userBlacklist.remove(packageName)
        saveUserBlacklist()
    }
    
    fun isInBlacklist(packageName: String?): Boolean {
        if (packageName == null) return false
        return userBlacklist.contains(packageName)
    }
    
    private fun saveUserWhitelist() {
        val prefs = getSharedPreferences()
        if (prefs != null) {
            val editor = prefs.edit()
            editor.putStringSet(USER_WHITELIST_KEY, userWhitelist)
            editor.apply()
            Log.d(TAG, "Saved user whitelist: $userWhitelist")
        } else {
            Log.e(TAG, "Shared preferences not initialized!")
        }
    }
    
    private fun loadUserWhitelist() {
        val prefs = getSharedPreferences()
        if (prefs != null) {
            val savedWhitelist = prefs.getStringSet(USER_WHITELIST_KEY, emptySet())
            userWhitelist.clear()
            if (savedWhitelist != null) {
                userWhitelist.addAll(savedWhitelist)
            }
            Log.d(TAG, "Loaded user whitelist: $userWhitelist")
        } else {
            Log.e(TAG, "Shared preferences not initialized!")
        }
    }
    
    private fun saveUserBlacklist() {
        val prefs = getSharedPreferences()
        if (prefs != null) {
            val editor = prefs.edit()
            editor.putStringSet(USER_BLACKLIST_KEY, userBlacklist)
            editor.apply()
            Log.d(TAG, "Saved user blacklist: $userBlacklist")
        } else {
            Log.e(TAG, "Shared preferences not initialized!")
        }
    }
    
    private fun loadUserBlacklist() {
        val prefs = getSharedPreferences()
        if (prefs != null) {
            val savedBlacklist = prefs.getStringSet(USER_BLACKLIST_KEY, emptySet())
            userBlacklist.clear()
            if (savedBlacklist != null) {
                userBlacklist.addAll(savedBlacklist)
            }
            Log.d(TAG, "Loaded user blacklist: $userBlacklist")
        } else {
            Log.e(TAG, "Shared preferences not initialized!")
        }
    }
}