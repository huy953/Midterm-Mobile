package com.example.offlinefirsttodo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.offlinefirsttodo.ui.TaskScreen
import com.example.offlinefirsttodo.ui.TaskViewModel
import com.example.offlinefirsttodo.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val vm: TaskViewModel = viewModel(factory = TaskViewModel.Factory)
                TaskScreen(vm)
            }
        }
    }
}
