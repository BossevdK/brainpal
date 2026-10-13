// Haalt de nieuwsfeeds op en schrijft web/data/feed.json. Draait elke ~30 minuten in GitHub Actions (zie .github/workflows/pages.yml).
// Zelfde regels als de Android-app: advertenties weg, bij Gaming ook films en tv weg, max 12 berichten per bron en 40 per onderwerp.
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";

const topics = JSON.parse(readFileSync(new URL("./topics.json", import.meta.url), "utf8"));
const UA = "BrainPal/1.0 (+https://github.com/BossevdK/brainpal)";

/* ---------- tekst opschonen ---------- */
const decodeOnce = (s) => s
  .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
  .replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&#0?39;|&apos;/g, "'")
  .replace(/&nbsp;/g, " ")
  .replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCodePoint(parseInt(n, 16)))
  .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(+n))
  .replace(/&amp;/g, "&");
const plain = (s) => decodeOnce(decodeOnce(s)).replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").trim();
const tag = (x, t) => (x.match(new RegExp(`<${t}(?:\\s[^>]*)?>([\\s\\S]*?)</${t}>`, "i")) || [])[1] || "";
const attr = (x, t, a) => (x.match(new RegExp(`<${t}\\b[^>]*\\b${a}=["']([^"']+)["']`, "i")) || [])[1];

function shorten(s, max = 240) {
  if (s.length <= max) return s;
  const cut = s.slice(0, max);
  const end = Math.max(cut.lastIndexOf(". "), cut.lastIndexOf("! "), cut.lastIndexOf("? "));
  return end > 100 ? cut.slice(0, end + 1) : cut.replace(/\s+\S*$/, "") + "…";
}
const https = (u) => (!u ? null : u.startsWith("//") ? "https:" + u : u.startsWith("http://") ? "https://" + u.slice(7) : u);

/* ---------- filters (zelfde als in de Android-app) ---------- */
const AD_STRONG = /promo codes?|coupons?|discount codes?|sponsored|advertorial|partner ?content/i;
const AD_STRONG_LINK = /promo-codes?|coupon|discount-codes?|\/sponsored|\/advertorial/i;
const AD_STRONG_CAT = /sponsored|advertis/i;
const AD_WEAK = /[0-9]+% off|deals? of the (day|week)|black friday|best .{0,40}deals?|deal alert/i;
const AD_WEAK_LINK = /\/deals?\//i;
const AD_WEAK_CAT = /deals/i;
const REVIEWISH = /review|hands-on|hands on|tested|preview|impressions|getest/i;
function isAd(title, link, cats) {
  if (AD_STRONG.test(title) || AD_STRONG_LINK.test(link) || AD_STRONG_CAT.test(cats)) return true;
  if (REVIEWISH.test(title) || /\/review/i.test(link) || REVIEWISH.test(cats)) return false;
  return AD_WEAK.test(title) || AD_WEAK_LINK.test(link) || AD_WEAK_CAT.test(cats);
}
const NON_GAME_CATS = new Set(["entertainment", "movies", "movie", "film", "tv", "television", "what to watch", "anime"]);
const NON_GAME_TEXT = /(?<![a-z])(netflix|paramount\+?|hbo max|prime video|disney\+|box office|movies?|films?|tv series|tv show|tv-serie|episodes?|season finale)(?![a-z])/i;
const offTopic = (title, link, catList) =>
  catList.some((c) => NON_GAME_CATS.has(c.trim().toLowerCase())) || NON_GAME_TEXT.test(title) || NON_GAME_TEXT.test(link);

