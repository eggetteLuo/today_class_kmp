package com.eggetteluo.todayclasskmp.feature.role.model

enum class UserRole(val code: String, val label: String) {
    Student("student", "学生"),
    Teacher("teacher", "老师"),
    Counselor("counselor", "辅导员");

    companion object {
        fun fromCode(code: String?): UserRole? {
            if (code.isNullOrBlank()) return null
            return entries.firstOrNull { it.code == code }
        }

        fun labelFromCode(code: String): String {
            return fromCode(code)?.label ?: "未知身份"
        }
    }
}
