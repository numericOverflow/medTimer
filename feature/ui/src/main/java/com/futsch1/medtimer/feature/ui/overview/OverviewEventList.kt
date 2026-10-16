package com.futsch1.medtimer.feature.ui.overview

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.withoutVisualEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.futsch1.medtimer.core.datastore.PreferencesDataSource
import com.futsch1.medtimer.core.domain.model.ReminderType
import com.futsch1.medtimer.core.ui.list.SelectionListController
import com.futsch1.medtimer.core.ui.preview.MedTimerPreview
import com.futsch1.medtimer.core.ui.rememberMedicineIcon
import com.futsch1.medtimer.core.ui.theme.MedTimerTheme
import com.futsch1.medtimer.feature.ui.overview.actions.ActionsFactory
import com.futsch1.medtimer.feature.ui.overview.actions.Button
import com.futsch1.medtimer.feature.ui.overview.model.OverviewEvent
import com.futsch1.medtimer.feature.ui.overview.model.OverviewEventContent
import com.futsch1.medtimer.feature.ui.overview.model.OverviewGroup
import com.futsch1.medtimer.feature.ui.overview.model.OverviewState
import com.futsch1.medtimer.feature.ui.overview.model.groupOverviewEvents
import com.google.gson.Gson
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.absoluteValue

/** Caps row width on wide/landscape screens so event rows stay readable instead of stretching edge to edge. */
private val EVENT_LIST_MAX_WIDTH = 540.dp

/** Bottom padding so the FAB, which floats over the list's bottom-end corner, doesn't cover the last event. */
private val FAB_CLEARANCE = 80.dp

/** Test seam: disable cache window to fix stale semantics position in row-order tests */
var overviewEventListCacheWindowEnabled = true

/** Test seam: the jump to the next upcoming event leaves the rows above it uncomposed, so index-based tests miss them. */
var overviewEventListScrollToNowEnabled = true

/**
 * The events of one day, one card per [OverviewGroup] under the group's time header.
 *
 * Every event stays its own lazy list item, and the first item of a group carries the header.
 * That keeps the item index equal to the running event index of [OverviewTestTags.event], and it
 * lets each row animate on its own when it moves between groups.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverviewEventList(
    groups: ImmutableList<OverviewGroup>,
    selection: SelectionListController<OverviewEvent>,
    onEventClick: (OverviewEvent) -> Unit,
    onEnterSelectionMode: (OverviewEvent) -> Unit,
    onAction: (Button, OverviewEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val railColor = MaterialTheme.colorScheme.primary
    val railAnchors = remember { mutableStateMapOf<Int, Float>() }
    var listTop by remember { mutableFloatStateOf(0f) }
    val overscrollEffect = rememberOverscrollEffect()
    val listState = if (overviewEventListCacheWindowEnabled) {
        rememberLazyListState(cacheWindow = LazyLayoutCacheWindow(ahead = 200.dp, behind = 200.dp))
    } else {
        rememberLazyListState()
    }
    var scrolledToNow by remember { mutableStateOf(false) }
    val groupStartIndices = remember(groups) { groups.runningFold(0) { start, group -> start + group.events.size } }

    LaunchedEffect(groups) {
        if (!overviewEventListScrollToNowEnabled || scrolledToNow || groups.isEmpty()) {
            return@LaunchedEffect
        }

        scrolledToNow = true
        // Scroll to the group rather than to the event, so that the group's header stays in view.
        val groupIndex = groups.indexOfFirst { group ->
            group.events.any { it.state == OverviewState.PENDING || it.state == OverviewState.RAISED }
        }
        if (groupIndex >= 0) {
            listState.scrollToItem(groupStartIndices[groupIndex])
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = FAB_CLEARANCE),
        overscrollEffect = overscrollEffect?.withoutVisualEffect(),
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .wrapContentWidth(Alignment.Start)
            .widthIn(max = EVENT_LIST_MAX_WIDTH)
            .padding(horizontal = 4.dp)
            .onGloballyPositioned { listTop = it.positionInRoot().y }
            .overscroll(overscrollEffect)
            .clipToBounds()
            .drawBehind { drawRailPills(railAnchors.values, listTop, railColor) }
            .testTag(OverviewTestTags.EVENT_LIST),
    ) {
        groups.forEachIndexed { groupIndex, group ->
            itemsIndexed(group.events, key = { _, event -> event.id }) { indexInGroup, event ->
                DisposableEffect(event.id) {
                    onDispose { railAnchors.remove(event.id) }
                }
                val position = OverviewGroupPosition.of(indexInGroup, group.events.size)
                Column(Modifier.animateItem()) {
                    if (position.isGroupStart) {
                        OverviewGroupHeader(
                            timestamp = group.timestamp,
                            modifier = Modifier
                                .padding(top = if (groupIndex > 0) EVENT_SPACING else 0.dp)
                                .testTag(OverviewTestTags.group(groupIndex)),
                        )
                    }
                    OverviewEventItem(
                        content = event.content,
                        state = event.state,
                        color = event.color,
                        icon = rememberMedicineIcon(event.icon),
                        isSelected = selection.isSelected(event),
                        isInSelectionMode = selection.isInSelectionMode,
                        actions = remember(event) { ActionsFactory().createActions(event) },
                        onClick = { onEventClick(event) },
                        onToggleSelection = { selection.toggleSelection(event) },
                        onEnterSelectionMode = { onEnterSelectionMode(event) },
                        onAction = { button -> onAction(button, event) },
                        modifier = Modifier.testTag(OverviewTestTags.event(groupStartIndices[groupIndex] + indexInGroup)),
                        position = position,
                        onRailAnchor = { railAnchors[event.id] = it },
                    )
                }
            }
        }
    }
}

internal fun Modifier.overviewDaySwipe(threshold: Float, onDaySwipe: (Int) -> Unit): Modifier =
    pointerInput(threshold, onDaySwipe) {
        var totalDrag = 0f
        var swipeHandled = false
        detectHorizontalDragGestures(
            onDragStart = {
                totalDrag = 0f
                swipeHandled = false
            },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                totalDrag += dragAmount
            },
            onDragEnd = {
                if (!swipeHandled && totalDrag.absoluteValue > threshold) {
                    swipeHandled = true
                    onDaySwipe(if (totalDrag < 0) 1 else -1)
                }
            },
        )
    }

/**
 * One pill per gap between adjacent events, spanning the corridor between the two neighboring
 * state buttons. A taller item centres its button further from the gap, so the pill facing it
 * stretches toward it, and so does a group header between two groups.
 *
 * The rows of one group stack without a gap, which leaves a corridor too short for [RAIL_INSET].
 * There the inset shrinks, so that the rail still runs through the group as a short pill.
 *
 * [anchors] are button centres in root coordinates, reported by the items themselves; sorting
 * them recovers the visual order without assuming anything about composition order.
 */
