package com.canimussurdis.reachout.ui.common

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.canimussurdis.reachout.data.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The dating-app style card: big picture on top, big name underneath.
 * [swipeProgress] runs from -1 (fully left) to 1 (fully right) and fades in the PASS / KEEP stamps.
 */
@Composable
fun ContactCard(
    contact: Contact,
    look: AccessoryLook,
    modifier: Modifier = Modifier,
    swipeProgress: Float = 0f,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                ContactPicture(contact, look, Modifier.fillMaxWidth().weight(1f))
                Text(
                    text = contact.name,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = nameSize(contact.name),
                        lineHeight = nameSize(contact.name) * 1.15f,
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (swipeProgress > 0.05f) {
                Stamp("KEEP", MaterialTheme.colorScheme.tertiary, swipeProgress, -12f, Modifier.align(Alignment.TopStart))
            }
            if (swipeProgress < -0.05f) {
                Stamp("PASS", MaterialTheme.colorScheme.error, -swipeProgress, 12f, Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

private fun nameSize(name: String) = when {
    name.length <= 14 -> 44.sp
    name.length <= 24 -> 36.sp
    else -> 28.sp
}

@Composable
private fun Stamp(text: String, color: Color, progress: Float, angle: Float, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier
            .padding(24.dp)
            .graphicsLayer {
                alpha = progress.coerceIn(0f, 1f)
                rotationZ = angle
            }
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
            .border(3.dp, color, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
        color = color,
        fontSize = 30.sp,
        fontWeight = FontWeight.Black,
    )
}

/** The contact's own photo if they have one, otherwise their cartoon animal wearing [look]. */
@Composable
fun ContactPicture(contact: Contact, look: AccessoryLook, modifier: Modifier = Modifier) {
    val photo by rememberContactPhoto(contact.photoUri)
    val bg = animalBackground(contact.animalIndex)
    when (val p = photo) {
        is PhotoState.Loaded -> Image(
            bitmap = p.bitmap,
            contentDescription = "Photo of ${contact.name}",
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
        PhotoState.Loading -> Box(modifier.background(bg))
        PhotoState.None -> Box(modifier.background(bg), contentAlignment = Alignment.Center) {
            AnimalAvatar(contact.animalIndex, look, Modifier.fillMaxSize(0.85f))
        }
    }
}

private sealed interface PhotoState {
    data object Loading : PhotoState
    data object None : PhotoState
    data class Loaded(val bitmap: ImageBitmap) : PhotoState
}

@Composable
private fun rememberContactPhoto(uri: String?): State<PhotoState> {
    val context = LocalContext.current
    return produceState<PhotoState>(if (uri == null) PhotoState.None else PhotoState.Loading, uri) {
        if (uri == null) {
            value = PhotoState.None
            return@produceState
        }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
        value = if (bitmap != null) PhotoState.Loaded(bitmap) else PhotoState.None
    }
}

/** The ☰ button that opens Settings. Drawn by hand so no icon library is needed. */
@Composable
fun MenuButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onBackground
    IconButton(onClick = onClick, modifier = modifier.semantics { contentDescription = "Menu" }) {
        Canvas(Modifier.size(24.dp)) {
            val stroke = 2.5.dp.toPx()
            for (i in 0..2) {
                val y = size.height * (0.25f + i * 0.25f)
                drawLine(color, Offset(size.width * 0.1f, y), Offset(size.width * 0.9f, y), stroke, StrokeCap.Round)
            }
        }
    }
}
