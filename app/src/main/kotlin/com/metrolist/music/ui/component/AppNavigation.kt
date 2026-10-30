/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.ui.screens.Screens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

@Stable
private fun isRouteSelected(currentRoute: String?, screenRoute: String, navigationItems: List<Screens>): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == screenRoute) return true
    if (navigationItems.any { it.route == screenRoute } &&
        currentRoute.startsWith("$screenRoute/")) return true

    // Fix: match the route template, not the resolved route
    if (screenRoute == "search_input" &&
        (currentRoute.startsWith("search/") || currentRoute == "search/{query}")) return true

    return false
}

@Composable
fun AppNavigationRail(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
    onHomeLongHold: (() -> Unit)? = null,
) {
    val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current

    NavigationRail(
        modifier = modifier,
        containerColor = containerColor
    ) {
        Spacer(modifier = Modifier.weight(1f))

        navigationItems.forEach { screen ->
            val isSelected = remember(currentRoute, screen.route) {
                isRouteSelected(currentRoute, screen.route, navigationItems)
            }
            val currentIsSelected by rememberUpdatedState(isSelected)
            val iconRes = remember(isSelected, screen) {
                if (isSelected) screen.iconIdActive else screen.iconIdInactive
            }

            val isSearchItem = screen == Screens.Search && onSearchLongClick != null
            val isHomeHoldItem = screen == Screens.Home && onHomeLongHold != null
            val interactionSource = remember { MutableInteractionSource() }

            // Long press detection using InteractionSource
            if (isSearchItem || isHomeHoldItem) {
                LaunchedEffect(interactionSource) {
                    var isLongClick = false
                    interactionSource.interactions.collectLatest { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> {
                                isLongClick = false
                                delay(if (isHomeHoldItem) 800L else viewConfiguration.longPressTimeoutMillis)
                                isLongClick = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isHomeHoldItem) onHomeLongHold.invoke() else onSearchLongClick?.invoke()
                            }
                            is PressInteraction.Release -> {
                                if (!isLongClick) {
                                    onItemClick(screen, currentIsSelected)
                                }
                            }
                            is PressInteraction.Cancel -> {
                                isLongClick = false
                            }
                        }
                    }
                }
            }

            NavigationRailItem(
                selected = isSelected,
                onClick = {
                    if (!isSearchItem && !isHomeHoldItem) {
                        onItemClick(screen, currentIsSelected)
                    }
                    // Long presses are handled via InteractionSource
                },
                interactionSource = interactionSource,
                icon = {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = stringResource(screen.titleId)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun AppNavigationBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    slimNav: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
    onHomeLongHold: (() -> Unit)? = null,
) {
    val hazeState = LocalHazeState.current
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current

    val selectedIndex = remember(currentRoute, navigationItems) {
        val idx = navigationItems.indexOfFirst { isRouteSelected(currentRoute, it.route, navigationItems) }
        if (idx >= 0) idx else 0
    }

    LaunchedEffect(selectedIndex) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val dockHeight = if (slimNav) 52.dp else 60.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dockHeight)
                .background(
                    color = if (pureBlack) Color.Black else Color(0xF20B0C12),
                    shape = RoundedCornerShape(32.dp)
                )
                .obsidianGlassEffect(
                    hazeState = hazeState,
                    shape = RoundedCornerShape(32.dp),
                    blurRadius = 25.dp
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.03f)
                        )
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
                .padding(horizontal = 4.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                navigationItems.forEachIndexed { index, screen ->
                    val isSelected = (index == selectedIndex)
                    val currentIsSelected by rememberUpdatedState(isSelected)

                    val isSearchItem = screen == Screens.Search && onSearchLongClick != null
                    val isHomeHoldItem = screen == Screens.Home && onHomeLongHold != null
                    val interactionSource = remember { MutableInteractionSource() }

                    // Long press detection
                    if (isSearchItem || isHomeHoldItem) {
                        LaunchedEffect(interactionSource) {
                            var isLongClick = false
                            interactionSource.interactions.collectLatest { interaction ->
                                when (interaction) {
                                    is PressInteraction.Press -> {
                                        isLongClick = false
                                        delay(if (isHomeHoldItem) 800L else viewConfiguration.longPressTimeoutMillis)
                                        isLongClick = true
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (isHomeHoldItem) onHomeLongHold.invoke() else onSearchLongClick?.invoke()
                                    }
                                    is PressInteraction.Release -> {
                                        if (!isLongClick) {
                                            onItemClick(screen, currentIsSelected)
                                        }
                                    }
                                    is PressInteraction.Cancel -> {
                                        isLongClick = false
                                    }
                                }
                            }
                        }
                    }

                    val tabWeight by animateFloatAsState(
                        targetValue = if (isSelected && !slimNav) 1.6f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = 0.78f,
                            stiffness = 380f
                        ),
                        label = "TabWeight"
                    )

                    val activeAlpha by animateFloatAsState(
                        targetValue = if (isSelected) 1.0f else 0.0f,
                        animationSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = 400f
                        ),
                        label = "ActiveAlpha"
                    )

                    Box(
                        modifier = Modifier
                            .weight(tabWeight)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(26.dp))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {
                                    if (!isSearchItem && !isHomeHoldItem) {
                                        onItemClick(screen, currentIsSelected)
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Animated pill background for the active tab
                        if (activeAlpha > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = activeAlpha }
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                            )
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        brush = Brush.verticalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                            )
                                        ),
                                        shape = RoundedCornerShape(24.dp)
                                    )
                            )
                        }

                        // Tab Content: Icon + Smooth Animated Text
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        val scale = 1.0f + activeAlpha * 0.10f
                                        scaleX = scale
                                        scaleY = scale
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                val animatedTint by animateColorAsState(
                                    targetValue = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                    },
                                    animationSpec = tween(durationMillis = 180),
                                    label = "NavIconTint"
                                )
                                Icon(
                                    painter = painterResource(id = if (isSelected) screen.iconIdActive else screen.iconIdInactive),
                                    contentDescription = stringResource(screen.titleId),
                                    tint = animatedTint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            if (!slimNav) {
                                AnimatedVisibility(
                                    visible = isSelected,
                                    enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                                            expandHorizontally(
                                                spring(
                                                    dampingRatio = 0.78f,
                                                    stiffness = 380f
                                                )
                                            ),
                                    exit = fadeOut(tween(durationMillis = 90)) +
                                            shrinkHorizontally(
                                                spring(
                                                    dampingRatio = 0.90f,
                                                    stiffness = 450f
                                                )
                                            )
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(screen.titleId),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Clip
                                        )
                                    }
                                }
                            }
                        }

                        // Slim navigation active indicator dot
                        if (slimNav && activeAlpha > 0.05f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 4.dp)
                                    .width(10.dp)
                                    .height(2.5.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .graphicsLayer { alpha = activeAlpha }
                            )
                        }
                    }
                }
            }
        }
    }
}
