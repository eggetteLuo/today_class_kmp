package com.eggetteluo.todayclasskmp.feature.desktop.ui

import androidx.compose.runtime.Composable
import com.eggetteluo.todayclasskmp.feature.desktop.counselor.ui.CounselorDesktopScreen
import com.eggetteluo.todayclasskmp.feature.desktop.student.ui.StudentDesktopScreen
import com.eggetteluo.todayclasskmp.feature.desktop.teacher.ui.TeacherDesktopScreen
import com.eggetteluo.todayclasskmp.feature.role.model.UserRole

@Composable
fun DesktopScreen(role: UserRole) {
    when (role) {
        UserRole.Student -> StudentDesktopScreen()
        UserRole.Teacher -> TeacherDesktopScreen()
        UserRole.Counselor -> CounselorDesktopScreen()
    }
}
