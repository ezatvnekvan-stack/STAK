package app.stak.nativeapp

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

data class MotionPreferences(val reduced: Boolean = false, val haptics: Boolean = true)

val LocalStakMotion = staticCompositionLocalOf { MotionPreferences() }
val StakEase = CubicBezierEasing(.22f, .68f, .18f, 1f)

fun <T> motionSpec(reduced: Boolean, duration: Int = 220): FiniteAnimationSpec<T> =
    if (reduced) snap() else tween(duration, easing = StakEase)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StakBottomSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (LocalStakMotion.current.reduced) {
        Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.matchParentSize().clickable(onClick = onDismissRequest))
                Surface(
                    Modifier.fillMaxWidth(),
                    shape =
                        androidx.compose.foundation.shape.RoundedCornerShape(
                            topStart = 24.dp,
                            topEnd = 24.dp,
                        ),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(Modifier.navigationBarsPadding().padding(top = 20.dp), content = content)
                }
            }
        }
    } else
        ModalBottomSheet(
            onDismissRequest,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            content = content,
        )
}

@Composable
fun MotionButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val policy = LocalStakMotion.current
    val view = LocalView.current
    val scale by
        animateFloatAsState(
            if (pressed && enabled && !policy.reduced) .98f else 1f,
            if (policy.reduced) snap()
            else tween(120, easing = CubicBezierEasing(.2f, .7f, .2f, 1f)),
            label = "Tactile press",
        )
    Surface(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = 44.dp)
            .clickable(
                enabled = enabled,
                interactionSource = interactions,
                indication = null,
                role = Role.Button,
            ) {
                if (policy.haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color =
            if (primary) MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .4f)
            else androidx.compose.ui.graphics.Color.Transparent,
        contentColor =
            if (primary) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .4f),
        border =
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) Icon(icon, null, Modifier.size(16.dp))
            Text(
                text.uppercase(java.util.Locale.ROOT),
                style =
                    MaterialTheme.typography.labelLarge.copy(
                        lineHeight = 15.4.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    ),
            )
        }
    }
}

@Composable
fun MotionTabs(
    labels: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val reduced = LocalStakMotion.current.reduced
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(
                MaterialTheme.colorScheme.surfaceContainer,
                androidx.compose.foundation.shape.RoundedCornerShape(13.dp),
            )
            .selectableGroup()
    ) {
        val width = maxWidth / labels.size
        val x =
            animateDpAsState(
                width * selected,
                if (reduced) snap() else spring(dampingRatio = 1.108f, stiffness = 600f),
                label = "Selection glide",
            )
        Box(
            Modifier.offset { IntOffset(x.value.roundToPx(), 0) }
                .width(width)
                .fillMaxHeight()
                .padding(4.dp)
                .background(
                    MaterialTheme.colorScheme.onSurface,
                    androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                )
        )
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, text ->
                Box(
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected == index,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text.uppercase(java.util.Locale.ROOT),
                        style = MaterialTheme.typography.labelLarge,
                        color =
                            if (index == selected) MaterialTheme.colorScheme.background
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun DotStatus(modifier: Modifier = Modifier, label: String = "Loading", progress: Float? = null) {
    val reduced = LocalStakMotion.current.reduced
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(reduced, lifecycle, progress) {
        if (!reduced && lifecycle.isAtLeast(Lifecycle.State.RESUMED) && progress == null) {
            while (true) {
                delay(130)
                step = (step + 1) % 8
            }
        }
    }
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier.size(58.dp, 16.dp).semantics {
            contentDescription = label
            progressBarRangeInfo =
                progress?.let { ProgressBarRangeInfo(it.coerceIn(0f, 1f), 0f..1f) }
                    ?: ProgressBarRangeInfo.Indeterminate
        }
    ) {
        repeat(5) { i ->
            val active =
                if (progress != null) i < (progress * 5).roundToInt()
                else reduced || step == i || step - 1 == i
            drawCircle(
                color.copy(alpha = if (active) 1f else .22f),
                2.5.dp.toPx(),
                Offset((i * 11 + 5).dp.toPx(), size.height / 2),
            )
        }
    }
}

@Composable
fun SegmentedProgress(progress: Float, modifier: Modifier = Modifier) {
    val target = progress.coerceIn(0f, 1f)
    val fill by
        animateFloatAsState(
            target,
            motionSpec(LocalStakMotion.current.reduced, 260),
            label = "Segment fill",
        )
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier.fillMaxWidth().height(5.dp).semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
        }
    ) {
        val gap = 2.dp.toPx()
        val width = (size.width - 23 * gap) / 24
        repeat(24) { i ->
            val x = i * (width + gap)
            drawRect(
                color.copy(alpha = .15f),
                Offset(x, 0f),
                Size(width.coerceAtLeast(0f), size.height),
            )
            val fraction = (fill * 24 - i).coerceIn(0f, 1f)
            if (fraction > 0)
                drawRect(
                    color,
                    Offset(x, 0f),
                    Size(width.coerceAtLeast(0f) * fraction, size.height),
                )
        }
    }
}

@Composable
fun DrawnCheck(checked: Boolean, modifier: Modifier = Modifier) {
    val fraction by
        animateFloatAsState(
            if (checked) 1f else 0f,
            motionSpec(LocalStakMotion.current.reduced, 190),
            label = "Check stroke",
        )
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier.size(24.dp).semantics {
            contentDescription = if (checked) "Watched" else "Unwatched"
        }
    ) {
        val path =
            Path().apply {
                moveTo(size.width * .18f, size.height * .52f)
                lineTo(size.width * .42f, size.height * .75f)
                lineTo(size.width * .84f, size.height * .25f)
            }
        val measure = PathMeasure().apply { setPath(path, false) }
        val segment = Path()
        measure.getSegment(0f, measure.length * fraction, segment, true)
        drawPath(segment, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}
