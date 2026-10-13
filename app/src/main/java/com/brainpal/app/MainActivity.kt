@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.brainpal.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.viewModels
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Brand = Color(0xFF5B4BDB)
private val BrandDark = Color(0xFF9B8FFF)
private val Orange = Color(0xFFFF9D2E)

class MainActivity : ComponentActivity() {
    // Buiten de compositie aanmaken: een Vm die tijdens het tekenen ontstaat, geeft "Reading a state that was created after the snapshot was taken".
    private val vm: Vm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val vm = vm // `by viewModels()` is lazy: hier afdwingen, anders ontstaat de Vm alsnog binnen setContent
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = if (dark) darkColorScheme(
                    primary = BrandDark, onPrimary = Color(0xFF14112E), background = Color(0xFF13121F), onBackground = Color(0xFFEFEEFF),
                    surface = Color(0xFF1C1A2E), onSurface = Color(0xFFEFEEFF), surfaceVariant = Color(0xFF2A2745),
                    onSurfaceVariant = Color(0xFFA9A6CC), outlineVariant = Color(0xFF2E2B4A), secondaryContainer = Color(0xFF332F5C),
                    onSecondaryContainer = Color(0xFFEFEEFF), error = Color(0xFFFF7B7A)
                ) else lightColorScheme(
                    primary = Brand, onPrimary = Color.White, background = Color(0xFFF6F5FF), onBackground = Color(0xFF1D1B3A),
                    surface = Color.White, onSurface = Color(0xFF1D1B3A), surfaceVariant = Color(0xFFEAE7FB),
                    onSurfaceVariant = Color(0xFF66638C), outlineVariant = Color(0xFFE4E1FA), secondaryContainer = Color(0xFFE3DEFF),
                    onSecondaryContainer = Color(0xFF1D1B3A), error = Color(0xFFD6403F)
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App(vm) }
            }
        }
    }
}

private val ItemSaver = androidx.compose.runtime.saveable.Saver<Item?, ArrayList<String>>(
    save = { i -> i?.let { arrayListOf(it.title, it.link, it.source, it.date.toString(), it.summary, it.img ?: "", it.topic) } ?: arrayListOf() },
    restore = { l -> if (l.size < 7) null else Item(l[0], l[1], l[2], l[3].toLong(), l[4], l[5].ifEmpty { null }, l[6]) }
)

enum class Screen(val label: String, val icon: ImageVector) {
    Feed("Feed", Ic.Feed), Saved("Bewaard", Ic.Bookmark), Settings("Instellingen", Ic.Settings)
}

fun ago(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60000
    if (m < 1) return "nu"
    if (m < 60) return "$m min"
    val h = m / 60
    if (h < 24) return "$h u"
    return "${h / 24} d"
}

@Composable
private fun Ico(v: ImageVector, desc: String?, tint: Color, size: Dp = 22.dp) =
    Icon(v, desc, Modifier.size(size), tint = tint)

@Composable
private fun IconBtn(v: ImageVector, desc: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) =
    IconButton(onClick = onClick) { Ico(v, desc, tint) }

