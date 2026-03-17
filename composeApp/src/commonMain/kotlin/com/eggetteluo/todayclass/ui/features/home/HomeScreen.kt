package com.eggetteluo.todayclass.ui.features.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eggetteluo.todayclass.ui.components.CourseItem
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.launch
import com.eggetteluo.todayclass.util.ExcelParser
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()
    // 文件解析
    val excelParser = ExcelParser()

    // 当天课程列表
    val courses by viewModel.todayCourses.collectAsStateWithLifecycle()

    val launcher = rememberFilePickerLauncher(
        type = PickerType.File(
            extensions = listOf("xlsx", "xls") // 限制只能选 Excel 文件
        ),
        title = "选择课表 Excel"
    ) { file ->
        // 当用户选中文件后的回调
        if (file != null) {
            scope.launch {
                try {
                    val bytes = file.readBytes()
                    Napier.d(tag = "READ_EXCEL_FILE") { "读取文件成功: ${bytes.size} bytes" }

                    val courseList = excelParser.parse(bytes)// 调用文件解析

                    viewModel.importCourses(courseList) // 存储课程数据
                    Napier.d(tag = "READ_EXCEL_FILE") { "解析成功: $courseList" }
                } catch (e: Exception) {
                    Napier.e(tag = "READ_EXCEL_FILE") { "读取文件失败: ${e.message}" }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("今日课表") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    launcher.launch()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (courses.isEmpty()) {
                Text("今天没课，去图书馆卷一下吧！", modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn {
                    items(courses) { course ->
                        CourseItem(course) // 接下来我们可以写这个 UI 组件
                    }
                }
            }
        }
    }

}