package com.meshcore.repeater.ui.components

import com.meshcore.repeater.ui.theme.BauhausCornerShape

import com.meshcore.repeater.i18n.tr
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshcore.repeater.ui.theme.AlertRed
import com.meshcore.repeater.ui.theme.BauhausDarkSignalRed
import kotlin.math.roundToInt

@Composable
fun SlideToConfirm(
    text: String,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = Color(0xFF2A1C1C),
    accentColor: Color = AlertRed,
    thumbColor: Color = AlertRed
) {
    var isConfirmed by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(BauhausCornerShape)
            .background(trackColor)
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val thumbSizePx = with(density) { 52.dp.toPx() }
        val maxOffsetPx = (widthPx - thumbSizePx).coerceAtLeast(0f)

        var dragOffset by remember { mutableFloatStateOf(0f) }

        val animatedTrackColor by animateColorAsState(
            targetValue = if (isConfirmed) BauhausDarkSignalRed else accentColor.copy(alpha = 0.35f),
            label = "trackColor"
        )

        // Progress bar background
        val progressWidth = with(density) { (dragOffset + thumbSizePx / 2).toDp() }
        Box(
            modifier = Modifier
                .width(progressWidth)
                .fillMaxHeight()
                .background(animatedTrackColor)
        )

        // Label in the center
        Text(
            text = if (isConfirmed) tr("BESTÄTIGT") else text,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center)
        )

        // Draggable Thumb handle
        Box(
            modifier = Modifier
                .padding(2.dp)
                .offset { IntOffset(dragOffset.roundToInt(), 0) }
                .size(52.dp)
                .clip(BauhausCornerShape)
                .background(if (isConfirmed) BauhausDarkSignalRed else thumbColor)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        if (!isConfirmed) {
                            dragOffset = (dragOffset + delta).coerceIn(0f, maxOffsetPx)
                        }
                    },
                    onDragStopped = {
                        if (dragOffset >= maxOffsetPx * 0.85f) {
                            dragOffset = maxOffsetPx
                            isConfirmed = true
                            onConfirmed()
                        } else {
                            dragOffset = 0f
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isConfirmed) Icons.Default.Check else Icons.Default.ArrowForward,
                contentDescription = "Slide Thumb",
                tint = Color.Black,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
