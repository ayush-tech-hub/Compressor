package com.compressx.app

import android.app.Application
import android.os.StrictMode
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class CompressXApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize PDFBox font cache
        PDFBoxResourceLoader.init(applicationContext)

        // Enable StrictMode for debug builds
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }
    }
}
