package com.dopdop.bopdop.ui.player

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.ui.components.Artwork
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.glass.ArtPalette
import com.dopdop.bopdop.ui.glass.glassEdges
import com.dopdop.bopdop.ui.share.shareBitmap
import com.dopdop.bopdop.ui.theme.BopType
import kotlinx.coroutines.launch

/**
 * A 9:16 story card of the picked lines: the song's art melted into its own colours behind a
 * glass panel, sized for Instagram Stories. Previewed, then shared as a PNG.
 */
@Composable
fun LyricCardDialog(t: Track, palette: ArtPalette, lines: List<String>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.weight(1f, fill = false).aspectRatio(9f / 16f).clip(RoundedCornerShape(22.dp))
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
            ) { StoryCard(t, palette, lines) }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Close", style = BopType.BodyStrong, color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f)).bounceClick(onClick = onDismiss).padding(horizontal = 22.dp, vertical = 12.dp))
                Text("Share", style = BopType.BodyStrong, color = Color(0xFF15161B),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).bounceClick {
                        scope.launch {
                            val bmp = layer.toImageBitmap().asAndroidBitmap()
                            shareBitmap(ctx, bmp, "${t.title} lyrics", "${t.title} · ${t.artist} — on BopDop", "Share lyrics")
                        }
                    }.padding(horizontal = 28.dp, vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun StoryCard(t: Track, palette: ArtPalette, lines: List<String>) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette.wash, palette.washDeep)))) {
        // The cover, blown up and softened, as the backdrop.
        Artwork(
            t.artwork, Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(40.dp) else Modifier),
            corner = 0.dp, size = 600,
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette.wash.copy(alpha = 0.35f), palette.washDeep.copy(alpha = 0.85f)))))
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.Center) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(Color.White.copy(alpha = 0.14f)).glassEdges(RoundedCornerShape(26.dp), 0.9f, light = false)
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(t.artwork, Modifier.size(46.dp), corner = 10.dp, size = 300)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(t.title, style = BopType.BodyStrong, color = Color.White, maxLines = 1)
                        Text(t.artist, style = BopType.Caption, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(18.dp))
                lines.forEach { line ->
                    Text(line, style = BopType.Lyric.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight(760)).forScript(line), color = Color.White,
                        modifier = Modifier.padding(vertical = 3.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("BopDop", style = BopType.Caption.copy(fontWeight = FontWeight(700)), color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
