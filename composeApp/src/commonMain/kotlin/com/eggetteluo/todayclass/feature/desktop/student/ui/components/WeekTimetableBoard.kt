package com.eggetteluo.todayclass.feature.desktop.student.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eggetteluo.todayclass.core.database.entity.CourseScheduleEntity
import com.eggetteluo.todayclass.core.time.getFormattedTimeRange

private val DAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private val PERIOD_SLOTS = listOf(1 to 2, 3 to 4, 5 to 6, 7 to 8, 9 to 10)

@Composable
fun WeekTimetableBoard(
    courses: List<CourseScheduleEntity>,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val periodColWidth = 60.dp
    val colWidth = 120.dp
    val rowHeight = 108.dp

    Column(modifier = modifier.horizontalScroll(scrollState)) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .width(periodColWidth)
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("节次", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
            DAY_LABELS.forEach { label ->
                Surface(
                    modifier = Modifier
                        .width(colWidth)
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        PERIOD_SLOTS.forEach { (startPeriod, endPeriod) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .width(periodColWidth)
                        .height(rowHeight)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "$startPeriod-$endPeriod",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }

                (1..7).forEach { day ->
                    val course = courses.firstOrNull {
                        it.dayOfWeek == day &&
                            it.startPeriod == startPeriod &&
                            (it.startPeriod + it.periodCount - 1) == endPeriod
                    }
                    Box(
                        modifier = Modifier
                            .width(colWidth)
                            .height(rowHeight)
                            .padding(4.dp),
                    ) {
                        if (course != null) {
                            val courseColor = rememberCourseColor(course.courseName)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = courseColor.copy(alpha = 0.18f),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = course.courseName,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 2,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = course.location.ifBlank { "未标注教室" },
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                    )
                                    Text(
                                        text = course.teacher.ifBlank { "未标注教师" },
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                    )
                                    Text(
                                        text = getFormattedTimeRange(
                                            startPeriod = course.startPeriod,
                                            periodCount = course.periodCount,
                                            location = course.location,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}
