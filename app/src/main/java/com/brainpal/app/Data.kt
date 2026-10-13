package com.brainpal.app

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

data class Topic(val id: String, val name: String, val emoji: String, val feeds: List<Pair<String, String>>, val strict: Boolean = false)

data class Item(
    val title: String,
    val link: String,
    val source: String,
    val date: Long,
    val summary: String,
    val img: String?,
    val topic: String,
)

data class Block(val type: String, val text: String) // type: h, p, li

data class Article(val title: String, val image: String?, val blocks: List<Block>, val url: String)

val TOPICS = listOf(
    Topic("nieuws", "Nieuws", "📰", listOf(
        "NOS" to "https://feeds.nos.nl/nosnieuwsalgemeen", "NU.nl" to "https://www.nu.nl/rss/Algemeen",
        "NRC" to "https://www.nrc.nl/rss/", "Volkskrant" to "https://www.volkskrant.nl/voorpagina/rss.xml",
        "DutchNews" to "https://www.dutchnews.nl/feed/", "BBC" to "https://feeds.bbci.co.uk/news/world/rss.xml",
        "The Guardian" to "https://www.theguardian.com/world/rss")),
    Topic("tech", "Tech", "💻", listOf(
        "Tweakers" to "https://tweakers.net/feeds/mixed.xml", "NU.nl" to "https://www.nu.nl/rss/Tech",
        "The Verge" to "https://www.theverge.com/rss/index.xml", "Ars Technica" to "https://feeds.arstechnica.com/arstechnica/index",
        "Wired" to "https://www.wired.com/feed/rss", "TechCrunch" to "https://techcrunch.com/feed/")),
    Topic("ai", "AI", "🤖", listOf(
        "MIT Tech Review" to "https://www.technologyreview.com/feed/", "Hacker News" to "https://hnrss.org/frontpage",
        "Ars Technica" to "https://arstechnica.com/ai/feed/", "OpenAI" to "https://openai.com/news/rss.xml")),
    Topic("wetenschap", "Wetenschap", "🔬", listOf(
        "NU.nl" to "https://www.nu.nl/rss/Wetenschap", "ScienceDaily" to "https://www.sciencedaily.com/rss/top/science.xml",
        "Quanta" to "https://api.quantamagazine.org/feed/", "New Scientist" to "https://www.newscientist.com/feed/home/",
        "Nature" to "https://www.nature.com/nature.rss", "Phys.org" to "https://phys.org/rss-feed/")),
    Topic("ruimte", "Ruimte", "🪐", listOf(
        "NASA" to "https://www.nasa.gov/feed/", "Space.com" to "https://www.space.com/feeds/all",
        "SpaceNews" to "https://spacenews.com/feed/", "Universe Today" to "https://www.universetoday.com/feed",
        "ESA" to "https://www.esa.int/rssfeed/Our_Activities/Space_Science")),
    Topic("economie", "Economie", "💶", listOf(
        "NOS" to "https://feeds.nos.nl/nosnieuwseconomie", "NU.nl" to "https://www.nu.nl/rss/Economie",
        "BBC" to "https://feeds.bbci.co.uk/news/business/rss.xml", "CNBC" to "https://www.cnbc.com/id/10001147/device/rss/rss.html")),
    Topic("sport", "Sport", "⚽", listOf(
        "NOS" to "https://feeds.nos.nl/nossportalgemeen", "NU.nl" to "https://www.nu.nl/rss/Sport",
        "BBC Sport" to "https://feeds.bbci.co.uk/sport/rss.xml", "ESPN" to "https://www.espn.com/espn/rss/news")),
    Topic("cultuur", "Cultuur", "🎬", listOf(
        "NOS" to "https://feeds.nos.nl/nosnieuwscultuurenmedia", "NU.nl" to "https://www.nu.nl/rss/Entertainment",
        "NU.nl Film" to "https://www.nu.nl/rss/Film", "The Guardian" to "https://www.theguardian.com/culture/rss")),
    Topic("gezondheid", "Gezondheid", "🫀", listOf(
        "NU.nl" to "https://www.nu.nl/rss/Gezondheid", "ScienceDaily" to "https://www.sciencedaily.com/rss/health_medicine.xml",
        "BBC" to "https://feeds.bbci.co.uk/news/health/rss.xml", "WHO" to "https://www.who.int/rss-feeds/news-english.xml")),
    Topic("gaming", "Gaming", "🎮", listOf(
        "IGN" to "https://www.ign.com/rss/articles/feed?tags=games", "GameSpot" to "https://www.gamespot.com/feeds/mashup/",
        "Eurogamer" to "https://www.eurogamer.net/feed", "PC Gamer" to "https://www.pcgamer.com/rss/",
        "Kotaku" to "https://kotaku.com/rss", "Rock Paper Shotgun" to "https://www.rockpapershotgun.com/feed",
        "Nintendo Life" to "https://www.nintendolife.com/feeds/latest", "Push Square" to "https://www.pushsquare.com/feeds/latest",
        "GamesIndustry.biz" to "https://www.gamesindustry.biz/feed"), strict = true),
    Topic("opmerkelijk", "Opmerkelijk", "✨", listOf(
        "NOS" to "https://feeds.nos.nl/nosnieuwsopmerkelijk", "NU.nl" to "https://www.nu.nl/rss/Opmerkelijk")),
)

