@file:OptIn(ExperimentalMaterial3Api::class)

package com.brainpal.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val NL = Locale("nl", "NL")

/** "Di 20 oktober" */
private fun dateline(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMMM", NL)).replaceFirstChar { it.uppercase() }

/** "Nieuw sinds je laatste bezoek om 09:41" (of "op 18 oktober" als dat niet vandaag was). */
private fun sinceText(prevVisit: Long): String? {
    if (prevVisit <= 0) return null
    val today = LocalDate.now()
    val day = java.time.Instant.ofEpochMilli(prevVisit).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return if (day == today) "Nieuw sinds je laatste bezoek om " + SimpleDateFormat("HH:mm", NL).format(Date(prevVisit))
    else "Nieuw sinds je laatste bezoek op " + day.format(DateTimeFormatter.ofPattern("d MMMM", NL))
}

/** "20 oktober, 09:02" */
fun readerTime(t: Long): String = SimpleDateFormat("d MMMM, HH:mm", NL).format(Date(t))

/**
 * De feed als korte krant: één bericht groot bovenaan (het eerste met een foto uit je beste tien), daaronder de rest als rij,
 * en onderaan "Dat was het voor nu". De volgorde zelf komt van `vm.ranked` (nieuw, en wat je zelf leuk vindt).
 */
@Composable
fun FeedScreen(vm: Vm, listState: LazyListState, onUpdate: () -> Unit, open: (Item) -> Unit) {
    val scope = rememberCoroutineScope()
    val list = vm.ranked
    val cs = MaterialTheme.colorScheme
    val lead = list.take(10).firstOrNull { it.img != null } ?: list.firstOrNull()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                Text(
                    dateline(), fontSize = 31.sp, lineHeight = 31.sp, fontFamily = DisplayNarrow, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp, color = cs.onBackground
                )
                val sub = sinceText(vm.prevVisit) ?: if (vm.updated > 0) "Bijgewerkt ${ago(vm.updated)} geleden" else null
                if (sub != null) Text(sub, fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrainMark(cs.tertiary, cs.background, Modifier.size(30.dp, 22.dp))
                IconBtn(Ic.Refresh, "Vernieuwen") { vm.refresh(only = vm.filter) }
            }
        }
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = cs.tertiary, trackColor = cs.outlineVariant)
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
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { TopicChip("Alles", vm.filter == null) { vm.filter = null; scope.launch { listState.scrollToItem(0) } } }
            items(vm.topics) { id -> TopicChip(topicOf(id)!!.name, vm.filter == id) { vm.filter = id; vm.refreshTopicIfStale(id); scope.launch { listState.scrollToItem(0) } } }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                list.isEmpty() && vm.loading -> Center { CircularProgressIndicator(color = cs.tertiary) }
                list.isEmpty() && vm.failed -> Center {
                    Text("Geen verbinding", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Het nieuws kon niet worden geladen.", color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                    Button(onClick = { vm.refresh() }) { Text("Opnieuw proberen") }
                }
                list.isEmpty() -> Center { Text("Niets gevonden voor dit filter.", color = cs.onSurfaceVariant) }
                else -> {
                    val rest = list.filter { it.link != lead?.link }
                    LazyColumn(state = listState, contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 20.dp)) {
                        if (lead != null) item(key = "lead:" + lead.link) { SwipeRow(vm, lead, 20.dp) { LeadCard(vm, lead, open) } }
                        if (rest.isNotEmpty()) item(key = "verder") {
                            Text("Verder", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 2.dp))
                        }
                        items(rest, key = { it.link }) { i ->
                            SwipeRow(vm, i, 0.dp) { NewsRow(vm, i, open) }
                            HorizontalDivider(color = cs.outlineVariant)
                        }
                        item(key = "einde") { EndMarker(vm) }
                    }
                }
            }
        }
    }
}

@Composable
fun SavedScreen(vm: Vm, listState: LazyListState, open: (Item) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Header("Bewaard", if (vm.saved.isEmpty()) null else "${vm.saved.size} ${if (vm.saved.size == 1) "artikel" else "artikelen"}")
        if (vm.saved.isEmpty()) {
            Center {
                Ico(Ic.Bookmark, null, cs.onSurfaceVariant, 40.dp)
                Text("Nog niets bewaard", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
                Text("Veeg een bericht naar rechts om het te bewaren, of tik in een artikel op het bladwijzer-icoon.", color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
        } else {
            LazyColumn(state = listState, contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 20.dp)) {
                items(vm.saved.toList(), key = { it.link }) { i ->
                    SwipeRow(vm, i, 0.dp) { NewsRow(vm, i, open) }
                    HorizontalDivider(color = cs.outlineVariant)
                }
            }
        }
    }
}

/**
 * Veeg naar rechts: bewaren (of weer uit bewaard halen). Veeg naar links: minder hiervan.
 * Het bericht springt daarna terug op zijn plek, met een bladwijzer of duim erbij zodat je ziet dat het gelukt is.
 */
@Composable
private fun SwipeRow(vm: Vm, i: Item, corner: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> vm.toggleSaved(i)
                SwipeToDismissBoxValue.EndToStart -> vm.vote(i, false)
                else -> {}
            }
            false // nooit echt weggooien: het bericht springt terug
        },
        positionalThreshold = { it * 0.3f }
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val dir = state.dismissDirection
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(corner)).background(cs.surfaceVariant).padding(horizontal = 22.dp),
                contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                when (dir) {
                    SwipeToDismissBoxValue.StartToEnd -> Ico(if (vm.isSaved(i.link)) Ic.BookmarkFilled else Ic.Bookmark, "Bewaar", cs.onSurface, 24.dp)
                    SwipeToDismissBoxValue.EndToStart -> Ico(Ic.ThumbDown, "Minder hiervan", cs.onSurface, 24.dp)
                    else -> {}
                }
            }
        },
        content = { Box(Modifier.background(cs.background)) { content() } }
    )
}

