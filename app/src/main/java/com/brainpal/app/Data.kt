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

/** Een nieuwsbron. `default`: staat standaard aan. `must`: alleen berichten waarvan de titel hierbij past. */
data class Source(val name: String, val url: String, val default: Boolean = true, val must: Regex? = null)

data class Topic(
    val id: String, val name: String, val emoji: String, val sources: List<Source>,
    val strict: Boolean = false, val reroute: List<Reroute> = emptyList(),
)

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

/** Alleen berichten waarvan de titel over AI gaat (voor bronnen die ook over andere dingen schrijven). */
private const val AI_WORDS = "(?<![a-z])(ai|a[.]i[.]|artificial intelligence|machine learning|deep learning|llms?|gpt[-0-9a-z]*|chatgpt|openai|anthropic|claude|gemini|deepseek|grok|mistral|llama|midjourney|sora|copilot|neural|chatbots?|generative|nvidia|robot(s|ics)?|diffusion)(?![a-z])"

/** Een bron schrijft soms over iets dat bij een ander onderwerp hoort. Past de titel (of bij `summary` de omschrijving), dan staat het bericht bij `to`, maar alleen als je dat onderwerp hebt gekozen. */
class Reroute(val to: String, val title: Regex, val summary: Regex? = null)

private const val AI_STRONG = "(?<![a-z])(openai|chatgpt|anthropic|claude|llms?|gpt[-0-9a-z]*|gemini|copilot|generative ai|artificial intelligence)(?![a-z])"
private const val GAME_WORDS = "(?<![a-z])(games?(?![-a-z])|gaming|gamers?|playstation|ps5|ps4|xbox|nintendo|switch 2|steam deck|steam machine|steam frame|steam sale|fortnite|minecraft|esports|twitch|call of duty|gta|zelda|mario|pok[eé]mon|elden ring|riot games|epic games|ubisoft|activision|blizzard|bethesda|rockstar|capcom|konami|sega|square enix|meta quest|oculus)(?![a-z])"
private const val SPACE_WORDS = "(?<![a-z])(nasa|esa|spacex|space station|astronauts?|mars|moon|lunar|rockets?|satellites?|telescopes?|james webb|jwst|hubble|galaxy|galaxies|exoplanets?|asteroids?|comets?|black holes?|supernova|cosmic|cosmos|solar system|big bang|dark matter|artemis|starship|starlink|maan|raket|sterrenkunde|heelal|melkweg|zwart gat|planeet|ruimtevaart)(?![a-z])"
private fun rx(s: String) = Regex(s, RegexOption.IGNORE_CASE)
private val AI_REROUTE = Reroute("ai", rx(AI_WORDS), rx(AI_STRONG))
private val GAME_REROUTE = Reroute("gaming", rx(GAME_WORDS))
private val SPACE_REROUTE = Reroute("ruimte", rx(SPACE_WORDS))

private fun s(name: String, url: String, on: Boolean = true, must: String? = null) =
    Source(name, url, on, must?.let { Regex(it, RegexOption.IGNORE_CASE) })