fun topicOf(id: String): Topic? = TOPICS.firstOrNull { it.id == id }

/** Site vraagt eerst om een cookie-keuze (DPG Media: Tweakers, NU.nl, Volkskrant). */
class ConsentGateException : IOException("consent")

/** Geeft per site alleen de cookies mee die de gebruiker zelf in de webweergave heeft gekregen. */
private object WebViewCookies : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // Cookies uit omleidingen bewaren, anders blijft de site ons doorsturen naar de cookiepagina.
        val cm = runCatching { android.webkit.CookieManager.getInstance() }.getOrNull() ?: return
        cookies.forEach { runCatching { cm.setCookie(url.toString(), it.toString()) } }
        runCatching { cm.flush() }
    }
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val raw = runCatching { android.webkit.CookieManager.getInstance().getCookie(url.toString()) }.getOrNull() ?: return emptyList()
        return raw.split(";").mapNotNull { p ->
            val kv = p.trim().split("=", limit = 2)
            if (kv.size == 2 && kv[0].isNotEmpty()) runCatching { Cookie.Builder().name(kv[0]).value(kv[1]).domain(url.host).build() }.getOrNull() else null
        }
    }
}

object Net {
    private const val FEED_UA = "BrainPal/1.0 (+personal news reader)"
    const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private var cacheDir: java.io.File? = null

    /** Wordt bij het starten van de app aangeroepen: zet de schijfcache aan voor (voorwaardelijke) verzoeken. */
    fun init(dir: java.io.File) { cacheDir = dir }

    /**
     * Gedeelde client. De schijfcache zorgt dat OkHttp bij een feed die niet veranderd is alleen "ongewijzigd" (304) terugkrijgt:
     * minder data, minder rekenwerk en minder batterij. De dispatcher beperkt het aantal gelijktijdige verbindingen.
     */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .cookieJar(WebViewCookies)
            .dispatcher(okhttp3.Dispatcher().apply { maxRequests = 12; maxRequestsPerHost = 4 })
            .apply { cacheDir?.let { cache(okhttp3.Cache(java.io.File(it, "http"), 25L * 1024 * 1024)) } }
            .build()
    }

    /** Geeft (body, eindurl) terug. */
    fun get(url: String, asBrowser: Boolean): Pair<String, String> {
        val req = Request.Builder().url(url)
            .header("User-Agent", if (asBrowser) BROWSER_UA else FEED_UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "nl,en;q=0.8")
            .build()
        val call = client.newCall(req)
        val response = try {
            call.execute()
        } catch (e: java.net.ProtocolException) {
            // "Too many follow-up requests": de site stuurt ons telkens terug naar de cookiepagina.
            if (e.message?.contains("follow-up", ignoreCase = true) == true) throw ConsentGateException()
            throw e
        }
        response.use { r ->
            if (r.request.url.host.endsWith("dpgmedia.nl")) throw ConsentGateException()
            if (!r.isSuccessful) throw IOException("De bron antwoordde met ${r.code}")
            val body = r.body?.string() ?: throw IOException("Lege reactie")
            return body.take(2_500_000) to r.request.url.toString()
        }
    }
}

private fun parseDate(s: String?): Long {
    val t = s?.trim().orEmpty()
    if (t.isEmpty()) return System.currentTimeMillis()
    return runCatching { ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }
        .recoverCatching { Instant.parse(t).toEpochMilli() }
        .getOrDefault(System.currentTimeMillis())
}

private fun plain(s: String): String = Jsoup.parse(Jsoup.parse(s).text()).text().replace(Regex("\\s+"), " ").trim()

