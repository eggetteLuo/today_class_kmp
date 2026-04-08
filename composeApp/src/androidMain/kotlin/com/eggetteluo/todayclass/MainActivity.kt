package com.eggetteluo.todayclass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.eggetteluo.todayclass.core.preferences.RolePreferences
import com.eggetteluo.todayclass.core.preferences.StudentSettingsPreferences
import com.eggetteluo.todayclass.core.preferences.TermPreferences

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        RolePreferences.init(this)
        TermPreferences.init(this)
        StudentSettingsPreferences.init(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
