package com.brainpal.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlin.math.tanh

private const val WORK_NAME = "brainpal-news"
private const val CHANNEL = "nieuws"

fun scheduleNews(ctx: Context) {
    val req = PeriodicWorkRequestBuilder<NewsWorker>(2, TimeUnit.HOURS, 40, TimeUnit.MINUTES) // flexibel venster: Android mag het bundelen met ander werk
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
        .build()
    WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
}

fun cancelNews(ctx: Context) {
    WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
}

/** Draait elke ~2 uur op de achtergrond en meldt nieuwe artikelen in je onderwerpen (niet tussen 22:00 en 08:00). */
class NewsWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val prefs = ctx.getSharedPreferences("brainpal", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notify", false)) return Result.success()
        val topics = prefs.getString("topics", "")!!.split(",").mapNotNull { topicOf(it) }
        if (topics.isEmpty()) return Result.success()
        val hour = LocalTime.now().hour
        if (hour >= 22 || hour < 8) return Result.success() // stille uren: later alsnog melden

        val now = System.currentTimeMillis()
        val base = prefs.getLong("notifBase", 0L)
        if (base == 0L) { prefs.edit().putLong("notifBase", now).apply(); return Result.success() }

        val items = coroutineScope {
            topics.flatMap { t -> t.feeds.map { (s, u) -> async(Dispatchers.IO) { runCatching { fetchFeed(s, u, t.id) }.getOrNull() } } }
                .awaitAll().filterNotNull().flatten()
        }
        if (items.isEmpty()) return Result.retry()

        val votes = JSONObject(prefs.getString("votes", "{}")!!)
        val meta = JSONObject(prefs.getString("vmeta", "{}")!!)
        val w = HashMap<String, Double>()
        for (k in votes.keys()) meta.optJSONArray(k)?.let { m ->
            val v = votes.optInt(k)
            w[m.getString(0)] = (w[m.getString(0)] ?: 0.0) + v
            w["s:" + m.getString(1)] = (w["s:" + m.getString(1)] ?: 0.0) + v
        }
        val fresh = items.distinctBy { it.link }.filter { it.date > base && votes.optInt(it.link) != -1 }
            .sortedByDescending { 6 * tanh((w[it.topic] ?: 0.0) / 3) + 3 * tanh((w["s:${it.source}"] ?: 0.0) / 3) + it.date / 3.6e6 }
        if (fresh.isEmpty()) { prefs.edit().putLong("notifBase", now).apply(); return Result.success() }

        notify(fresh)
        prefs.edit().putLong("notifBase", now).apply()
        return Result.success()
    }

    private suspend fun notify(fresh: List<Item>) = withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return@withContext
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Nieuws", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val top = fresh.first()
        val n = fresh.size
        val style = NotificationCompat.InboxStyle().also { s -> fresh.take(5).forEach { s.addLine("${topicOf(it.topic)?.emoji ?: ""} ${it.title}") } }
        if (n > 5) style.setSummaryText("+ ${n - 5} meer")
        val notif = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_fg)
            .setContentTitle(if (n == 1) "Nieuw: ${top.source}" else "$n nieuwe berichten")
            .setContentText(top.title)
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(1, notif) } // SecurityException als de toestemming ontbreekt
    }
}