private fun shorten(s: String, max: Int = 240): String {
    if (s.length <= max) return s
    val cut = s.substring(0, max)
    val end = maxOf(cut.lastIndexOf(". "), cut.lastIndexOf("! "), cut.lastIndexOf("? "))
    return if (end > 100) cut.substring(0, end + 1) else cut.replace(Regex("\\s+\\S*$"), "") + "…"
}

fun fetchFeed(source: String, url: String, topic: String): List<Item> {
    val (body, _) = Net.get(url, asBrowser = false)
    // Sommige feeds zijn enorm (honderden berichten). We willen er maar 12, dus parseer alleen het begin: scheelt tijd en batterij.
    val doc = Jsoup.parse(if (body.length > 250_000) body.substring(0, 250_000) else body, "", Parser.xmlParser())
    return doc.select("item, entry").mapNotNull { e ->
        val title = plain(e.selectFirst("title")?.text().orEmpty())
        val linkEl = e.select("link").firstOrNull { it.hasAttr("href") && it.attr("rel") != "self" } ?: e.selectFirst("link")
        val link = (linkEl?.attr("href")?.takeIf { it.isNotBlank() } ?: linkEl?.text()).orEmpty().trim()
        if (title.isEmpty() || !link.startsWith("http")) return@mapNotNull null
        val catList = e.select("category").map { it.text() }
        val cats = catList.joinToString("|")
        if (isAd(title, link, cats)) return@mapNotNull null // advertenties en kortingscodes
        if (topicOf(topic)?.strict == true && offTopic(title, link, catList)) return@mapNotNull null // film/tv in een gamesfeed
        val date = parseDate(
            (e.selectFirst("pubDate") ?: e.selectFirst("published") ?: e.selectFirst("updated")
                ?: e.getElementsByTag("dc:date").firstOrNull())?.text()
        )
        val rawDesc = (e.selectFirst("description") ?: e.selectFirst("summary") ?: e.getElementsByTag("content:encoded").firstOrNull()
            ?: e.selectFirst("content"))?.text().orEmpty()
        var sum = plain(rawDesc)
        if (sum.startsWith(title, ignoreCase = true)) sum = sum.substring(title.length).trimStart(' ', ':', '.', '-', '–', '—')
        val img = (e.getElementsByTag("media:content") + e.getElementsByTag("media:thumbnail") + e.select("enclosure"))
            .firstOrNull { el ->
                val u = el.attr("url")
                val type = el.attr("type")
                u.isNotBlank() && el.attr("medium") != "video" && !type.startsWith("video") && !type.startsWith("audio") &&
                    (type.startsWith("image") || IMG_EXT.containsMatchIn(u))
            }?.attr("url")
            ?: Jsoup.parse(rawDesc).selectFirst("img[src]")?.attr("src") // plaatje in de tekst van het bericht
        Item(title, link, source, date, shorten(sum), https(img), topic)
    }.sortedByDescending { it.date }.take(12)
}

private val IMG_EXT = Regex("\\.(jpe?g|png|webp)", RegexOption.IGNORE_CASE)

/** Android blokkeert gewone http-verbindingen, dus plaatjes via http falen stilletjes. Upgrade naar https. */
fun https(u: String?): String? = when {
    u.isNullOrBlank() -> null
    u.startsWith("//") -> "https:$u"
    u.startsWith("http://") -> "https://" + u.removePrefix("http://")
    else -> u
}

/** Duidelijke reclame: kortingscodes en gesponsorde berichten. Altijd weglaten. */
private val AD_STRONG = Regex("promo codes?|coupons?|discount codes?|sponsored|advertorial|partner ?content", RegexOption.IGNORE_CASE)
private val AD_STRONG_LINK = Regex("promo-codes?|coupon|discount-codes?|/sponsored|/advertorial", RegexOption.IGNORE_CASE)
private val AD_STRONG_CAT = Regex("sponsored|advertis", RegexOption.IGNORE_CASE)

/** Mogelijk koop-tips ("beste deals"). Alleen weglaten als het geen review of test is: daar komen deze woorden ook legitiem in voor. */
private val AD_WEAK = Regex("[0-9]+% off|deals? of the (day|week)|black friday|best .{0,40}deals?|deal alert", RegexOption.IGNORE_CASE)
private val AD_WEAK_LINK = Regex("/deals?/", RegexOption.IGNORE_CASE)
private val AD_WEAK_CAT = Regex("deals", RegexOption.IGNORE_CASE)
private val REVIEWISH = Regex("review|hands-on|hands on|tested|preview|impressions|getest", RegexOption.IGNORE_CASE)

