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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xr.steambridge.R
import xr.steambridge.UiState

@Composable
fun BridgeScreen(
    state: UiState,
    logs: List<String>,
    onLoginQr: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Steam.Ground)
            .padding(18.dp),
    ) {
        Header(state)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            LoginPanel {
                when (state) {
                    is UiState.LoggedOut -> SignedOut(state.error, onLoginQr)
                    is UiState.Working -> Busy(state.message)
                    is UiState.ShowQr -> QrView(state.challengeUrl)
                    is UiState.LoggedIn -> SignedIn(state, onLogout)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Console(logs)
    }
}

@Composable
private fun Header(state: UiState) {
    val (dotColor, label) = when (state) {
        is UiState.LoggedIn -> (if (state.relayRunning) Steam.Green else Steam.Faint) to
            (if (state.relayRunning) "Online" else "Offline")
        is UiState.LoggedOut -> Steam.Faint to "Offline"
        else -> Steam.BlueLt to "Connecting"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_steam_mark), contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Row {
            Text("Steam", color = Steam.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Bridge", color = Steam.BlueLt, fontSize = 20.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(7.dp))
        Text(label, color = Steam.Muted, fontSize = 12.sp)
    }
}

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
        Text("Sign in", color = Steam.White, fontSize = 26.sp, fontWeight = FontWeight.Normal)
        Spacer(Modifier.height(8.dp))
        Text(
            "Use the Steam Mobile App to sign in\nwith a QR code — no password needed.",
            color = Steam.Muted, fontSize = 13.sp,
        )
        Spacer(Modifier.height(22.dp))
        SteamButton("Sign in with QR code", onLoginQr)
        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(error, color = Steam.Danger, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SteamButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(Steam.SignIn)
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Steam.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
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
        Text(
            "Use the Steam Mobile App to\nsign in via QR code",
            color = Steam.BlueLt, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(13.dp), color = Steam.BlueLt, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Waiting for approval…", color = Steam.Faint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SignedIn(state: UiState.LoggedIn, onLogout: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(Steam.PanelHi)
                .border(1.dp, Steam.LineDim, RoundedCornerShape(3.dp))
                .padding(14.dp),
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(2.dp)).background(Steam.Bg1),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.account.take(1).uppercase(), color = Steam.BlueLt, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(state.account, color = Steam.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Steam.Green))
                    Spacer(Modifier.width(6.dp))
                    Text(state.steamId, color = Steam.Faint, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        RelayPill(state.relayRunning)
        Spacer(Modifier.height(20.dp))
        Text(
            "Sign out",
            color = Steam.BlueLt, fontSize = 14.sp,
            modifier = Modifier.clickable(onClick = onLogout).padding(8.dp),
        )
    }
}

@Composable
private fun RelayPill(running: Boolean) {
    val color = if (running) Steam.Green else Steam.Faint
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Steam.Bg1.copy(alpha = 0.6f))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(9.dp))
        Text(
            if (running) "Ticket relay live · 127.0.0.1:48010" else "Relay stopped",
            color = Steam.Text, fontSize = 13.sp,
        )
    }
}

@Composable
private fun Console(logs: List<String>) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.scrollToItem(logs.size - 1) }
    Column(
        Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Steam.Bg1.copy(alpha = 0.7f))
            .border(1.dp, Steam.LineDim, RoundedCornerShape(3.dp))
            .padding(12.dp),
    ) {
        Text("ACTIVITY", color = Steam.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        if (logs.isEmpty()) {
            Text("Nothing yet.", color = Steam.Faint, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        } else {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
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
            Text(line.substring(0, split), color = Steam.Faint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(8.dp))
            Text(line.substring(split + 2), color = Steam.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    } else {
        Text(line, color = Steam.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}
