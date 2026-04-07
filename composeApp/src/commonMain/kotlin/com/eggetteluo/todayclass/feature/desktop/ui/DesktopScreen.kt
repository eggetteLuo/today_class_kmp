package com.eggetteluo.todayclass.feature.desktop.ui

import androidx.compose.runtime.Composable
import com.eggetteluo.todayclass.feature.desktop.counselor.ui.CounselorDesktopScreen
import com.eggetteluo.todayclass.feature.desktop.student.ui.StudentDesktopScreen
import com.eggetteluo.todayclass.feature.desktop.teacher.ui.TeacherDesktopScreen
import com.eggetteluo.todayclass.feature.role.model.UserRole

@Composable
fun DesktopScreen(role: UserRole) {
    when (role) {
        UserRole.Student -> StudentDesktopScreen()
        UserRole.Teacher -> TeacherDesktopScreen()
        UserRole.Counselor -> CounselorDesktopScreen()
    }
}
