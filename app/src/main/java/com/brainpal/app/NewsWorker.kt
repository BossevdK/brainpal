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
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
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

/** Draait elke ~2 uur op de achtergrond. Stuurt, afhankelijk van je keuze, één melding per dag of hooguit één per 3 uur, met de beste vijf nieuwe berichten voor jou. */
class NewsWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val prefs = ctx.getSharedPreferences("brainpal", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notify", false)) return Result.success()
        val topics = prefs.getString("topics", "")!!.split(",").mapNotNull { topicOf(it) }
        if (topics.isEmpty()) return Result.success()
        val hour = LocalTime.now().hour
        if (hour >= 22 || hour < 8) return Result.success() // stille uren

        val now = System.currentTimeMillis()
        val mode = prefs.getString("notifyMode", "daily")!! // "daily" = eenmaal per dag, "often" = een paar keer per dag
        val today = LocalDate.now().toEpochDay()
        if (mode == "daily" && prefs.getLong("notifDay", -1L) == today) return Result.success()            // vandaag al gehad
        if (mode == "often" && now - prefs.getLong("notifAt", 0L) < 3 * 3600_000L) return Result.success() // minstens 3 uur ertussen

        // Alleen berichten van de laatste 24 uur, van na je laatste melding, na het aanzetten van meldingen
        // en na je laatste bezoek aan de app (wat je daar hebt kunnen zien hoeft niet nog eens gemeld).
        val since = maxOf(now - 24 * 3600_000L, prefs.getLong("notifBase", 0L), prefs.getLong("lastVisit", 0L))

        val so = JSONObject(prefs.getString("srcOverride", "{}")!!)
        val items = coroutineScope {
            topics.flatMap { t -> t.sources.filter { so.optBoolean(t.id + "/" + it.name, it.default) }.map { s -> async(Dispatchers.IO) { runCatching { fetchFeed(s, t.id) }.getOrNull() } } }
                .awaitAll().filterNotNull().flatten()
        }
        if (items.isEmpty()) return Result.retry()

        val votes = JSONObject(prefs.getString("votes", "{}")!!)
        val meta = JSONObject(prefs.getString("vmeta", "{}")!!)
        val readArr = JSONArray(prefs.getString("read", "[]")!!)
        val read = HashSet<String>().also { s -> for (i in 0 until readArr.length()) s.add(readArr.getString(i)) }
        val w = HashMap<String, Double>()
        for (k in votes.keys()) meta.optJSONArray(k)?.let { m ->
            val v = votes.optInt(k)
            w[m.getString(0)] = (w[m.getString(0)] ?: 0.0) + v
            w["s:" + m.getString(1)] = (w["s:" + m.getString(1)] ?: 0.0) + v
        }
        val candidates = items.distinctBy { it.link }
            .filter { it.date > since && votes.optInt(it.link) != -1 && it.link !in read }
            .sortedByDescending { 6 * tanh((w[it.topic] ?: 0.0) / 3) + 3 * tanh((w["s:${it.source}"] ?: 0.0) / 3) + it.date / 3.6e6 }

        // De beste vijf, met hooguit twee per bron, zodat het geen lijst van één site wordt.
        val picked = ArrayList<Item>()
        val perSource = HashMap<String, Int>()
        for (i in candidates) {
            if (picked.size >= 5) break
            val c = perSource[i.source] ?: 0
            if (c >= 2) continue
            picked.add(i); perSource[i.source] = c + 1
        }
        if (picked.size < (if (mode == "daily") 1 else 2)) return Result.success() // niets de moeite waard: de melding wacht

        notify(picked, mode)
        prefs.edit().putLong("notifBase", now).putLong("notifAt", now).putLong("notifDay", today).apply()
        return Result.success()
    }

    private suspend fun notify(picked: List<Item>, mode: String) = withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return@withContext
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Nieuws", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (mode == "daily") "Je nieuws van vandaag" else "Nieuw voor jou"
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title).setSummaryText("Open BrainPal voor meer")
        picked.forEach { style.addLine("${it.source}: ${it.title}") }
        val notif = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_fg)
            .setContentTitle(title)
            .setContentText(picked.first().title) // bewust geen aantal: een getal als "198 nieuwe berichten" zegt je niets
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(1, notif) } // SecurityException als de toestemming ontbreekt
    }
}