val TOPICS = listOf(
    Topic("nieuws", "Nieuws", "📰", listOf(
        s("NOS", "https://feeds.nos.nl/nosnieuwsalgemeen"),
        s("NU.nl", "https://www.nu.nl/rss/Algemeen"),
        s("NRC", "https://www.nrc.nl/rss/"),
        s("Volkskrant", "https://www.volkskrant.nl/voorpagina/rss.xml"),
        s("DutchNews", "https://www.dutchnews.nl/feed/"),
        s("BBC", "https://feeds.bbci.co.uk/news/world/rss.xml"),
        s("The Guardian", "https://www.theguardian.com/world/rss"),
        s("RTL Nieuws", "https://www.rtl.nl/rss.xml", on = false),
        s("Telegraaf", "https://www.telegraaf.nl/rss", on = false),
        s("Parool", "https://www.parool.nl/rss.xml", on = false),
        s("AD", "https://www.ad.nl/rss.xml", on = false),
        s("Trouw", "https://www.trouw.nl/rss.xml", on = false),
        s("NOS Politiek", "https://feeds.nos.nl/nosnieuwspolitiek", on = false),
        s("NOS Binnenland", "https://feeds.nos.nl/nosnieuwsbinnenland", on = false),
        s("NOS Buitenland", "https://feeds.nos.nl/nosnieuwsbuitenland", on = false),
        s("Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml", on = false),
        s("DW", "https://rss.dw.com/rdf/rss-en-all", on = false),
        s("NPR", "https://feeds.npr.org/1001/rss.xml", on = false)
    )),
    Topic("tech", "Tech", "💻", listOf(
        s("Tweakers", "https://tweakers.net/feeds/mixed.xml"),
        s("NU.nl", "https://www.nu.nl/rss/Tech"),
        s("The Verge", "https://www.theverge.com/rss/index.xml"),
        s("Ars Technica", "https://feeds.arstechnica.com/arstechnica/index"),
        s("Wired", "https://www.wired.com/feed/rss"),
        s("TechCrunch", "https://techcrunch.com/feed/"),
        s("Bright", "https://www.bright.nl/rss", on = false),
        s("iCulture", "https://www.iculture.nl/feed/", on = false),
        s("Androidplanet", "https://www.androidplanet.nl/feed/", on = false),
        s("Engadget", "https://www.engadget.com/rss.xml", on = false),
        s("9to5Google", "https://9to5google.com/feed/", on = false),
        s("TechRadar", "https://www.techradar.com/rss", on = false),
        s("The Register", "https://www.theregister.com/headlines.atom", on = false),
        s("BBC Tech", "https://feeds.bbci.co.uk/news/technology/rss.xml", on = false)
    ), reroute = listOf(AI_REROUTE, GAME_REROUTE)),
    Topic("ai", "AI", "🤖", listOf(
        s("MIT Tech Review", "https://www.technologyreview.com/feed/", must = AI_WORDS),
        s("Hacker News", "https://hnrss.org/frontpage", must = AI_WORDS),
        s("Ars Technica", "https://arstechnica.com/ai/feed/"),
        s("OpenAI", "https://openai.com/news/rss.xml"),
        s("The Verge AI", "https://www.theverge.com/rss/ai-artificial-intelligence/index.xml", on = false),
        s("TechCrunch AI", "https://techcrunch.com/category/artificial-intelligence/feed/", on = false),
        s("Google AI", "https://blog.google/technology/ai/rss/", on = false),
        s("Hugging Face", "https://huggingface.co/blog/feed.xml", on = false),
        s("MIT News AI", "https://news.mit.edu/rss/topic/artificial-intelligence2", on = false),
        s("Wired AI", "https://www.wired.com/feed/tag/ai/latest/rss", on = false)
    )),
    Topic("wetenschap", "Wetenschap", "🔬", listOf(
        s("NU.nl", "https://www.nu.nl/rss/Wetenschap"),
        s("ScienceDaily", "https://www.sciencedaily.com/rss/top/science.xml"),
        s("Quanta", "https://api.quantamagazine.org/feed/"),
        s("New Scientist", "https://www.newscientist.com/feed/home/"),
        s("Nature", "https://www.nature.com/nature.rss"),
        s("Phys.org", "https://phys.org/rss-feed/"),
        s("Scientias", "https://www.scientias.nl/feed/", on = false),
        s("Quest", "https://www.quest.nl/rss", on = false),
        s("BBC Science", "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml", on = false)
    ), reroute = listOf(AI_REROUTE, SPACE_REROUTE)),
    Topic("ruimte", "Ruimte", "🪐", listOf(
        s("NASA", "https://www.nasa.gov/feed/"),
        s("Space.com", "https://www.space.com/feeds/all"),
        s("SpaceNews", "https://spacenews.com/feed/"),
        s("Universe Today", "https://www.universetoday.com/feed"),
        s("ESA", "https://www.esa.int/rssfeed/Our_Activities/Space_Science")
    )),
    Topic("economie", "Economie", "💶", listOf(
        s("NOS", "https://feeds.nos.nl/nosnieuwseconomie"),
        s("NU.nl", "https://www.nu.nl/rss/Economie"),
        s("BBC", "https://feeds.bbci.co.uk/news/business/rss.xml"),
        s("CNBC", "https://www.cnbc.com/id/10001147/device/rss/rss.html")
    )),
    Topic("sport", "Sport", "⚽", listOf(
        s("NOS", "https://feeds.nos.nl/nossportalgemeen"),
        s("NU.nl", "https://www.nu.nl/rss/Sport"),
        s("BBC Sport", "https://feeds.bbci.co.uk/sport/rss.xml"),
        s("ESPN", "https://www.espn.com/espn/rss/news"),
        s("NU Voetbal", "https://www.nu.nl/rss/Voetbal", on = false),
        s("BBC Football", "https://feeds.bbci.co.uk/sport/football/rss.xml", on = false)
    )),
    Topic("cultuur", "Cultuur", "🎬", listOf(
        s("NOS", "https://feeds.nos.nl/nosnieuwscultuurenmedia"),
        s("NU.nl", "https://www.nu.nl/rss/Entertainment"),
        s("NU.nl Film", "https://www.nu.nl/rss/Film"),
        s("The Guardian", "https://www.theguardian.com/culture/rss"),
        s("BBC Entertainment", "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml", on = false),
        s("Variety", "https://variety.com/feed/", on = false)
    )),
    Topic("gezondheid", "Gezondheid", "🫀", listOf(
        s("NU.nl", "https://www.nu.nl/rss/Gezondheid"),
        s("ScienceDaily", "https://www.sciencedaily.com/rss/health_medicine.xml"),
        s("BBC", "https://feeds.bbci.co.uk/news/health/rss.xml"),
        s("WHO", "https://www.who.int/rss-feeds/news-english.xml")
    )),
    Topic("gaming", "Gaming", "🎮", listOf(
        s("IGN", "https://www.ign.com/rss/articles/feed?tags=games"),
        s("GameSpot", "https://www.gamespot.com/feeds/mashup/"),
        s("Eurogamer", "https://www.eurogamer.net/feed"),
        s("PC Gamer", "https://www.pcgamer.com/rss/"),
        s("Kotaku", "https://kotaku.com/rss"),
        s("Rock Paper Shotgun", "https://www.rockpapershotgun.com/feed"),
        s("Nintendo Life", "https://www.nintendolife.com/feeds/latest"),
        s("Push Square", "https://www.pushsquare.com/feeds/latest"),
        s("GamesIndustry.biz", "https://www.gamesindustry.biz/feed"),
        s("Gamekings", "https://www.gamekings.tv/feed/", on = false)
    ), strict = true),
    Topic("opmerkelijk", "Opmerkelijk", "✨", listOf(
        s("NOS", "https://feeds.nos.nl/nosnieuwsopmerkelijk"),
        s("NU.nl", "https://www.nu.nl/rss/Opmerkelijk")
    )),
)

