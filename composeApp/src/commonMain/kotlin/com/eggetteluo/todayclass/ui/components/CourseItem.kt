package com.eggetteluo.todayclass.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
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
    val elevation by animateDpAsState(targetValue = if (status == CourseStatus.ONGOING) 4.dp else 0.dp)
    val containerAlpha by animateFloatAsState(targetValue = if (status == CourseStatus.FINISHED) 0.6f else 1f)

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(containerAlpha),
        shape = RoundedCornerShape(20.dp), // 稍微收敛一点的圆角
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = elevation),
        colors = CardDefaults.outlinedCardColors(
            containerColor = when (status) {
                CourseStatus.ONGOING -> courseColor.copy(alpha = 0.08f)
                CourseStatus.FINISHED -> Color.Transparent // 已结束背景全透明，靠阴影和灰度区分
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                when (status) {
                    CourseStatus.ONGOING -> courseColor.copy(alpha = 0.5f)
                    CourseStatus.FINISHED -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                }
            ),
            width = if (status == CourseStatus.ONGOING) 1.5.dp else 0.8.dp
        )
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min).fillMaxWidth()) {
            // 1. 胶囊形色条 (不再紧贴顶部和底部)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(5.dp)
                    .padding(vertical = 16.dp, horizontal = 0.dp)
                    .background(
                        color = if (status == CourseStatus.FINISHED) Color.Gray.copy(alpha = 0.4f)
                        else courseColor.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                    )
            )

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 第一行：标题与教师卡片
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = course.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = if (status == CourseStatus.FINISHED) Color.Gray else MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        StatusBadge(status, courseColor)
                    }

                    TeacherChip(course.teacher, isGray = status == CourseStatus.FINISHED)
                }

                // 第二行：地点和时间 (分垂直排布，增加易读性)
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
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, color.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (status == CourseStatus.FINISHED) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp).padding(end = 4.dp),
                    tint = color
                )
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
        color = if (isGray) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isGray) Color.Gray else MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
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
            modifier = Modifier.size(16.dp),
            tint = tint.copy(alpha = 0.8f)
        )
        Spacer(Modifier.width(8.dp)) // 增加图标与文字的间距
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = if (tint == Color.Gray) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun rememberCourseColor(name: String): Color {
    // 使用更柔和的 M3 色系
    val colors = listOf(
        Color(0xFF6750A4),
        Color(0xFF006A6A),
        Color(0xFF924B4B),
        Color(0xFF4B5F92),
        Color(0xFF7A5901)
    )
    return colors[abs(name.hashCode()) % colors.size]
}