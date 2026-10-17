package com.dopdop.bopdop.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dopdop.bopdop.data.AppUpdate
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.Updates
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType

/** Downloads the update's APK (or opens its release page) in the browser. */
fun openUpdate(ctx: android.content.Context, u: AppUpdate) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.apkUrl ?: u.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * A quiet glass card on Home when a newer BopDop is out: Update downloads it, the cross hides
 * it until the next version after this one.
 */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val update by Updates.available.collectAsState()
    val lib by LibraryStore.state.collectAsState()
    val u = update
    AnimatedVisibility(
        visible = u != null && u.version != lib.settings.skippedUpdate,
        enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        if (u == null) return@AnimatedVisibility
        val shape = RoundedCornerShape(24.dp)
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth()
                .glassLite(shape, alpha = 0.6f, shadow = 12.dp).padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Bop.Ink), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${u.title} is out", style = BopType.BodyStrong, color = Bop.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("A new BopDop is ready to install", style = BopType.Caption, color = Bop.Text2, maxLines = 1)
            }
            Text(
                "Update", style = BopType.BodyStrong, color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Bop.Ink).bounceClick { openUpdate(ctx, u) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Box(
                Modifier.size(36.dp).clip(CircleShape).bounceClick(0.9f) { LibraryStore.updateSettings { it.copy(skippedUpdate = u.version) } },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, "Not now", tint = Bop.Text2, modifier = Modifier.size(18.dp)) }
        }
    }
}