fun topicOf(id: String): Topic? = TOPICS.firstOrNull { it.id == id }

/** De site gaf een foutcode terug (bijvoorbeeld 401 of 403 bij een betaalmuur). */
class HttpException(val code: Int) : IOException("De bron antwoordde met $code")

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
    private const val BOT_UA = "BrainPal/1.0 (+https://github.com/BossevdK/brainpal)"
    private const val FEED_UA = "BrainPal/1.0 (+personal news reader)"
    const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private var cacheDir: java.io.File? = null

    /** Wordt bij het starten gezet; nodig om de gebruikersnaam-tekst van de ingebouwde browser op te vragen. */
    var appContext: android.content.Context? = null

    /**
     * Dezelfde gebruikersnaam-tekst als de ingebouwde webweergave. Loop je daar een controle van een site door, dan geldt die ook
     * voor de app, die deze tekst dan gebruikt bij het ophalen van artikelen.
     */
    val browserUa: String by lazy {
        runCatching { android.webkit.WebSettings.getDefaultUserAgent(appContext!!) }.getOrDefault(BROWSER_UA)
    }

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
        if (!asBrowser) return fetch(url, FEED_UA)
        return try {
            fetch(url, browserUa)
        } catch (e: HttpException) {
            // Sommige sites (bijvoorbeeld Phys.org) blokkeren nagemaakte browsers, maar laten een app door die zich eerlijk bekendmaakt.
            if (e.code == 403 || e.code == 503) fetch(url, BOT_UA) else throw e
        }
    }

    private fun fetch(url: String, ua: String): Pair<String, String> {
        val req = Request.Builder().url(url)
            .header("User-Agent", ua)
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
            if (!r.isSuccessful) throw HttpException(r.code)
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

fun fetchFeed(src: Source, topic: String): List<Item> {
    val (body, _) = try {
        Net.get(src.url, asBrowser = false)
    } catch (e: IOException) {
        // hnrss.org is soms even onbereikbaar: dan de officiële (eenvoudigere) Hacker News-feed
        if (src.name == "Hacker News") Net.get("https://news.ycombinator.com/rss", asBrowser = false) else throw e
    }
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
        if (src.must != null && !src.must.containsMatchIn(title)) return@mapNotNull null // bron die ook over andere dingen schrijft
        val date = parseDate(
            (e.selectFirst("pubDate") ?: e.selectFirst("published") ?: e.selectFirst("updated")
                ?: e.getElementsByTag("dc:date").firstOrNull())?.text()
        )
        val rawDesc = (e.selectFirst("description") ?: e.selectFirst("summary") ?: e.getElementsByTag("content:encoded").firstOrNull()
            ?: e.selectFirst("content"))?.text().orEmpty()
        var sum = plain(rawDesc)
        if (src.name == "Hacker News") sum = hnSummary(sum)
        if (sum.startsWith(title, ignoreCase = true)) sum = sum.substring(title.length).trimStart(' ', ':', '.', '-', '–', '—')
        val img = (e.getElementsByTag("media:content") + e.getElementsByTag("media:thumbnail") + e.select("enclosure"))
            .filter { el ->
                val u = el.attr("url")
                val type = el.attr("type")
                u.isNotBlank() && el.attr("medium") != "video" && !type.startsWith("video") && !type.startsWith("audio") &&
                    (type.startsWith("image") || IMG_EXT.containsMatchIn(u))
            }
            .maxByOrNull { it.attr("width").toIntOrNull() ?: 0 }?.attr("url") // meerdere formaten (The Guardian): de grootste
            ?: Jsoup.parse(rawDesc).selectFirst("img[src]")?.attr("src") // plaatje in de tekst van het bericht
            ?: (e.selectFirst("content") ?: e.getElementsByTag("content:encoded").firstOrNull())?.text()
                ?.let { Jsoup.parse(it).selectFirst("img[src]")?.attr("src") } // en in de volledige tekst (The Verge)
        Item(title, link, src.name, date, shorten(sum), https(img)?.let { sharper(it) }, topic)
    }.sortedByDescending { it.date }.take(12)
}