/* ---------- feed ophalen en lezen ---------- */
async function fetchFeed([source, url], topic) {
  const res = await fetch(url, {
    headers: { "user-agent": UA, accept: "application/rss+xml,application/atom+xml,application/xml,text/xml,*/*" },
    redirect: "follow", signal: AbortSignal.timeout(20000),
  });
  if (!res.ok) throw new Error(String(res.status));
  let xml = await res.text();
  if (xml.length > 400000) xml = xml.slice(0, 400000); // grote feeds: alleen het begin, daar staat het nieuwste
  const blocks = xml.match(/<(item|entry)[\s>][\s\S]*?<\/\1>/gi) || [];
  const out = [];
  for (const b of blocks) {
    const title = plain(tag(b, "title"));
    let link = plain(tag(b, "link"));
    if (!link) link = decodeOnce(attr(b, "link", "href") || "");
    if (!title || !/^https?:/.test(link)) continue;
    const catList = [...b.matchAll(/<category\b[^>]*?(?:term=["']([^"']+)["'][^>]*)?>([\s\S]*?)<\/category>|<category\b[^>]*term=["']([^"']+)["'][^>]*\/>/gi)]
      .map((m) => plain(m[1] || m[2] || m[3] || "")).filter(Boolean);
    const cats = catList.join("|");
    if (isAd(title, link, cats)) continue;
    if (topic.strict && offTopic(title, link, catList)) continue;
    let date = Date.parse(plain(tag(b, "pubDate") || tag(b, "published") || tag(b, "updated") || tag(b, "dc:date")));
    if (!Number.isFinite(date) || date > Date.now() + 36e5) date = Date.now();
    const rawDesc = tag(b, "description") || tag(b, "summary") || tag(b, "content:encoded") || tag(b, "content");
    let sum = plain(rawDesc);
    if (sum.toLowerCase().startsWith(title.toLowerCase())) sum = sum.slice(title.length).replace(/^[\s:.\-–—]+/, "");
    let img = null;
    for (const m of b.matchAll(/<(media:content|media:thumbnail|enclosure)\b[^>]*>/gi)) {
      const t = m[0], u = attr(t, m[1], "url"), type = attr(t, m[1], "type") || "", medium = attr(t, m[1], "medium") || "";
      if (u && medium !== "video" && !/^(video|audio)/.test(type) && (type.startsWith("image") || /\.(jpe?g|png|webp)/i.test(u))) { img = decodeOnce(u); break; }
    }
    if (!img) img = (decodeOnce(rawDesc).match(/<img[^>]+src=["']([^"']+)["']/i) || [])[1] || null;
    out.push({ t: title, l: link, s: source, d: date, m: shorten(sum), i: https(img), p: topic.id });
  }
  return out.sort((a, b) => b.d - a.d).slice(0, 12);
}

/* ---------- alles ophalen ---------- */
const jobs = [];
for (const t of topics) for (const f of t.feeds) jobs.push({ t, f });
const results = new Map(); // topic.id -> items
let failed = 0;
let next = 0;
async function worker() {
  while (next < jobs.length) {
    const { t, f } = jobs[next++];
    try {
      const items = await fetchFeed(f, t);
      results.set(t.id, (results.get(t.id) || []).concat(items));
    } catch (e) {
      failed++;
      console.warn(`✗ ${f[0]} (${t.id}): ${e.message}`);
    }
  }
}
await Promise.all(Array.from({ length: 8 }, worker));

const seen = new Set();
const items = [];
for (const t of topics) {
  const list = (results.get(t.id) || []).sort((a, b) => b.d - a.d).filter((i) => !seen.has(i.l) && seen.add(i.l)).slice(0, 40);
  items.push(...list);
  console.log(`${t.id.padEnd(12)} ${list.length} berichten`);
}
if (items.length < 20) { console.error("Te weinig berichten opgehaald, ik publiceer niets."); process.exit(1); }

mkdirSync(new URL("../web/data/", import.meta.url), { recursive: true });
writeFileSync(
  new URL("../web/data/feed.json", import.meta.url),
  JSON.stringify({ updated: Date.now(), topics: topics.map(({ id, n, e }) => ({ id, n, e })), items })
);
console.log(`Klaar: ${items.length} berichten, ${failed} van ${jobs.length} bronnen mislukt.`);
