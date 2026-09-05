package xr.steambridge.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import xr.steambridge.R
import xr.steambridge.UiState
import xr.steambridge.cm.msg.OwnedGame

@Composable
fun BridgeScreen(
    state: UiState,
    logs: List<String>,
    library: List<OwnedGame>,
    libraryLoading: Boolean,
    activeAppId: Int,
    onLoginQr: () -> Unit,
    onLogout: () -> Unit,
    onStartApp: (Int) -> Unit,
    onStopRelay: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Steam.Ground)
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp),
    ) {
        Header(state)
        Spacer(Modifier.height(14.dp))
        if (state is UiState.LoggedIn) {
            LibraryView(state, library, libraryLoading, activeAppId, onStartApp, onStopRelay, onLogout, onRefresh, Modifier.weight(1f))
        } else {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                LoginPanel {
                    when (state) {
                        is UiState.LoggedOut -> SignedOut(state.error, onLoginQr)
                        is UiState.Working -> Busy(state.message)
                        is UiState.ShowQr -> QrView(state.challengeUrl)
                        else -> {}
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Console(logs)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Header(state: UiState) {
    val (dot, label) = when (state) {
        is UiState.LoggedIn -> (if (state.relayRunning) Steam.Green else Steam.Faint) to
            (if (state.relayRunning) "Online" else "Offline")
        is UiState.LoggedOut -> Steam.Faint to "Offline"
        else -> Steam.BlueLt to "Connecting"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_steam_mark), contentDescription = null, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(9.dp))
        Row {
            Text("Steam", color = Steam.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("Bridge", color = Steam.BlueLt, fontSize = 19.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Steam.Muted, fontSize = 12.sp)
    }
}

// ---- Library ----

@Composable
private fun LibraryView(
    state: UiState.LoggedIn,
    library: List<OwnedGame>,
    loading: Boolean,
    activeAppId: Int,
    onStartApp: (Int) -> Unit,
    onStopRelay: () -> Unit,
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier,
) {
    val activeName = library.firstOrNull { it.appId == activeAppId }?.name?.ifEmpty { null }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(3.dp)).background(Steam.Bg1), contentAlignment = Alignment.Center) {
                Text(state.account.take(1).uppercase(), color = Steam.BlueLt, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(state.account, color = Steam.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Sign out", color = Steam.BlueLt, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onLogout))
            }
            Text(
                "Refresh",
                color = Steam.BlueLt, fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(Steam.PanelHi)
                    .clickable(onClick = onRefresh)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        RelayBar(running = state.relayRunning, activeName = activeName, onStop = onStopRelay)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LIBRARY", color = Steam.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.width(8.dp))
            Text(if (library.isEmpty()) "" else "${library.size}", color = Steam.Faint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            if (loading) CircularProgressIndicator(Modifier.size(14.dp), color = Steam.BlueLt, strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(10.dp))
        when {
            library.isEmpty() && loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Loading your library…", color = Steam.Muted, fontSize = 13.sp)
            }
            library.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No games found.", color = Steam.Muted, fontSize = 13.sp)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(library, key = { it.appId }) { game ->
                    GameTile(game, active = game.appId == activeAppId && state.relayRunning) { onStartApp(game.appId) }
                }
            }
        }
    }
}

@Composable
private fun RelayBar(running: Boolean, activeName: String?, onStop: () -> Unit) {
    val accent = if (running) Steam.Green else Steam.Faint
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(Steam.Bg1.copy(alpha = 0.6f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(if (running) "Relay live" else "Relay stopped", color = Steam.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (running) (activeName ?: "127.0.0.1:48010") else "Tap a game to start it",
                color = Steam.Faint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (running) {
            Text(
                "Stop",
                color = Steam.Danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, Steam.Danger.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                    .clickable(onClick = onStop)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun GameTile(game: OwnedGame, active: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.667f)
                .clip(RoundedCornerShape(4.dp))
                .then(if (active) Modifier.border(2.dp, Steam.Green, RoundedCornerShape(4.dp)) else Modifier),
        ) {
            CapsuleImage(game, Modifier.fillMaxSize())
            if (active) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Steam.Green)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text("● LIVE", color = Steam.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            game.name.ifEmpty { game.appId.toString() },
            color = if (active) Steam.Green else Steam.Text,
            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
        )
        Text(
            if (active) "Relay live" else "Tap to start",
            color = Steam.Faint, fontSize = 10.sp,
        )
    }
}

@Composable
private fun CapsuleImage(game: OwnedGame, modifier: Modifier) {
    // library_600x900 → header.jpg → solid branded tile with the name.
    SubcomposeAsyncImage(
        model = SteamImages.capsule(game.appId),
        contentDescription = game.name,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Error -> HeaderOrSolid(game, modifier)
            else -> SubcomposeAsyncImageContent()
        }
    }
}

@Composable
private fun HeaderOrSolid(game: OwnedGame, modifier: Modifier) {
    SubcomposeAsyncImage(
        model = SteamImages.header(game.appId),
        contentDescription = game.name,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Error -> Box(
                modifier.background(Steam.Card), contentAlignment = Alignment.Center,
            ) {
                Text(
                    game.name.ifEmpty { game.appId.toString() },
                    color = Steam.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp),
                )
            }
            else -> SubcomposeAsyncImageContent()
        }
    }
}

// ---- Login flow ----

@Composable
private fun LoginPanel(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF10161E).copy(alpha = 0.66f))
            .border(1.dp, Steam.LineDim, RoundedCornerShape(4.dp))
            .padding(22.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun SignedOut(error: String?, onLoginQr: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Sign in", color = Steam.White, fontSize = 26.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Use the Steam Mobile App to sign in\nwith a QR code — no password needed.",
            color = Steam.Muted, fontSize = 13.sp,
        )
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(Steam.SignIn)
                .clickable(onClick = onLoginQr)
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Sign in with QR code", color = Steam.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(error, color = Steam.Danger, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Busy(message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Steam.BlueLt, strokeWidth = 3.dp)
        Spacer(Modifier.height(16.dp))
        Text(message, color = Steam.Muted, fontSize = 14.sp)
    }
}

@Composable
private fun QrView(challengeUrl: String) {
    val qr = remember(challengeUrl) { QrEncoder.encode(challengeUrl) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White).padding(14.dp)) {
            Image(bitmap = qr, contentDescription = "Steam login QR code", modifier = Modifier.size(210.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Use the Steam Mobile App to\nsign in via QR code", color = Steam.BlueLt, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(13.dp), color = Steam.BlueLt, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Waiting for approval…", color = Steam.Faint, fontSize = 12.sp)
        }
    }
}

// ---- Activity console ----

@Composable
private fun Console(logs: List<String>) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.scrollToItem(logs.size - 1) }
    Column(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Steam.Bg1.copy(alpha = 0.7f))
            .border(1.dp, Steam.LineDim, RoundedCornerShape(3.dp))
            .padding(10.dp),
    ) {
        Text("ACTIVITY", color = Steam.Faint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(5.dp))
        if (logs.isEmpty()) {
            Text("Nothing yet.", color = Steam.Faint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        } else {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(logs) { line -> LogLine(line) }
            }
        }
    }
}

@Composable
private fun LogLine(line: String) {
    val split = line.indexOf("  ")
    if (split in 1..12) {
        Row {
            Text(line.substring(0, split), color = Steam.Faint, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(8.dp))
            Text(line.substring(split + 2), color = Steam.Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    } else {
        Text(line, color = Steam.Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}
