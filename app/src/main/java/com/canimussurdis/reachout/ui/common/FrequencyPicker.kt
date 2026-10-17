package com.canimussurdis.reachout.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.canimussurdis.reachout.data.KeepFrequency

/**
 * The "how often" choices in a recessed track, two per row. The chosen one is raised.
 * [selected] = null shows none of the four raised.
 * [showCustom] adds a fifth, full-width Custom choice (Settings only, never the swipe screen);
 * [customOn] raises it instead of the four.
 */
@Composable
fun FrequencyPicker(
    selected: KeepFrequency?,
    onSelect: (KeepFrequency) -> Unit,
    modifier: Modifier = Modifier,
    showCustom: Boolean = false,
    customOn: Boolean = false,
    onCustom: () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .recessed(RoundedCornerShape(16.dp))
            .padding(4.dp)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KeepFrequency.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { f ->
                    Segment(f.label, on = !customOn && f == selected, onClick = { onSelect(f) }, modifier = Modifier.weight(1f))
                }
            }
        }
        if (showCustom) {
            Segment("Custom", on = customOn, onClick = onCustom, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Segment(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .heightIn(min = 44.dp)
            .then(if (on) Modifier.raised(shape, 3.dp) else Modifier.clip(shape))
            .selectable(selected = on, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
            color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
