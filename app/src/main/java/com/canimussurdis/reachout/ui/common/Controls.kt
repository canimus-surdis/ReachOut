package com.canimussurdis.reachout.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.canimussurdis.reachout.data.Contact

/** A round, raised icon button (menu, back, − and +). 44dp, so it's easy to tap. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Int = 44,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = modifier
            .size(size.dp)
            .raised(CircleShape, if (enabled) 6.dp else 1.dp),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.35f),
            // A 20dp icon centred in the circle.
            modifier = Modifier.padding(((size - 20) / 2).dp),
        )
    }
}

/** The button that opens Settings. */
@Composable
fun MenuButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CircleIconButton(AppIcons.Menu, "Menu", onClick, modifier)
}

/** "Mobile · (555) 555-0101", or null if they have no number. */
fun Contact.phoneLine(): String? {
    val number = mainNumber ?: return null
    val label = phones.firstOrNull { it.number == number }?.label?.takeIf { it.isNotBlank() }
    return if (label != null) "$label · $number" else number
}