@Composable
fun App(vm: Vm) {
    var screen by rememberSaveable { mutableStateOf(Screen.Feed) }
    // Blijft staan als Android de app tussendoor uit het geheugen haalt (het artikel wordt dan opnieuw geladen).
    var reading by rememberSaveable(stateSaver = ItemSaver) { mutableStateOf<Item?>(null) }
    // Scrollstand staat hier, boven het artikel, zodat de feed op dezelfde plek terugkomt na het lezen.
    val feedState = rememberLazyListState()
    val savedState = rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.checkUpdate(manual = false) } // kijkt op GitHub of er een nieuwe versie is (hooguit eens per 6 uur)
    // Vernieuw als je terugkomt in de app (alleen als de feed ouder is dan 10 minuten).
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refreshIfStale() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val ctx = LocalContext.current
    var crash by remember { mutableStateOf(runCatching { java.io.File(ctx.filesDir, "crash.txt").takeIf { it.exists() }?.readText() }.getOrNull()) }
    crash?.let { text ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { Text("BrainPal is vorige keer gecrasht") },
            text = { Text(text.lines().take(14).joinToString("\n"), fontSize = 11.sp, lineHeight = 14.sp) },
            confirmButton = { TextButton(onClick = { runCatching { java.io.File(ctx.filesDir, "crash.txt").delete() }; crash = null }) { Text("Sluiten") } }
        )
    }
    reading?.let {
        BackHandler { reading = null }
        Reader(vm, it) { reading = null }
        return
    }
    val current = if (vm.topics.isEmpty()) Screen.Settings else screen
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (vm.topics.isNotEmpty()) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                Screen.entries.forEach { s ->
                    NavigationBarItem(
                        selected = current == s, onClick = { screen = s },
                        icon = { Ico(if (s == Screen.Saved && current == s) Ic.BookmarkFilled else s.icon, null, LocalContentColorFix()) },
                        label = { Text(s.label, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (current) {
                Screen.Feed -> FeedScreen(vm, feedState, { screen = Screen.Settings }) { reading = it }
                Screen.Saved -> SavedScreen(vm, savedState) { reading = it }
                Screen.Settings -> SettingsScreen(vm) { screen = Screen.Feed }
            }
        }
    }
}

/** Het icoon in de navigatiebalk neemt de kleur over van NavigationBarItem. */
@Composable
private fun LocalContentColorFix(): Color = androidx.compose.material3.LocalContentColor.current

@Composable
fun Header(title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
fun FeedScreen(vm: Vm, listState: LazyListState, onUpdate: () -> Unit, open: (Item) -> Unit) {
    val scope = rememberCoroutineScope()
    val list = vm.ranked
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Header("BrainPal", if (vm.updated > 0) "Bijgewerkt ${ago(vm.updated)} geleden" else null) {
            IconBtn(Ic.Refresh, "Vernieuwen") { vm.refresh(only = vm.filter) }
        }
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = cs.primary, trackColor = cs.outlineVariant)
        else Spacer(Modifier.height(2.dp))
        vm.update?.let { u ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp))
                    .background(cs.primary).clickable(onClick = onUpdate).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Update beschikbaar: versie ${u.version}", color = cs.onPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Bekijk", color = cs.onPrimary, fontWeight = FontWeight.Bold)
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { TopicChip("Alles", vm.filter == null) { vm.filter = null; scope.launch { listState.scrollToItem(0) } } }
            items(vm.topics) { id -> TopicChip(topicOf(id)!!.name, vm.filter == id) { vm.filter = id; vm.refreshTopicIfStale(id); scope.launch { listState.scrollToItem(0) } } }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                list.isEmpty() && vm.loading -> Center { CircularProgressIndicator(color = cs.primary) }
                list.isEmpty() && vm.failed -> Center {
                    Text("Geen verbinding", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Het nieuws kon niet worden geladen.", color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                    Button(onClick = { vm.refresh() }) { Text("Opnieuw proberen") }
                }
                list.isEmpty() -> Center { Text("Niets gevonden voor dit filter.", color = cs.onSurfaceVariant) }
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(list, key = { it.link }) { ArticleCard(vm, it, open) }
                }
            }
        }
    }
}

@Composable
private fun TopicChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = cs.surface, labelColor = cs.onSurface,
            selectedContainerColor = cs.primary, selectedLabelColor = cs.onPrimary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected, borderColor = cs.outlineVariant, selectedBorderColor = cs.primary
        )
    )
}

@Composable
fun Center(content: @Composable () -> Unit) =
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { content() }

