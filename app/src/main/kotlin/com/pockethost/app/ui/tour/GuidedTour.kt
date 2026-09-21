@file:OptIn(ExperimentalFoundationApi::class)

package com.pockethost.app.ui.tour

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Elements a guided tour can point at. An anchor is attached to a composable with
 * [tourAnchor]; only anchors that are currently on screen can be highlighted, so
 * steps should point at things that are reachable from the screen they run on.
 */
enum class TourAnchor {
    SERVER_CARD,
    START_BUTTON,
    JOIN_CARD,
    NAV_PLAYERS,
    NAV_STORAGE,
    NAV_MODS,
    NAV_SETTINGS,
    SERVER_TYPE_CARD,
    SHEET_SERVER_TYPES,
    SHEET_VERSIONS,
    SHEET_CONFIRM_BUTTON,
    JAR_DOWNLOAD_BUTTON,
    JAR_SELECT_BUTTON,
    EULA_ACCEPT_BUTTON,
    SETTINGS_SERVER_TAB,
    PLUS_SERVER_BUTTON,
    TOPBAR_SETTINGS,
    NAV_NEW_SERVER,
    CREATE_SERVER_NAME,
    CREATE_SERVER_TYPE,
    CREATE_SERVER_GAMEPLAY,
    CREATE_SERVER_GAMEMODE,
    CREATE_SERVER_DIFFICULTY,
    CREATE_SERVER_CROSSPLAY,
    CREATE_SERVER_SUBMIT
}

/** The tours the app can run. Used to persist "already seen" state per tour. */
enum class TourId { FIRST_SERVER, SERVER_LIVE, CREATE_SERVER }

/** How the user gets from one step to the next. */
sealed interface TourAdvance {
    /** The tooltip shows a button; tapping it (or the dimmed area) moves on. */
    data class Button(val label: String) : TourAdvance

    /**
     * The highlighted control stays tappable and the dimmed area does not react.
     * The screen that owns the control is responsible for calling
     * [TourController.completeStep] once the user has actually done the thing.
     */
    data class TapTarget(val hint: String) : TourAdvance
}

data class TourStep(
    val key: String,
    val anchor: TourAnchor?,
    val title: String,
    val body: String,
    val advance: TourAdvance = TourAdvance.Button("Next"),
    val spotlightPadding: Dp = 6.dp,
    val spotlightCornerRadius: Dp = 20.dp
)

/**
 * Drives the overlay. One instance lives at the top of the app and is handed to
 * the rest of the tree through [LocalTourController], so any screen can register
 * an anchor or report that a step has been satisfied.
 */
@Stable
class TourController {

    private val anchorBounds = mutableStateMapOf<TourAnchor, Rect>()
    private val anchorRequesters = mutableMapOf<TourAnchor, BringIntoViewRequester>()

    var steps by mutableStateOf<List<TourStep>>(emptyList())
        private set

    var stepIndex by mutableIntStateOf(0)
        private set

    var runningTour by mutableStateOf<TourId?>(null)
        private set

    private var onFinish: ((TourId, Boolean) -> Unit)? = null

    val currentStep: TourStep?
        get() = steps.getOrNull(stepIndex)

    val isRunning: Boolean
        get() = runningTour != null && currentStep != null

    val isActive: Boolean
        get() = isRunning

    fun start(tour: TourId, steps: List<TourStep>, onFinish: ((TourId, Boolean) -> Unit)? = null) {
        if (steps.isEmpty()) return
        this.steps = steps
        this.stepIndex = 0
        this.onFinish = onFinish
        this.runningTour = tour
    }

    fun next() {
        val tour = runningTour ?: return
        if (stepIndex < steps.lastIndex) stepIndex++ else finish(tour, completed = true)
    }

    fun back() {
        if (stepIndex > 0) stepIndex--
    }

    fun skip() {
        runningTour?.let { finish(it, completed = false) }
    }

    /**
     * Drops the tour without reporting a result, for when the app moves somewhere the
     * tour cannot follow — a full-screen download, for instance. The caller's
     * [start] callback is deliberately not run, so a tour interrupted this way is not
     * recorded as seen and can be offered again.
     */
    fun cancel() {
        runningTour = null
        steps = emptyList()
        stepIndex = 0
        onFinish = null
    }

    /**
     * Moves past [stepKey] if it is the step currently on screen. Screens call this
     * when the user has done what a [TourAdvance.TapTarget] step asked for, so a
     * late or duplicate report can never skip an unrelated step.
     */
    fun completeStep(stepKey: String) {
        if (currentStep?.key == stepKey) next()
    }

    fun advanceTo(stepKey: String) {
        val targetIdx = steps.indexOfFirst { it.key == stepKey }
        if (targetIdx >= 0) {
            stepIndex = targetIdx
        } else {
            next()
        }
    }

    fun boundsOf(anchor: TourAnchor): Rect? = anchorBounds[anchor]

    internal fun register(anchor: TourAnchor, bounds: Rect, requester: BringIntoViewRequester) {
        anchorRequesters[anchor] = requester
        val known = anchorBounds[anchor]
        // onGloballyPositioned fires on every scroll frame; only publish real moves so
        // the overlay does not recompose continuously while a list is being flung.
        if (known == null || !known.isRoughly(bounds)) {
            anchorBounds[anchor] = bounds
        }
    }

    internal fun unregister(anchor: TourAnchor) {
        anchorBounds.remove(anchor)
        anchorRequesters.remove(anchor)
    }

    internal suspend fun bringAnchorIntoView(anchor: TourAnchor) {
        anchorRequesters[anchor]?.bringIntoView()
    }

    private fun finish(tour: TourId, completed: Boolean) {
        val callback = onFinish
        runningTour = null
        steps = emptyList()
        stepIndex = 0
        onFinish = null
        callback?.invoke(tour, completed)
    }
}

private fun Rect.isRoughly(other: Rect): Boolean =
    abs(left - other.left) < 0.5f &&
        abs(top - other.top) < 0.5f &&
        abs(right - other.right) < 0.5f &&
        abs(bottom - other.bottom) < 0.5f

val LocalTourController = staticCompositionLocalOf<TourController?> { null }

/**
 * Marks this composable as the target for [anchor]. Safe to use anywhere: with no
 * controller in the tree the modifier does nothing.
 */
@Composable
fun Modifier.tourAnchor(anchor: TourAnchor): Modifier {
    val controller = LocalTourController.current ?: return this
    val requester = remember { BringIntoViewRequester() }
    DisposableEffect(controller, anchor) {
        onDispose { controller.unregister(anchor) }
    }
    return this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { coordinates ->
            controller.register(anchor, coordinates.boundsInRoot(), requester)
        }
}

/** Swallows taps so the dimmed part of the overlay cannot reach the UI underneath. */
internal fun Modifier.blockTouches(key: Any?, onTap: (() -> Unit)? = null): Modifier =
    pointerInput(key) {
        detectTapGestures { onTap?.invoke() }
    }