@Composable
private fun TitleText(i: Item, isNew: Boolean, dim: Boolean, size: TextUnit, line: TextUnit, weight: FontWeight, maxLines: Int, spacing: TextUnit = 0.sp) {
    val cs = MaterialTheme.colorScheme
    val marker = if (isSystemInDarkTheme()) MarkerDark else MarkerLight
    val text = buildAnnotatedString {
        if (isNew) withStyle(SpanStyle(background = marker)) { append(i.title) } else append(i.title)
    }
    Text(
        text, fontSize = size, lineHeight = line, fontWeight = weight, letterSpacing = spacing, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        color = if (dim) cs.onSurfaceVariant else cs.onSurface
    )
}

/** Stip in de kleur van het onderwerp, de bron, en rechts de tijd. Een bladwijzer of duim laat zien wat je met het bericht gedaan hebt. */
@Composable
private fun MetaLine(vm: Vm, i: Item) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(topicColor(vm.displayTopic(i), isSystemInDarkTheme())))
        Spacer(Modifier.width(8.dp))
        Text(i.source, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (vm.isSaved(i.link)) { Spacer(Modifier.width(6.dp)); Ico(Ic.BookmarkFilled, null, cs.onSurface, 14.dp) }
        if (vm.votes[i.link] == 1) { Spacer(Modifier.width(6.dp)); Ico(Ic.ThumbUpFilled, null, cs.onSurface, 14.dp) }
        if (vm.votes[i.link] == -1) { Spacer(Modifier.width(6.dp)); Ico(Ic.ThumbDownFilled, null, cs.onSurfaceVariant, 14.dp) }
        Spacer(Modifier.weight(1f))
        Text(ago(i.date), fontSize = 13.sp, color = cs.onSurfaceVariant)
    }
}

/** Het bericht dat bovenaan staat: het enige in een kaart. */
@Composable
private fun LeadCard(vm: Vm, i: Item, open: (Item) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val isRead = vm.read[i.link] == true
    val isNew = vm.prevVisit > 0 && i.date > vm.prevVisit && !isRead
    val disliked = vm.votes[i.link] == -1
    Surface(onClick = { open(i) }, shape = RoundedCornerShape(20.dp), color = cs.surface, modifier = Modifier.fillMaxWidth()) {
        Column {
            val img = i.img ?: vm.ogImages[i.link]
            LaunchedEffect(i.link) { vm.loadOgImage(i) }
            if (img != null) NewsImage(img, i.link, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            else if (vm.ogFailed[i.link] == true) SourceTile(i.source, vm.logos[i.source])
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
                MetaLine(vm, i)
                Spacer(Modifier.height(8.dp))
                TitleText(i, isNew, isRead || disliked, 28.sp, 31.sp, FontWeight.Bold, 5, (-0.3).sp)
                if (i.summary.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(i.summary, fontSize = 15.sp, lineHeight = 22.sp, fontFamily = Read, color = cs.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Eén bericht in de lijst: titel links, kleine foto rechts. */
@Composable
private fun NewsRow(vm: Vm, i: Item, open: (Item) -> Unit) {
    val isRead = vm.read[i.link] == true
    val isNew = vm.prevVisit > 0 && i.date > vm.prevVisit && !isRead
    val disliked = vm.votes[i.link] == -1
    Row(Modifier.fillMaxWidth().clickable { open(i) }.padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            MetaLine(vm, i)
            Spacer(Modifier.height(4.dp))
            TitleText(i, isNew, isRead || disliked, 17.sp, 22.sp, FontWeight.SemiBold, 4)
        }
        Spacer(Modifier.width(14.dp))
        Thumb(vm, i)
    }
}

@Composable
private fun Thumb(vm: Vm, i: Item) {
    val dark = isSystemInDarkTheme()
    val c = topicColor(vm.displayTopic(i), dark)
    LaunchedEffect(i.link) { vm.loadOgImage(i) }
    val img = i.img ?: vm.ogImages[i.link]
    Box(
        Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = if (dark) 0.28f else 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        if (img != null) NewsImage(img, i.link, Modifier.fillMaxSize())
        else Text(i.source.take(1).uppercase(), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = c)
    }
}

/** De brief houdt op. Dat is het punt: je weet dat je bent bijgepraat. */
@Composable
private fun EndMarker(vm: Vm) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 28.dp)) {
        HorizontalDivider(thickness = 2.dp, color = cs.onSurface)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrainMark(cs.tertiary, cs.background, Modifier.size(30.dp, 22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Dat was het voor nu", fontSize = 21.sp, fontFamily = DisplayNarrow, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (vm.notify && vm.notifyMode == "daily") "Je volgende brief komt morgen rond 08:00." else "Tik op vernieuwen voor nieuw nieuws.",
            fontSize = 14.sp, color = cs.onSurfaceVariant
        )
    }
}
