package com.brainpal.app

import android.app.Application
import android.content.Intent
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlin.system.exitProcess

class BrainPalApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        installCrashScreen()
        Net.init(cacheDir)
        Net.appContext = applicationContext
    }

    /**
     * Bij een crash: toon de foutmelding in een los scherm (eigen proces), bewaar hem voor het venster bij de volgende start,
     * en gooi de bewaarde feed weg zodat een kapotte cache geen crashlus kan veroorzaken.
     */
    private fun installCrashScreen() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            val text = runCatching { android.util.Log.getStackTraceString(e).take(4000) }.getOrDefault(e.toString())
            runCatching {
                java.io.File(filesDir, "crash.txt").writeText(text)
                java.io.File(filesDir, "feed-cache.json").delete()
            }
            val started = runCatching {
                startActivity(
                    Intent(this, CrashActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .putExtra("t", text)
                )
            }.isSuccess
            if (started) {
                runCatching { Thread.sleep(500) } // het crashscherm even de tijd geven om te starten
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            } else previous?.uncaughtException(thread, e)
        }
    }

    /** Eén gedeelde plaatjesloader: dezelfde verbindingen als de rest van de app, met geheugen- en schijfcache. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(Net.client)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(60L * 1024 * 1024).build() }
        .crossfade(150)
        .respectCacheHeaders(false) // nieuwsplaatjes veranderen niet: altijd uit de cache als we ze al hebben
        .build()
}
