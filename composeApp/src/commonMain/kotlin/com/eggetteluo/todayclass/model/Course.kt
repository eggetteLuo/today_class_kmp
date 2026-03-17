package com.eggetteluo.todayclass.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,         // 课程名
    val teacher: String,      // 老师
    val location: String,     // 地点
    val dayOfWeek: Int,       // 星期 (1-7)
    val section: String,      // 节次描述 (如 "第一节")
    val weekList: List<Int>,  // 用于首页过滤的周次集合
    val originalWeeks: String // 用于 UI 显示的原始文本
)
