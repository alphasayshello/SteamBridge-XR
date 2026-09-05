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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import xr.steambridge.RelayStatus
import xr.steambridge.UiState
import xr.steambridge.cm.msg.OwnedGame

@Composable
fun BridgeScreen(
    state: UiState,
    logs: List<String>,
    library: List<OwnedGame>,
    libraryLoading: Boolean,
    activeAppId: Int,
    relayStatus: RelayStatus.State,
    onLoginQr: () -> Unit,
    onLoginCredentials: (String, String) -> Unit,
    onSubmitGuardCode: (String) -> Unit,
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
            LibraryView(state, library, libraryLoading, activeAppId, relayStatus, onStartApp, onStopRelay, onLogout, onRefresh, Modifier.weight(1f))
        } else {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                LoginPanel {
                    when (state) {
                        is UiState.LoggedOut -> SignedOut(state.error, onLoginQr, onLoginCredentials)
                        is UiState.Working -> Busy(state.message)
                        is UiState.ShowQr -> QrView(state.challengeUrl)
                        is UiState.GuardPrompt -> GuardCodeView(state.isDeviceCode, state.hint, onSubmitGuardCode)
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
    relayStatus: RelayStatus.State,
    onStartApp: (Int) -> Unit,
    onStopRelay: () -> Unit,
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier,
) {
    val activeName = library.firstOrNull { it.appId == activeAppId }?.name?.ifEmpty { null }
    val nameOf = { id: Int -> library.firstOrNull { it.appId == id }?.name?.ifEmpty { null } }
    var query by remember { mutableStateOf("") }
    // Library defaults to the VR filter (this is a VR headset); the All chip drops it.
    var vrOnly by remember { mutableStateOf(true) }
    val hasVr = remember(library) { library.any { it.isVr } }
    val shown = remember(library, query, vrOnly) {
        library.asSequence()
            .filter { !vrOnly || it.isVr }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .toList()
    }
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
        RelayBar(running = state.relayRunning, activeName = activeName, status = relayStatus, nameOf = nameOf, onStop = onStopRelay)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LIBRARY", color = Steam.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.width(8.dp))
            Text(if (library.isEmpty()) "" else "${shown.size}", color = Steam.Faint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            if (loading) CircularProgressIndicator(Modifier.size(14.dp), color = Steam.BlueLt, strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(10.dp))
        SearchField(query, { query = it })
        Spacer(Modifier.height(10.dp))
        FilterChips(vrOnly, hasVr) { vrOnly = it }
        Spacer(Modifier.height(12.dp))
        when {
            library.isEmpty() && loading -> Centered("Loading your library…")
            library.isEmpty() -> Centered("No games found.")
            shown.isEmpty() -> Centered(if (vrOnly) "No VR apps found — tap All." else "No matches for \"$query\".")
            else -> LazyVerticalGrid(
                // Adaptive so tiles re-flow into more/fewer columns as the panel is resized.
                columns = GridCells.Adaptive(minSize = 128.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(shown, key = { it.appId }) { game ->
                    GameTile(game, tileStateFor(game.appId, activeAppId, state.relayRunning, relayStatus)) {
                        onStartApp(game.appId)
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Centered(text: String) {
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Text(text, color = Steam.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = Steam.Text, fontSize = 14.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Steam.BlueLt),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(Steam.Search)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { inner ->
            if (query.isEmpty()) Text("Search library", color = Steam.Faint, fontSize = 14.sp)
            inner()
        },
    )
}

@Composable
private fun FilterChips(vrOnly: Boolean, hasVr: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("All", selected = !vrOnly) { onChange(false) }
        Chip(if (hasVr) "VR" else "VR (detecting…)", selected = vrOnly) { onChange(true) }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Steam.White else Steam.Muted,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (selected) Steam.PanelHi else Steam.Bg1.copy(alpha = 0.5f))
            .then(if (selected) Modifier.border(1.dp, Steam.Line, RoundedCornerShape(2.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

private data class RelayView(val accent: Color, val title: String, val sub: String, val spinner: Boolean)

@Composable
private fun RelayBar(
    running: Boolean,
    activeName: String?,
    status: RelayStatus.State,
    nameOf: (Int) -> String?,
    onStop: () -> Unit,
) {
    // Mint status drives the display; fall back to the relay's running/stopped baseline.
    val v = when (status) {
        is RelayStatus.State.Minting ->
            RelayView(Steam.BlueLt, "Minting ticket…", nameOf(status.appId) ?: "app ${status.appId}", spinner = true)
        is RelayStatus.State.Ready ->
            RelayView(Steam.Green, "Relay live", nameOf(status.appId) ?: activeName ?: "127.0.0.1:48010", spinner = false)
        is RelayStatus.State.Failed -> {
            val who = nameOf(status.appId)?.let { "$it — " } ?: ""
            RelayView(
                Steam.Danger,
                if (status.notOwned) "Mint failed · not owned" else "Mint failed",
                if (status.notOwned) "${who}this account doesn't own it" else who + status.reason,
                spinner = false,
            )
        }
        RelayStatus.State.Idle ->
            if (running) RelayView(Steam.Green, "Relay live", activeName ?: "127.0.0.1:48010", spinner = false)
            else RelayView(Steam.Faint, "Relay stopped", "Tap a game to start it", spinner = false)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(Steam.Bg1.copy(alpha = 0.6f))
            .border(1.dp, v.accent.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (v.spinner) {
            CircularProgressIndicator(Modifier.size(12.dp), color = v.accent, strokeWidth = 2.dp)
        } else {
            Box(Modifier.size(8.dp).clip(CircleShape).background(v.accent))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(v.title, color = Steam.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(v.sub, color = Steam.Faint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

private enum class TileState { NONE, LIVE, MINTING, FAILED }

/** The active tile mirrors the live mint status; every other tile is NONE. */
private fun tileStateFor(appId: Int, activeAppId: Int, running: Boolean, status: RelayStatus.State): TileState {
    if (appId != activeAppId) return TileState.NONE
    return when {
        status is RelayStatus.State.Failed && status.appId == appId -> TileState.FAILED
        status is RelayStatus.State.Minting && status.appId == appId -> TileState.MINTING
        running -> TileState.LIVE
        else -> TileState.NONE
    }
}

@Composable
private fun GameTile(game: OwnedGame, tile: TileState, onClick: () -> Unit) {
    val accent = when (tile) {
        TileState.LIVE -> Steam.Green
        TileState.MINTING -> Steam.BlueLt
        TileState.FAILED -> Steam.Danger
        TileState.NONE -> null
    }
    val badge = when (tile) {
        TileState.LIVE -> "● LIVE"
        TileState.MINTING -> "MINTING"
        TileState.FAILED -> "FAILED"
        TileState.NONE -> null
    }
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.667f)
                .clip(RoundedCornerShape(4.dp))
                .then(if (accent != null) Modifier.border(2.dp, accent, RoundedCornerShape(4.dp)) else Modifier),
        ) {
            CapsuleImage(game, Modifier.fillMaxSize())
            if (accent != null && badge != null) {
                Text(
                    badge,
                    color = Steam.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(accent)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            game.name.ifEmpty { game.appId.toString() },
            color = accent ?: Steam.Text,
            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
        )
        Text(
            when (tile) {
                TileState.LIVE -> "Relay live"
                TileState.MINTING -> "Minting ticket…"
                TileState.FAILED -> "Mint failed"
                TileState.NONE -> "Tap to start"
            },
            color = accent ?: Steam.Faint, fontSize = 10.sp,
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
private fun SignedOut(error: String?, onLoginQr: () -> Unit, onLoginCredentials: (String, String) -> Unit) {
    var byPassword by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Sign in", color = Steam.White, fontSize = 26.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("QR code", selected = !byPassword) { byPassword = false }
            Chip("Password", selected = byPassword) { byPassword = true }
        }
        Spacer(Modifier.height(20.dp))
        if (byPassword) {
            CredentialForm(onSubmit = onLoginCredentials)
        } else {
            Text(
                "Use the Steam Mobile App to sign in\nwith a QR code — no password needed.",
                color = Steam.Muted, fontSize = 13.sp,
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Sign in with QR code", enabled = true, onClick = onLoginQr)
        }
        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(error, color = Steam.Danger, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CredentialForm(onSubmit: (String, String) -> Unit) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        InputField(account, { account = it }, "Account name", password = false)
        Spacer(Modifier.height(10.dp))
        InputField(password, { password = it }, "Password", password = true)
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            "Sign in",
            enabled = account.isNotBlank() && password.isNotEmpty(),
        ) { onSubmit(account, password) }
        Spacer(Modifier.height(10.dp))
        Text(
            "Steam Guard asks for a code next if your account needs one.",
            color = Steam.Faint, fontSize = 11.sp,
        )
    }
}

@Composable
private fun GuardCodeView(isDeviceCode: Boolean, hint: String, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Steam Guard", color = Steam.White, fontSize = 22.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            if (isDeviceCode) "Enter the code from your Steam\nMobile authenticator app."
            else if (hint.isNotBlank()) "Enter the code sent to $hint."
            else "Enter the Steam Guard code sent to\nyour email.",
            color = Steam.Muted, fontSize = 13.sp,
        )
        Spacer(Modifier.height(18.dp))
        InputField(code, { code = it }, "Code", password = false, numeric = true)
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Verify", enabled = code.isNotBlank()) { onSubmit(code) }
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(if (enabled) Steam.SignIn else androidx.compose.ui.graphics.SolidColor(Steam.PanelHi))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) Steam.White else Steam.Faint,
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InputField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    password: Boolean,
    numeric: Boolean = false,
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = Steam.Text, fontSize = 15.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Steam.BlueLt),
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = when {
                password -> androidx.compose.ui.text.input.KeyboardType.Password
                numeric -> androidx.compose.ui.text.input.KeyboardType.NumberPassword
                else -> androidx.compose.ui.text.input.KeyboardType.Text
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(Steam.Search)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, color = Steam.Faint, fontSize = 15.sp)
            inner()
        },
    )
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
