@file:OptIn(ExperimentalMaterial3Api::class)

package com.vaultpass.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.vaultpass.app.data.Auth
import com.vaultpass.app.ui.AppRoot
import com.vaultpass.app.ui.UnlockScreen
import com.vaultpass.app.ui.VaultTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val auth = Auth(this)
        setContent {
            VaultTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var unlocked by rememberSaveable { mutableStateOf(false) }
                    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { unlocked = false }
                    if (!unlocked) UnlockScreen(this@MainActivity, auth) { unlocked = true }
                    else AppRoot(this@MainActivity, auth) { unlocked = false }
                }
            }
        }
    }
}
