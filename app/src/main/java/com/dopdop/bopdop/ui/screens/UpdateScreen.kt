package com.dopdop.bopdop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.BuildConfig
import com.dopdop.bopdop.data.InstallState
import com.dopdop.bopdop.data.UpdateInstaller
import com.dopdop.bopdop.data.Updates
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import kotlinx.coroutines.launch

/**
 * The in-app updater: what's installed, what's new (with the release notes), how big it is, and
 * one button that downloads it with a progress bar and hands it to Android's installer. The new
 * version installs over this one; your library and settings stay.
 */
@Composable
fun UpdateScreen() {
    val ctx = LocalContext.current
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val update by Updates.available.collectAsState()
    val install by UpdateInstaller.state.collectAsState()
    var checking by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (update == null) { checking = true; Updates.check(force = true); checking = false }
        checked = true
    }
    val u = update
    LargeTitleScaffold(title = "Updates", eyebrow = "BopDop ${BuildConfig.VERSION_NAME} installed", showBack = true) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().glassLite(RoundedCornerShape(24.dp), alpha = 0.5f).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(if (u != null) Bop.Ink else Bop.Veil), contentAlignment = Alignment.Center) {
                        Icon(if (u != null) Icons.Rounded.SystemUpdate else Icons.Rounded.CheckCircle, null, tint = if (u != null) Bop.OnInk else Bop.Text, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when { u != null -> "${u.title} is ready"; checking || !checked -> "Checking for updates…"; else -> "You're up to date" },
                            style = BopType.Title, color = Bop.Text,
                        )
                        Text(
                            if (u != null) "${BuildConfig.VERSION_NAME} → ${u.version}" + (u.sizeBytes.takeIf { it > 0 }?.let { " · ${mb(it)}" } ?: "")
                            else "BopDop ${BuildConfig.VERSION_NAME}",
                            style = BopType.Caption, color = Bop.Text2,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                when (val s = install) {
                    is InstallState.Downloading -> {
                        LinearProgressIndicator(progress = { s.fraction }, color = Bop.Accent, trackColor = Bop.Veil,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)))
                        Text("Downloading ${(s.fraction * 100).toInt()}%" + (u?.sizeBytes?.takeIf { it > 0 }?.let { " · ${mb((it * s.fraction).toLong())} of ${mb(it)}" } ?: ""),
                            style = BopType.Caption, color = Bop.Text2, modifier = Modifier.padding(top = 8.dp))
                    }
                    InstallState.Installing -> Text("Opening Android's installer… confirm there to finish.", style = BopType.Body, color = Bop.Text2)
                    InstallState.NeedsPermission -> Text("Allow \"Install unknown apps\" for BopDop, then come back: the update continues by itself.", style = BopType.Body, color = Bop.Text2)
                    is InstallState.Failed -> Text(s.message, style = BopType.Body, color = Bop.Danger)
                    else -> {}
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val busy = install is InstallState.Downloading || install == InstallState.Installing
                    if (u != null) Text(
                        if (busy) "Updating…" else "Update now", style = BopType.BodyStrong, color = Bop.OnInk,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Bop.Ink.copy(alpha = if (busy) 0.5f else 1f))
                            .bounceClick { if (!busy) scope.launch { UpdateInstaller.install(ctx.applicationContext, u) } }
                            .padding(horizontal = 20.dp, vertical = 11.dp),
                    )
                    Text(
                        if (checking) "Checking…" else "Check again", style = BopType.BodyStrong, color = Bop.Text,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Bop.Veil.copy(alpha = 0.6f))
                            .bounceClick {
                                if (checking) return@bounceClick
                                scope.launch {
                                    checking = true
                                    val found = Updates.check(force = true)
                                    checking = false
                                    if (found == null) app.toast("You're on the latest version")
                                }
                            }.padding(horizontal = 20.dp, vertical = 11.dp),
                    )
                }
            }
        }
        if (u != null && u.notes.isNotBlank()) item {
            Text("WHAT'S NEW", style = BopType.Micro, color = Bop.Text3, modifier = Modifier.padding(start = 28.dp, top = 18.dp, bottom = 8.dp))
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().glassLite(RoundedCornerShape(24.dp), alpha = 0.5f).padding(18.dp)) {
                ReleaseNotes(u.notes)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun mb(bytes: Long) = "%.1f MB".format(bytes / 1_048_576f)

/** GitHub's release notes, read simply: "## Heading", "- item" and **bold**; links and the rest as text. */
@Composable
private fun ReleaseNotes(md: String) {
    md.lines().map { it.trimEnd() }.forEach { line ->
        when {
            line.isBlank() -> Spacer(Modifier.height(6.dp))
            line.startsWith("#") -> Text(line.trimStart('#', ' '), style = BopType.Headline, color = Bop.Text, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> Row(Modifier.padding(vertical = 2.dp)) {
                Text("•", style = BopType.Body, color = Bop.Text2, modifier = Modifier.width(16.dp))
                Text(bold(line.trimStart().drop(2)), style = BopType.Body, color = Bop.Text2)
            }
            else -> Text(bold(line), style = BopType.Body, color = Bop.Text2, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

private fun bold(s: String): AnnotatedString = buildAnnotatedString {
    val text = s.replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
    var rest = text
    while (true) {
        val a = rest.indexOf("**"); val b = if (a >= 0) rest.indexOf("**", a + 2) else -1
        if (a < 0 || b < 0) { append(rest); break }
        append(rest.substring(0, a))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Bop.Text)) { append(rest.substring(a + 2, b)) }
        rest = rest.substring(b + 2)
    }
}
