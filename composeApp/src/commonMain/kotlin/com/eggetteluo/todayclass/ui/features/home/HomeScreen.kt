package com.eggetteluo.todayclass.ui.features.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.launch
import com.eggetteluo.todayclass.util.ExcelParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val scope = rememberCoroutineScope()
    // 文件解析
    val excelParser = ExcelParser()

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
            Text("你好")
        }
    }

}