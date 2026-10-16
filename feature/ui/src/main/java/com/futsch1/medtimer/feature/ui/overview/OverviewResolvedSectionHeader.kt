package com.futsch1.medtimer.feature.ui.overview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.futsch1.medtimer.core.ui.preview.MedTimerPreview
import com.futsch1.medtimer.core.ui.theme.MedTimerTheme
import com.futsch1.medtimer.core.ui.R as CoreUiR

private val RESOLVED_HEADER_MIN_HEIGHT = 48.dp

/**
 * Divides the open groups from the resolved ones and counts the resolved events. While
 * [collapsible], a tap toggles the section, and the semantics announce the expand or collapse
 * action. A section that the day or the filter forces open shows no chevron, because a tap could
 * not close it.
 */
@Composable
fun OverviewResolvedSectionHeader(
    eventCount: Int,
    expanded: Boolean,
    collapsible: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().testTag(OverviewTestTags.RESOLVED_HEADER)) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(start = GROUP_HEADER_START_PADDING),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = RESOLVED_HEADER_MIN_HEIGHT)
                .then(
                    if (collapsible) {
                        Modifier
                            .clickable(onClick = onToggle)
                            .semantics {
                                if (expanded) collapse { onToggle(); true } else expand { onToggle(); true }
                            }
                    } else {
                        Modifier
                    }
                )
                .padding(start = GROUP_HEADER_START_PADDING, end = 12.dp),
        ) {
            Text(
                text = stringResource(CoreUiR.string.resolved_count, eventCount),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (collapsible) {
                val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "resolved_chevron")
                Icon(
                    painter = painterResource(CoreUiR.drawable.caret_down_fill),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(rotation),
                )
            }
        }
    }
}

@MedTimerPreview
@Composable
private fun OverviewResolvedSectionHeaderCollapsedPreview() {
    MedTimerTheme {
        Surface {
            OverviewResolvedSectionHeader(eventCount = 4, expanded = false, collapsible = true, onToggle = {})
        }
    }
}

@MedTimerPreview
@Composable
private fun OverviewResolvedSectionHeaderExpandedPreview() {
    MedTimerTheme {
        Surface {
            OverviewResolvedSectionHeader(eventCount = 4, expanded = true, collapsible = true, onToggle = {})
        }
    }
}
