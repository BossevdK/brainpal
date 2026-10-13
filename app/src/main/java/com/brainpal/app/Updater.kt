package com.brainpal.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** GitHub-repository waar nieuwe versies als "Release" met een .apk-bestand worden gezet. */
const val UPDATE_REPO = "BossevdK/brainpal"

data class UpdateInfo(val version: String, val notes: String, val url: String, val size: Long, val sha256: String?)

private fun versionParts(v: String): List<Int> =
    v.trim().removePrefix("v").removePrefix("V").split(".", "-", "+").mapNotNull { it.toIntOrNull() }

/** Is `remote` een hoger versienummer dan `local`? ("1.10" is hoger dan "1.9"). */
fun isNewer(remote: String, local: String): Boolean {
    val a = versionParts(remote)
    val b = versionParts(local)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

fun currentVersion(ctx: Context): String =
    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0"

/** Vraagt GitHub om de nieuwste release. Geeft null als je al de nieuwste versie hebt. Gooit een fout bij netwerkproblemen. */
fun checkForUpdate(current: String): UpdateInfo? {
    val req = Request.Builder().url("https://api.github.com/repos/$UPDATE_REPO/releases/latest")
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "BrainPal")
        .build()
    Net.client.newCall(req).execute().use { r ->
        if (r.code == 404) return null // nog geen release
        if (!r.isSuccessful) throw IOException("GitHub antwoordde met ${r.code}")
        val o = JSONObject(r.body?.string() ?: throw IOException("Lege reactie"))
        val tag = o.optString("tag_name")
        if (tag.isEmpty() || !isNewer(tag, current)) return null
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                val digest = a.optString("digest").removePrefix("sha256:").takeIf { it.length == 64 }
                return UpdateInfo(tag.removePrefix("v"), o.optString("body").trim(), a.getString("browser_download_url"), a.optLong("size"), digest)
            }
        }
        return null
    }
}

/** Downloadt de APK naar de cache van de app. `progress` krijgt een waarde van 0 tot 1. */
fun downloadUpdate(ctx: Context, info: UpdateInfo, progress: (Float) -> Unit): File {
    val uri = Uri.parse(info.url)
    if (uri.scheme != "https" || uri.host != "github.com") throw IOException("Onverwachte downloadlink")
    val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "BrainPal-${info.version}.apk")
    val req = Request.Builder().url(info.url).header("User-Agent", "BrainPal").build()
    Net.client.newBuilder().callTimeout(0, java.util.concurrent.TimeUnit.SECONDS).readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
        .newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Downloaden mislukt (${r.code})")
            val host = r.request.url.host
            if (!(host == "github.com" || host.endsWith(".githubusercontent.com"))) throw IOException("Onverwachte downloadserver")
            val body = r.body ?: throw IOException("Lege reactie")
            val total = body.contentLength().takeIf { it > 0 } ?: info.size
            val md = MessageDigest.getInstance("SHA-256")
            var done = 0L
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n); md.update(buf, 0, n); done += n
                        if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (info.size > 0 && done != info.size) { file.delete(); throw IOException("Download is niet compleet") }
            val hash = md.digest().joinToString("") { "%02x".format(it) }
            if (info.sha256 != null && !hash.equals(info.sha256, ignoreCase = true)) { file.delete(); throw IOException("Controlesom klopt niet, download is beschadigd") }
        }
    return file
}

/**
 * Opent het installatiescherm van Android. Geeft false als eerst toestemming nodig is voor "onbekende apps installeren":
 * dan opent de app de instelling en moet je daarna nog een keer op installeren tikken.
 * Android controleert zelf dat de update met dezelfde sleutel is ondertekend als de geïnstalleerde app.
 */
fun installApk(ctx: Context, file: File): Boolean {
    if (!ctx.packageManager.canRequestPackageInstalls()) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return false
    }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    ctx.startActivity(
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    return true
}