private fun isAd(title: String, link: String, cats: String): Boolean {
    if (AD_STRONG.containsMatchIn(title) || AD_STRONG_LINK.containsMatchIn(link) || AD_STRONG_CAT.containsMatchIn(cats)) return true
    if (REVIEWISH.containsMatchIn(title) || link.contains("/review", ignoreCase = true) || REVIEWISH.containsMatchIn(cats)) return false
    return AD_WEAK.containsMatchIn(title) || AD_WEAK_LINK.containsMatchIn(link) || AD_WEAK_CAT.containsMatchIn(cats)
}

private val JUNK = Regex(
    "^(advertentie|lees ook|lees meer|volg ons|abonneer|©|copyright|deel dit|share|related|aanmelden|inloggen|meld je aan|" +
        "edited by|reviewed by|see more|the original article|this is today's edition|discover the cosmos|introduction$)",
    RegexOption.IGNORE_CASE
)

private fun findBody(o: Any?, depth: Int = 0): String {
    if (o == null || depth > 6) return ""
    when (o) {
        is JSONArray -> for (i in 0 until o.length()) findBody(o.opt(i), depth + 1).let { if (it.isNotEmpty()) return it }
        is JSONObject -> {
            val b = o.optString("articleBody")
            if (b.length > 200) return b
            for (k in o.keys()) findBody(o.opt(k), depth + 1).let { if (it.isNotEmpty()) return it }
        }
    }
    return ""
}

/** Haalt de leesbare tekst uit een artikelpagina. Geeft null als er te weinig tekst is (betaalmuur, cookie-pagina, enz.). */
fun extractArticle(html: String, url: String): Article? {
    val doc = Jsoup.parse(html, url)
    val title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() } ?: doc.title()
    val image = https(doc.selectFirst("meta[property=og:image]")?.absUrl("content")?.takeIf { it.isNotBlank() })
    var ldBody = ""
    for (s in doc.select("script[type=application/ld+json]")) {
        val raw = s.data().trim()
        val parsed: Any? = runCatching { if (raw.startsWith("[")) JSONArray(raw) else JSONObject(raw) }.getOrNull()
        ldBody = findBody(parsed)
        if (ldBody.isNotEmpty()) break
    }
    doc.select("script,style,noscript,svg,form,nav,header,footer,aside,iframe,button,figcaption,select").remove()
    val root = doc.select("article").maxByOrNull { it.text().length } ?: doc.selectFirst("main") ?: doc.body()

    var blocks = ArrayList<Block>()
    var last = ""
    for (el in root.select("h2,h3,h4,p,li")) {
        val text = el.text().replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty() || text == last || JUNK.containsMatchIn(text)) continue
        if (text.contains("cookie", true) && text.length < 200) continue
        if (el.tagName().startsWith("h")) {
            if (text.length < 3 || text.length > 140 || text == title) continue
            blocks.add(Block("h", text))
        } else {
            if (text.length < 40) continue
            blocks.add(Block(if (el.tagName() == "li") "li" else "p", text))
        }
        last = text
    }
    while (blocks.isNotEmpty() && blocks.last().type == "h") blocks.removeAt(blocks.size - 1)
    var chars = blocks.filter { it.type != "h" }.sumOf { it.text.length }
    if (ldBody.length > chars * 1.2 || chars < 500) {
        val paras = ldBody.split(Regex("\\n+|(?<=[.!?])\\s{2,}")).map { it.trim() }.filter { it.length >= 40 }
        val c2 = paras.sumOf { it.length }
        if (c2 > chars) {
            blocks = ArrayList(paras.map { Block("p", it) })
            chars = c2
        }
    }
    if (chars < 400) return null
    return Article(title, image, blocks, url)
}

/** Voor onderwerpen met `strict = true` (Gaming): berichten over films en tv weglaten die toch in de feed van een gamesite staan. */
private val NON_GAME_CATS = setOf("entertainment", "movies", "movie", "film", "tv", "television", "what to watch", "anime")
private val NON_GAME_TEXT = Regex(
    "(?<![a-z])(netflix|paramount[+]?|hbo max|prime video|disney[+]|box office|movies?|films?|tv series|tv show|tv-serie|episodes?|season finale)(?![a-z])",
    RegexOption.IGNORE_CASE
)

private fun offTopic(title: String, link: String, cats: List<String>): Boolean =
    cats.any { it.trim().lowercase() in NON_GAME_CATS } || NON_GAME_TEXT.containsMatchIn(title) || NON_GAME_TEXT.containsMatchIn(link)
