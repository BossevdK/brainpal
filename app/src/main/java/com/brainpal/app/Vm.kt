package com.brainpal.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.tanh

class Vm(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("brainpal", Context.MODE_PRIVATE)

    var topics by mutableStateOf(prefs.getString("topics", "")!!.split(",").filter { topicOf(it) != null })
    var filter by mutableStateOf<String?>(null)
    var items by mutableStateOf<List<Item>>(emptyList())
    var loading by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var updated by mutableStateOf(0L)
    var notify by mutableStateOf(prefs.getBoolean("notify", false))
    var autoCookies by mutableStateOf(prefs.getBoolean("autoCookies", false))

    val votes = mutableStateMapOf<String, Int>()
    val read = mutableStateMapOf<String, Boolean>()
    val saved = mutableStateListOf<Item>()

    /** Gelezen artikelen zakken pas bij het volgende vernieuwen naar onderen, niet terwijl je nog aan het lezen bent. */
    private var sunk by mutableStateOf<Set<String>>(emptySet())
    private val voteMeta = HashMap<String, Pair<String, String>>() // link -> (topic, bron)

    /** Tijdstip van het vorige bezoek: artikelen die daarna kwamen krijgen "Nieuw". */
    val prevVisit: Long = prefs.getLong("lastVisit", 0L)

    /** Maximaal 30 artikelen in het geheugen, de oudste vallen eruit. */
    private val articles = object : LinkedHashMap<String, Article?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Article?>?) = size > 30
    }

    private val cacheFile = java.io.File(app.filesDir, "feed-cache.json")

    /** Bewaart de laatste feed op schijf, zodat de app bij het starten meteen iets toont. */
    private fun saveCache(list: List<Item>) {
        runCatching {
            val a = JSONArray(); list.forEach { a.put(itemToJson(it)) }
            cacheFile.writeText(JSONObject().put("at", System.currentTimeMillis()).put("items", a).toString())
        }
    }

    private fun loadCache() {
        runCatching {
            if (!cacheFile.exists()) return
            val o = JSONObject(cacheFile.readText())
            val a = o.getJSONArray("items")
            val list = (0 until a.length()).map { itemFromJson(a.getJSONObject(it)) }.filter { it.topic in topics }
            if (list.isNotEmpty()) { items = list.distinctBy { it.link }; updated = o.getLong("at") }
        }
    }

    /** Wordt alleen opnieuw berekend als items, filter, stemmen of gelezen-status veranderen (niet bij elke hertekening). */
    val ranked: List<Item> by derivedStateOf { computeRanked() }
    private val savedLinks by derivedStateOf { saved.map { it.link }.toSet() }
    fun isSaved(link: String) = link in savedLinks

    init {
        prefs.edit().putLong("lastVisit", System.currentTimeMillis()).apply()
        runCatching {
            val v = JSONObject(prefs.getString("votes", "{}")!!)
            val m = JSONObject(prefs.getString("vmeta", "{}")!!)
            for (k in v.keys()) {
                votes[k] = v.getInt(k)
                m.optJSONArray(k)?.let { voteMeta[k] = it.getString(0) to it.getString(1) }
            }
            val r = JSONArray(prefs.getString("read", "[]")!!)
            for (i in 0 until r.length()) read[r.getString(i)] = true
            val s = JSONArray(prefs.getString("saved", "[]")!!)
            for (i in 0 until s.length()) saved.add(itemFromJson(s.getJSONObject(i)))
        }
        sunk = read.keys.toSet()
        loadCache()
        if (notify) scheduleNews(app)
        refreshIfStale()
        runCatching { java.io.File(app.cacheDir, "updates").deleteRecursively() } // oude gedownloade updates opruimen
    }

    fun switchNotify(on: Boolean) {
        notify = on
        val e = prefs.edit().putBoolean("notify", on)
        if (on) e.putLong("notifBase", System.currentTimeMillis()) // alleen nieuws van na het aanzetten melden
        e.apply()
        if (on) scheduleNews(getApplication()) else cancelNews(getApplication())
    }

    private fun itemToJson(i: Item) = JSONObject().put("t", i.title).put("l", i.link).put("s", i.source)
        .put("d", i.date).put("m", i.summary).put("i", i.img ?: "").put("p", i.topic)

    private fun itemFromJson(o: JSONObject) = Item(
        o.getString("t"), o.getString("l"), o.getString("s"), o.getLong("d"), o.getString("m"),
        o.getString("i").ifEmpty { null }, o.getString("p")
    )

    private fun persist() {
        val v = JSONObject(); val m = JSONObject()
        for ((k, x) in votes) {
            v.put(k, x)
            voteMeta[k]?.let { m.put(k, JSONArray().put(it.first).put(it.second)) }
        }
        val r = JSONArray(); read.keys.toList().takeLast(500).forEach { r.put(it) }
        val s = JSONArray(); saved.forEach { s.put(itemToJson(it)) }
        prefs.edit().putString("topics", topics.joinToString(",")).putString("votes", v.toString())
            .putString("vmeta", m.toString()).putString("read", r.toString()).putString("saved", s.toString()).apply()
    }

    fun chooseTopics(t: List<String>) {
        topics = t
        items = items.filter { it.topic in t }
        if (filter != null && filter !in t) filter = null
        persist()
        refresh()
    }

    fun refreshIfStale() {
        if (topics.isNotEmpty() && !loading && System.currentTimeMillis() - updated > 10 * 60_000) refresh()
    }

    fun refresh() {
        if (loading) return
        val wanted = topics.mapNotNull { topicOf(it) }
        if (wanted.isEmpty()) return
        loading = true; failed = false
        sunk = read.keys.toSet()
        viewModelScope.launch {
            val byTopic = HashMap<String, List<Item>>(items.groupBy { it.topic })
            var ok = 0
            wanted.map { t ->
                async(Dispatchers.IO) {
                    val lists = t.feeds.map { (src, url) ->
                        async { runCatching { fetchFeed(src, url, t.id) }.getOrNull() }
                    }.awaitAll()
                    val merged = lists.filterNotNull().flatten().distinctBy { it.link }.sortedByDescending { it.date }.take(40)
                    withContext(Dispatchers.Main) {
                        if (merged.isNotEmpty()) { byTopic[t.id] = merged; ok++ }
                        items = byTopic.values.flatten().distinctBy { it.link } // meteen tonen wat al binnen is; zelfde bericht in twee onderwerpen telt eenmaal
                    }
                }
            }.awaitAll()
            if (ok > 0) {
                updated = System.currentTimeMillis()
                val snapshot = items
                launch(Dispatchers.IO) { saveCache(snapshot) }
            }
            failed = ok == 0 && items.isEmpty()
            loading = false
        }
    }

    private fun weights(): Map<String, Double> {
        val w = HashMap<String, Double>()
        for ((l, v) in votes) voteMeta[l]?.let { (t, s) ->
            w[t] = (w[t] ?: 0.0) + v
            w["s:$s"] = (w["s:$s"] ?: 0.0) + v
        }
        return w
    }

    /** Nieuw eerst, met een duwtje voor onderwerpen en bronnen die je waardeert. */
    private fun computeRanked(): List<Item> {
        val w = weights(); val now = System.currentTimeMillis()
        return items.distinctBy { it.link }.filter { votes[it.link] != -1 && (filter == null || it.topic == filter) }
            .sortedByDescending {
                -(now - it.date) / 3.6e6 + 6 * tanh((w[it.topic] ?: 0.0) / 3) +
                    3 * tanh((w["s:${it.source}"] ?: 0.0) / 3) - (if (it.link in sunk) 100 else 0)
            }
    }

    fun vote(i: Item, up: Boolean) {
        voteMeta[i.link] = i.topic to i.source
        votes[i.link] = if (up) (if (votes[i.link] == 1) 0 else 1) else -1
        persist()
    }

    fun toggleSaved(i: Item) {
        val ex = saved.indexOfFirst { it.link == i.link }
        if (ex >= 0) saved.removeAt(ex) else saved.add(0, i)
        persist()
    }

    // --- Updates via GitHub ---
    var update by mutableStateOf<UpdateInfo?>(null)
    var updateStatus by mutableStateOf<String?>(null)
    var checkingUpdate by mutableStateOf(false)
    var downloadProgress by mutableStateOf<Float?>(null)
    val appVersion: String = currentVersion(app)
    private var pendingApk: java.io.File? = null

    /** Kijkt op GitHub of er een nieuwere versie is. Automatisch hooguit eens per 6 uur, handmatig altijd. */
    fun checkUpdate(manual: Boolean) {
        if (checkingUpdate) return
        if (!manual && System.currentTimeMillis() - prefs.getLong("updateChecked", 0L) < 6 * 3600_000L) return
        checkingUpdate = true
        if (manual) updateStatus = null
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { checkForUpdate(appVersion) } }
            checkingUpdate = false
            r.onSuccess {
                prefs.edit().putLong("updateChecked", System.currentTimeMillis()).apply()
                update = it
                if (manual) updateStatus = if (it == null) "Je hebt de nieuwste versie." else null
            }.onFailure {
                if (manual) updateStatus = "Controleren mislukt: ${it.message ?: "geen verbinding"}"
            }
        }
    }

    /** Downloadt de update en opent het installatiescherm van Android. */
    fun installUpdate() {
        val info = update ?: return
        if (downloadProgress != null) return
        updateStatus = null
        pendingApk?.takeIf { it.exists() }?.let { file ->
            // al gedownload (we wachtten op toestemming voor onbekende apps): alleen nog installeren
            runCatching { if (installApk(getApplication(), file)) pendingApk = null }
            return
        }
        downloadProgress = 0f
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { downloadUpdate(getApplication(), info) { p -> downloadProgress = p } } }
            downloadProgress = null
            r.onSuccess { file ->
                runCatching { installApk(getApplication(), file) }
                    .onSuccess { opened ->
                        if (!opened) {
                            pendingApk = file
                            updateStatus = "Zet bij de instelling die nu opent 'Toestaan' aan voor BrainPal, ga terug en tik nog een keer op Installeren."
                        }
                    }
                    .onFailure { updateStatus = "Installeren mislukt: ${it.message}" }
            }.onFailure { updateStatus = "Downloaden mislukt: ${it.message ?: "geen verbinding"}" }
        }
    }

    fun switchAutoCookies(on: Boolean) { autoCookies = on; prefs.edit().putBoolean("autoCookies", on).apply() }

    fun forget(link: String) { articles.remove(link) }

    fun markRead(i: Item) { read[i.link] = true; persist() }

    /** Haalt een artikel op voor de leesmodus. Null = kan niet in de app getoond worden. */
    suspend fun article(i: Item): Result<Article?> = withContext(Dispatchers.IO) {
        if (articles.containsKey(i.link)) return@withContext Result.success(articles[i.link])
        runCatching {
            val (html, finalUrl) = Net.get(i.link, asBrowser = true)
            extractArticle(html, finalUrl)
        }.onSuccess { articles[i.link] = it }
    }
}
