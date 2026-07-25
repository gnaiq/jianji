package com.example.jianji

import android.app.Application
import com.example.jianji.utils.BackupScheduler

class JianjiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BackupScheduler.ensureScheduled(this)
    }
}