private fun DrawScope.drawRailPills(anchors: Collection<Float>, listTop: Float, color: Color) {
    if (anchors.size < 2) return
    val centers = anchors.map { it - listTop }.sorted()
    val width = RAIL_PILL_WIDTH.toPx()
    val buttonRadius = EVENT_STATE_BUTTON_SIZE.toPx() / 2
    for (i in 0 until centers.lastIndex) {
        val corridorTop = centers[i] + buttonRadius
        val corridorBottom = centers[i + 1] - buttonRadius
        val inset = RAIL_INSET.toPx().coerceAtMost((corridorBottom - corridorTop - width) / 2).coerceAtLeast(0f)
        val top = corridorTop + inset
        val bottom = corridorBottom - inset
        if (bottom <= top) continue
        drawRoundRect(
            color = color,
            topLeft = Offset(RAIL_CENTER_X.toPx() - width / 2, top),
            size = Size(width, bottom - top),
            cornerRadius = CornerRadius(width / 2),
        )
    }
}

private val EVENT_LIST_PREVIEW_TIME: Instant =
    LocalDate.of(2026, 5, 28).atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant()

private class EventListPreviewEvent(
    override val id: Int,
    override val timestamp: Long,
    override val content: OverviewEventContent,
    override val state: OverviewState,
    preferencesDataSource: PreferencesDataSource,
) : OverviewEvent(preferencesDataSource) {
    override val icon = 0
    override val color: Int? = null
    override val reminderId = id
    override val cannotSkipMedicine = false
}

@Composable
private fun rememberEventListPreviewGroups(): ImmutableList<OverviewGroup> {
    val context = LocalContext.current
    val preferencesDataSource = remember {
        val prefs = context.getSharedPreferences("overview_event_list_preview", Context.MODE_PRIVATE)
        PreferencesDataSource(prefs, CoroutineScope(Dispatchers.Unconfined), Gson())
    }
    return remember {
        listOf(
            EventListPreviewEvent(
                id = 1,
                timestamp = EVENT_LIST_PREVIEW_TIME.epochSecond,
                content = OverviewEventContent(
                    reminderType = ReminderType.TIME_BASED,
                    time = EVENT_LIST_PREVIEW_TIME,
                    medicineName = "Vitamin D",
                    dose = "1 tablet",
                    takenTime = EVENT_LIST_PREVIEW_TIME.plus(Duration.ofMinutes(42)),
                ),
                state = OverviewState.TAKEN,
                preferencesDataSource = preferencesDataSource,
            ),
            EventListPreviewEvent(
                id = 2,
                timestamp = EVENT_LIST_PREVIEW_TIME.plus(Duration.ofHours(4)).epochSecond,
                content = OverviewEventContent(
                    reminderType = ReminderType.TIME_BASED,
                    time = EVENT_LIST_PREVIEW_TIME.plus(Duration.ofHours(4)),
                    medicineName = "Ibuprofen",
                    dose = "2 tablets",
                ),
                state = OverviewState.RAISED,
                preferencesDataSource = preferencesDataSource,
            ),
            EventListPreviewEvent(
                id = 4,
                timestamp = EVENT_LIST_PREVIEW_TIME.plus(Duration.ofHours(4)).epochSecond,
                content = OverviewEventContent(
                    reminderType = ReminderType.TIME_BASED,
                    time = EVENT_LIST_PREVIEW_TIME.plus(Duration.ofHours(4)),
                    medicineName = "Magnesium",
                    dose = "1 tablet",
                ),
                state = OverviewState.RAISED,
                preferencesDataSource = preferencesDataSource,
            ),
            EventListPreviewEvent(
                id = 3,
                timestamp = EVENT_LIST_PREVIEW_TIME.plus(Duration.ofHours(8)).epochSecond,
                content = OverviewEventContent(
                    reminderType = ReminderType.TIME_BASED,
                    time = EVENT_LIST_PREVIEW_TIME.plus(Duration.ofHours(8)),
                    medicineName = "Antibiotic",
                    dose = "1 capsule",
                ),
                state = OverviewState.SKIPPED,
                preferencesDataSource = preferencesDataSource,
            ),
        ).let { groupOverviewEvents(it, combineNotifications = true) }
    }
}

@MedTimerPreview
@Composable
private fun OverviewEventListPreview() {
    val selection = remember { SelectionListController<OverviewEvent> { it.id } }
    MedTimerTheme {
        Surface {
            OverviewEventList(
                groups = rememberEventListPreviewGroups(),
                selection = selection,
                onEventClick = {},
                onEnterSelectionMode = {},
                onAction = { _, _ -> },
            )
        }
    }
}
