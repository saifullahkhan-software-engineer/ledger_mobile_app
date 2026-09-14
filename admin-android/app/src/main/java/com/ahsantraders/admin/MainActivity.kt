package com.ahsantraders.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahsantraders.admin.data.AdminRepository
import com.ahsantraders.admin.data.SessionStore
import com.ahsantraders.admin.ui.AdminApp
import com.ahsantraders.admin.ui.AdminViewModel
import com.ahsantraders.admin.ui.AhsanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AdminViewModel(AdminRepository(SessionStore(applicationContext), BuildConfig.DEBUG)) as T
        }
        setContent {
            val vm: AdminViewModel = viewModel(factory = factory)
            val state by vm.state.collectAsStateWithLifecycle()
            AhsanTheme(state.language) { AdminApp(state, vm) }
        }
    }
}