@Composable
fun SavedScreen(vm: Vm, listState: LazyListState, open: (Item) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Header("Bewaard", if (vm.saved.isEmpty()) null else "${vm.saved.size} ${if (vm.saved.size == 1) "artikel" else "artikelen"}")
        if (vm.saved.isEmpty()) {
            Center {
                Ico(Ic.Bookmark, null, cs.onSurfaceVariant, 40.dp)
                Text("Nog niets bewaard", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
                Text("Tik bij een artikel op het bladwijzer-icoon om het hier te bewaren.", color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
        } else {
            LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(vm.saved.toList(), key = { it.link }) { ArticleCard(vm, it, open) }
            }
        }
    }
}

@Composable
fun ArticleCard(vm: Vm, i: Item, open: (Item) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val isRead = vm.read[i.link] == true
    val isNew = vm.prevVisit > 0 && i.date > vm.prevVisit && !isRead
    val saved = vm.isSaved(i.link)
    val liked = vm.votes[i.link] == 1
    val disliked = vm.votes[i.link] == -1
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        border = BorderStroke(1.dp, cs.outlineVariant),
    ) {
        Column(Modifier.clickable { open(i) }) {
            if (i.img != null) {
                NewsImage(i.img, i.link, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isNew) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Orange))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(topicOf(vm.displayTopic(i))?.name ?: i.topic, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cs.primary)
                    Text("  ·  ${i.source}  ·  ${ago(i.date)}", fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    i.title, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    color = if (isRead || disliked) cs.onSurfaceVariant else cs.onSurface
                )
                if (i.summary.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(i.summary, fontSize = 14.sp, lineHeight = 20.sp, color = cs.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(if (liked) Ic.ThumbUpFilled else Ic.ThumbUp, "Meer hiervan", if (liked) cs.primary else cs.onSurfaceVariant) { vm.vote(i, true) }
                IconBtn(if (disliked) Ic.ThumbDownFilled else Ic.ThumbDown, "Minder hiervan", if (disliked) cs.primary else cs.onSurfaceVariant) { vm.vote(i, false) }
                Spacer(Modifier.weight(1f))
                IconBtn(if (saved) Ic.BookmarkFilled else Ic.Bookmark, if (saved) "Verwijder uit bewaard" else "Bewaar", if (saved) cs.primary else cs.onSurfaceVariant) { vm.toggleSaved(i) }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = cs.surface),
        border = BorderStroke(1.dp, cs.outlineVariant), modifier = Modifier.fillMaxWidth()
    ) { Column(Modifier.padding(18.dp)) { content() } }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(desc, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsScreen(vm: Vm, done: () -> Unit) {
    val sel = remember { mutableStateListOf<String>().also { it.addAll(vm.topics) } }
    val first = vm.topics.isEmpty()
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.switchNotify(granted) }
    val changed = sel.toSet() != vm.topics.toSet()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            if (first) {
                Column(Modifier.padding(start = 4.dp, top = 28.dp, bottom = 8.dp)) {
                    Text("Welkom bij BrainPal", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Elke dag het nieuws in kort bestek, over onderwerpen die jij kiest. Wat je 👍 geeft komt vaker terug.",
                        color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)
                    )
                }
            } else Header("Instellingen")
        }
        item {
            SectionCard {
                Text(if (first) "Kies je onderwerpen" else "Onderwerpen", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TOPICS.forEach { t ->
                        FilterChip(
                            selected = t.id in sel,
                            onClick = { if (t.id in sel) sel.remove(t.id) else sel.add(t.id) },
                            label = { Text("${t.emoji}  ${t.name}", fontSize = 14.sp) },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = cs.surface, labelColor = cs.onSurface,
                                selectedContainerColor = cs.primary, selectedLabelColor = cs.onPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true, selected = t.id in sel, borderColor = cs.outlineVariant, selectedBorderColor = cs.primary
                            )
                        )
                    }
                }
                if (first || changed) {
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { vm.chooseTopics(TOPICS.map { it.id }.filter { it in sel }); done() },
                        enabled = sel.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)
                    ) { Text(if (first) "Toon mijn feed" else "Opslaan", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        if (!first) {
            item {
                SectionCard {
                    SwitchRow("Meldingen", "Een melding bij nieuw nieuws in je onderwerpen. Niet tussen 22:00 en 08:00.", vm.notify) { on ->
                        if (!on) vm.switchNotify(false)
                        else if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else vm.switchNotify(true)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 14.dp), color = cs.outlineVariant)
                    SwitchRow(
                        "Cookies automatisch accepteren",
                        "Voor Tweakers, NU.nl en de Volkskrant (DPG Media). De app klikt de cookiemelding voor je weg zodat artikelen in de app te lezen zijn. Let op: dit accepteert ook tracking-cookies.",
                        vm.autoCookies
                    ) { vm.switchAutoCookies(it) }
                }
            }
            item {
                SectionCard {
                    Text("Bronnen", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(
                        "Kies per onderwerp welke bronnen je wilt. Alleen aangezette bronnen worden opgehaald: minder bronnen is sneller.",
                        fontSize = 13.sp, lineHeight = 18.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp)
                    )
                    vm.topics.mapNotNull { topicOf(it) }.forEach { t ->
                        var open by rememberSaveable(t.id) { mutableStateOf(false) }
                        HorizontalDivider(Modifier.padding(top = 12.dp), color = cs.outlineVariant)
                        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text("${vm.enabledSources(t).size} van ${t.sources.size} aan", fontSize = 13.sp, color = cs.onSurfaceVariant)
                            Text(if (open) "  ▴" else "  ▾", color = cs.onSurfaceVariant)
                        }
                        if (open) t.sources.forEach { s ->
                            Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(s.name, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                Switch(checked = vm.sourceOn(t, s), onCheckedChange = { vm.setSource(t, s, it) })
                            }
                        }
                    }
                }
            }
            item {
                SectionCard {
                    Text("Updates", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("Je gebruikt versie ${vm.appVersion}.", fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                    val u = vm.update
                    val p = vm.downloadProgress
                    if (u != null) {
                        Text("Versie ${u.version} is beschikbaar", fontWeight = FontWeight.Bold)
                        if (u.notes.isNotEmpty()) Text(u.notes.take(400), fontSize = 13.sp, lineHeight = 18.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(12.dp))
                        if (p != null) {
                            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = cs.primary, trackColor = cs.outlineVariant)
                            Text("Downloaden… ${(p * 100).toInt()}%", fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        } else {
                            Button(onClick = { vm.installUpdate() }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                                Text("Download en installeer", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        Button(
                            onClick = { vm.checkUpdate(true) }, enabled = !vm.checkingUpdate,
                            modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)
                        ) { Text(if (vm.checkingUpdate) "Bezig…" else "Controleer op updates", fontWeight = FontWeight.SemiBold) }
                    }
                    vm.updateStatus?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
            item {
                Text(
                    "👍 en 👎 bij artikelen leren BrainPal wat je leuk vindt. Je gegevens blijven op deze telefoon.",
                    fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

private const val CLICK_JS = """
(function(){var n=0;var yes=/(akkoord|accepteer|alles accepteren|accept all|agree|ga verder|toestemming geven)/i;
var no=/(niet|weiger|instellingen|beheer|reject|decline|settings|manage)/i;
var t=setInterval(function(){n++;
 var els=document.querySelectorAll('button,a,[role=button],input[type=submit]');
 for(var i=0;i<els.length;i++){var e=els[i];var tx=(e.innerText||e.value||'').trim();
  if(tx.length>0&&tx.length<60&&yes.test(tx)&&!no.test(tx)){e.click();clearInterval(t);return;}}
 if(n>40)clearInterval(t);},500);})();
"""

/** Onzichtbare webweergave die de cookiemelding voor je accepteert (alleen als je dat bij Instellingen hebt aangezet). */
@Composable
fun AutoConsent(link: String, done: (Boolean) -> Unit) {
    var finished by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        delay(20000)
        if (!finished) { finished = true; done(false) }
    }
    AndroidView(
        modifier = Modifier.size(1.dp),
        factory = { ctx ->
            CookieManager.getInstance().setAcceptCookie(true)
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        val host = Uri.parse(url ?: "").host.orEmpty()
                        if (host.endsWith("dpgmedia.nl")) view.evaluateJavascript(CLICK_JS, null)
                        else if (!finished && host.isNotEmpty() && !url!!.contains("privacygate")) { finished = true; done(true) }
                    }
                }
                loadUrl(link)
            }
        },
        onRelease = { it.destroy() }
    )
}

@Composable
fun Reader(vm: Vm, item: Item, close: () -> Unit) {
    val uri = LocalUriHandler.current
    val cs = MaterialTheme.colorScheme
    var reload by remember { mutableStateOf(0) }
    var manual by remember { mutableStateOf(false) }
    var auto by remember { mutableStateOf(0) } // 0 = nog niet geprobeerd, 1 = bezig, 2 = geprobeerd
    val res by produceState<Result<Article?>?>(null, item.link, reload) {
        value = null
        vm.markRead(item)
        value = vm.article(item)
    }
    val gate = res?.exceptionOrNull() is ConsentGateException
    androidx.compose.runtime.LaunchedEffect(gate) { if (gate && vm.autoCookies && auto == 0) auto = 1 }

    if (manual) {
        ConsentView(item) {
            CookieManager.getInstance().flush()
            vm.forget(item.link)
            manual = false
            reload++
        }
        return
    }
    val article = res?.getOrNull()
    val saved = vm.isSaved(item.link)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ic.Back, "Terug", cs.onSurface) { close() }
            Text(item.source, color = cs.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconBtn(if (saved) Ic.BookmarkFilled else Ic.Bookmark, if (saved) "Verwijder uit bewaard" else "Bewaar", if (saved) cs.primary else cs.onSurfaceVariant) { vm.toggleSaved(item) }
            IconBtn(Ic.External, "Open op de website") { uri.openUri(item.link) }
        }
        if (auto == 1 && gate) AutoConsent(item.link) { ok ->
            auto = 2
            CookieManager.getInstance().flush()
            vm.forget(item.link)
            if (ok) reload++
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
            item {
                Text(topicOf(vm.displayTopic(item))?.name?.uppercase() ?: "", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cs.primary, letterSpacing = 1.sp)
                Text(
                    article?.title?.takeIf { it.isNotBlank() } ?: item.title,
                    fontSize = 26.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text("${item.source}  ·  ${ago(item.date)} geleden", fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            }
            when {
                res == null || (gate && auto == 1) -> item {
                    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = cs.primary)
                        if (gate) Text("Cookiemelding wordt geaccepteerd…", fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                    }
                }
                article == null -> item {
                    val ex = res?.exceptionOrNull()
                    val blocked = ex is HttpException && ex.code in listOf(401, 402, 403, 451) // betaalmuur of geblokkeerd
                    val check = ex is HttpException && ex.code in listOf(403, 503) // bot-controle van de site (bijvoorbeeld Cloudflare)
                    val msg = if (gate || blocked) null else ex?.message
                    SectionCard {
                        Text(
                            if (gate) "${item.source} vraagt eerst om je cookie-keuze"
                            else if (check) "${item.source} wil eerst controleren of je een mens bent"
                            else if (msg != null) "Laden mislukt" else "Dit artikel kan niet in de app worden getoond",
                            fontWeight = FontWeight.Bold, fontSize = 17.sp
                        )
                        Text(
                            if (gate) "Maak eenmalig zelf je keuze, daarna kan de app artikelen van deze sites laden. Of zet bij Instellingen \"Cookies automatisch accepteren\" aan."
                            else if (check) "Doorloop de controle eenmalig in de app: de pagina opent hier. Daarna probeert BrainPal het artikel zelf te laden. Lukt dat niet, dan kun je het gewoon in die pagina lezen."
                            else msg ?: "Waarschijnlijk staat het achter een betaalmuur of blokkeert de site het lezen.",
                            fontSize = 14.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                        )
                        if (item.summary.isNotEmpty()) {
                            Text(item.summary, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(bottom = 16.dp))
                        }
                        if (gate || check) Button(onClick = { manual = true }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Text(if (gate) "Cookie-keuze maken" else "Open in de app") }
                        if (gate || check) Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { uri.openUri(item.link) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
                            colors = if (gate || check) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.buttonColors(),
                            border = if (gate || check) BorderStroke(1.dp, cs.outlineVariant) else null
                        ) { Text("Open op ${item.source}") }
                    }
                }
                else -> {
                    (article.image ?: item.img)?.let { img ->
                        item {
                            NewsImage(img, item.link, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)))
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                    items(article.blocks) { b ->
                        when (b.type) {
                            "h" -> Text(b.text, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                            else -> Text(
                                (if (b.type == "li") "•  " else "") + b.text, fontSize = 18.sp, lineHeight = 29.sp, fontFamily = FontFamily.Serif,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                    }
                    item {
                        TextButton(onClick = { uri.openUri(article.url) }) {
                            Text("Origineel bij ${item.source}", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(6.dp)); Ico(Ic.External, null, cs.primary, 16.dp)
                        }
                    }
                }
            }
        }
    }
}

/** Toont de cookiepagina van de site in een webweergave, zodat jij zelf je keuze maakt. De app gebruikt daarna jouw cookie. */
@Composable
fun ConsentView(item: Item, done: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    BackHandler { done() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Doorloop de pagina en tik daarna op Klaar", fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            Button(onClick = done, shape = RoundedCornerShape(12.dp)) { Text("Klaar") }
        }
        AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { ctx ->
            CookieManager.getInstance().setAcceptCookie(true)
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                loadUrl(item.link)
            }
        }, onRelease = { it.destroy() })
    }
}

/** Plaatje bij een bericht. Stuurt de pagina mee als herkomst (sommige sites blokkeren anders) en verdwijnt als laden mislukt. */
@Composable
fun NewsImage(url: String, pageUrl: String, modifier: Modifier) {
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) return
    val ctx = LocalContext.current
    val request = remember(url, pageUrl) {
        ImageRequest.Builder(ctx).data(url)
            .addHeader("Referer", pageUrl)
            .addHeader("User-Agent", Net.BROWSER_UA)
            .build()
    }
    AsyncImage(
        model = request, contentDescription = null, contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        onError = { failed = true }
    )
}
