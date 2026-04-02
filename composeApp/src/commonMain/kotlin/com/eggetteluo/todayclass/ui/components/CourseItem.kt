package com.eggetteluo.todayclass.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eggetteluo.todayclass.model.Course
import com.eggetteluo.todayclass.util.CourseStatus
import com.eggetteluo.todayclass.util.TimeUtil.getCourseStatus
import com.eggetteluo.todayclass.util.TimeUtil.getFormattedTimeRange
import kotlin.math.abs

@Composable
fun CourseItem(
    course: Course,
    isToday: Boolean,
    modifier: Modifier = Modifier
) {
    val courseColor = rememberCourseColor(course.name)
    val timeRange = remember(course.section, course.location) {
        getFormattedTimeRange(course.section, course.location)
    }
    val status = remember(timeRange, isToday) {
        if (isToday) getCourseStatus(timeRange) else CourseStatus.NONE
    }

    // 动态样式数值
    val containerAlpha by animateFloatAsState(targetValue = if (status == CourseStatus.FINISHED) 0.5f else 1f)

    // 仅进行中时启用呼吸动画，减少无效动画开销
    val pulseAlpha = if (status == CourseStatus.ONGOING) {
        val infiniteTransition = rememberInfiniteTransition(label = "breathing")
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
            label = "pulse"
        ).value
    } else {
        0.7f
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .alpha(containerAlpha),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = when (status) {
                CourseStatus.ONGOING -> courseColor.copy(alpha = 0.05f)
                CourseStatus.FINISHED -> Color.Transparent
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                when (status) {
                    CourseStatus.ONGOING -> courseColor.copy(alpha = 0.4f)
                    CourseStatus.FINISHED -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                }
            ),
            width = if (status == CourseStatus.ONGOING) 1.2.dp else 0.6.dp
        )
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min).fillMaxWidth()) {
            // 1. 左侧指示色条 (进行中时带呼吸感)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .padding(vertical = 12.dp)
                    .background(
                        color = if (status == CourseStatus.FINISHED) Color.LightGray.copy(alpha = 0.5f)
                        else courseColor.copy(alpha = if (status == CourseStatus.ONGOING) pulseAlpha else 0.7f),
                        shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                    )
            )

            Column(
                modifier = Modifier
                    .padding(start = 12.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 第一行：标题与状态标签
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = course.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = if (status == CourseStatus.FINISHED) Color.Gray else MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        StatusBadge(status, courseColor)
                    }

                    TeacherChip(course.teacher, isGray = status == CourseStatus.FINISHED)
                }

                // 信息部分
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoItem(
                        icon = Icons.Default.LocationOn,
                        text = course.location,
                        tint = if (status == CourseStatus.FINISHED) Color.Gray else courseColor
                    )
                    InfoItem(
                        icon = Icons.Default.Schedule,
                        text = "第 ${course.section} 节 ($timeRange)",
                        tint = if (status == CourseStatus.FINISHED) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: CourseStatus, accentColor: Color) {
    val (text, color) = when (status) {
        CourseStatus.ONGOING -> "正在进行" to accentColor
        CourseStatus.UPCOMING -> "即将开始" to MaterialTheme.colorScheme.error
        CourseStatus.NONE -> "待开始" to MaterialTheme.colorScheme.outline
        CourseStatus.FINISHED -> "已结束" to Color.Gray
    }

    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (status == CourseStatus.ONGOING) {
                // 进行中增加一个小动点
                val dotAlpha by rememberInfiniteTransition().animateFloat(
                    initialValue = 0.2f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse)
                )
                Box(Modifier.size(6.dp).background(color, RoundedCornerShape(3.dp)).alpha(dotAlpha))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = color
            )
        }
    }
}

@Composable
private fun TeacherChip(name: String, isGray: Boolean) {
    Surface(
        color = if (isGray) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (isGray) Color.Gray else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isGray) Color.Gray else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun InfoItem(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = tint.copy(alpha = 0.7f)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = if (tint == Color.Gray) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = 0.8f
            )
        )
    }
}

@Composable
private fun rememberCourseColor(name: String): Color {
    val colors = listOf(
        Color(0xFF6750A4), // 深紫
        Color(0xFF006A6A), // 墨绿
        Color(0xFFB3261E), // 砖红
        Color(0xFF005AC1), // 亮蓝
        Color(0xFF8B5000)  // 棕褐
    )
    return colors[abs(name.hashCode()) % colors.size]
}
