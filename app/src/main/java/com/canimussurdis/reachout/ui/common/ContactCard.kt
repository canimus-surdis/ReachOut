package com.canimussurdis.reachout.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
        modifier = modifier.raised(RoundedCornerShape(26.dp), 18.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                ContactPicture(contact, look, Modifier.fillMaxWidth().weight(1f))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 22.dp),
                ) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = nameSize(contact.name),
                            lineHeight = nameSize(contact.name) * 1.2f,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    contact.phoneLine()?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
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
    name.length <= 14 -> 30.sp
    name.length <= 24 -> 26.sp
    else -> 22.sp
}

@Composable
private fun Stamp(text: String, color: Color, progress: Float, angle: Float, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier
            .padding(18.dp)
            .graphicsLayer {
                alpha = progress.coerceIn(0f, 1f)
                rotationZ = angle / 3f
            }
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
            .border(1.5.dp, color, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        color = color,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.1.em,
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
        val parsed = Uri.parse(uri)
        if (!isTrustedPhoto(context, parsed)) {
            value = PhotoState.None
            return@produceState
        }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(parsed)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
        value = if (bitmap != null) PhotoState.Loaded(bitmap) else PhotoState.None
    }
}

/**
 * Photos only ever come from the phone's contacts or the app's own demo picture. A photo link from
 * anywhere else (for example a tampered backup pointing at another app's files) is ignored.
 */
private fun isTrustedPhoto(context: Context, uri: Uri): Boolean = when (uri.scheme) {
    "content" -> uri.authority == ContactsContract.AUTHORITY
    "android.resource" -> uri.authority == context.packageName
    else -> false
}