/** De Hacker News-feed zet alleen links en tellers in de omschrijving: maak er een korte regel van. */
private fun hnSummary(s: String): String {
    val points = Regex("Points: ([0-9]+)").find(s)?.groupValues?.get(1)
    val comments = Regex("# Comments: ([0-9]+)").find(s)?.groupValues?.get(1)
    return listOfNotNull(points?.let { "$it punten" }, comments?.let { "$it reacties" }).joinToString(" · ")
}

/** Sommige feeds geven alleen een klein voorbeeldplaatje (NU.nl 256 px, BBC 240 px, Volkskrant 320 px, Phys.org 90 px, GameSpot 300 px). Dezelfde sites hebben ook grotere versies. */
fun sharper(u: String): String = u
    .replace("_sqr256/", "_sqr1024/")
    .replace(Regex("""ichef\.bbci\.co\.uk/ace/standard/\d+/"""), "ichef.bbci.co.uk/ace/standard/1536/")
    .replace(Regex("""image\.volkskrant\.nl/(\d+)/feature-crop/\d+/\d+/"""), "image.volkskrant.nl/$1/feature-crop/1024/576/")
    .replace("/csz/news/tmb/", "/csz/news/800a/")
    .replace(Regex("""(gamespot\.com/wp-content/uploads/[^?]+\?w=)\d+""")) { it.groupValues[1] + "1024" }

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
        "edited by|reviewed by|this article has been reviewed|see more|the original article|this is today's edition|discover the cosmos|introduction$)",
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
