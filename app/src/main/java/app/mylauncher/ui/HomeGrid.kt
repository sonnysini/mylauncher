package app.mylauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Tiles per row: every row shows exactly this many whole cards, margin to margin. */
const val CardsPerRow = 5

val Margin = 48.dp
val CardGap = 18.dp

/**
 * Card geometry worked out from the screen width, in whole pixels so five cards and their gaps
 * add up exactly: the few pixels left over go into the side margins ([margin], ~48dp).
 */
@Immutable
data class CardDims(
    val width: Dp,
    val height: Dp,
    val gap: Dp,
    val margin: Dp,
)

fun cardDims(screenWidth: Dp, density: Density): CardDims = with(density) {
    val total = screenWidth.roundToPx()
    val avail = total - 2 * Margin.roundToPx()
    val gap = CardGap.roundToPx()
    val w = (avail - (CardsPerRow - 1) * gap) / CardsPerRow
    val h = w * 9 / 16
    val margin = (total - CardsPerRow * w - (CardsPerRow - 1) * gap) / 2
    CardDims(w.toDp(), h.toDp(), gap.toDp(), margin.toDp())
}

val LocalCardDims = staticCompositionLocalOf {
    CardDims(158.dp, 88.dp, 18.dp, 48.dp)
}

/**
 * Horizontal scrolling that keeps whole cards: the focused card is brought to the inner edge of
 * the side margin, not to the edge of the screen. A row holds a whole number of cards, so the
 * others land on the grid too and nothing is cut in half. Recomputed every frame from the live
 * bounds.
 */
@OptIn(ExperimentalFoundationApi::class)
class MarginBringIntoView(private val marginPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val start = marginPx
        val end = containerSize - marginPx
        return when {
            size > end - start -> offset - start
            offset < start -> offset - start
            offset + size > end -> offset + size - end
            else -> 0f
        }
    }
}

/**
 * While an app is being moved the focus stays on it and nothing asks for a scroll: keep one
 * whole neighbour on each side by scrolling to a whole item (always on the grid).
 */
suspend fun LazyListState.keepWithNeighbours(index: Int, perScreen: Int) {
    val first = firstVisibleItemIndex
    val target = when {
        index - 1 < first -> (index - 1).coerceAtLeast(0)
        index + 1 > first + perScreen - 1 -> index + 2 - perScreen
        else -> return
    }
    animateScrollToItem(target.coerceAtLeast(0))
}
