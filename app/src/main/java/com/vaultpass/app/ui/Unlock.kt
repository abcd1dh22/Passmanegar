@file:OptIn(ExperimentalMaterial3Api::class)

package com.vaultpass.app.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.vaultpass.app.data.Auth

fun FragmentActivity.canBiometric(): Boolean =
    BiometricManager.from(this)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

fun FragmentActivity.showBiometric(onSuccess: () -> Unit) {
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("VaultPass আনলক করুন")
        .setSubtitle("ফিঙ্গারপ্রিন্ট বা ফেস ব্যবহার করুন")
        .setNegativeButtonText("মাস্টার পাসওয়ার্ড")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()
    BiometricPrompt(
        this, ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        }
    ).authenticate(info)
}

@Composable
fun UnlockScreen(activity: FragmentActivity, auth: Auth, onUnlocked: () -> Unit) {
    val setup = remember { !auth.isSet() }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val bio = remember { !setup && auth.biometric && activity.canBiometric() }

    LaunchedEffect(Unit) { if (bio) activity.showBiometric(onUnlocked) }

    fun submit() {
        if (setup) {
            when {
                pw.length < 8 -> error = "কমপক্ষে ৮ অক্ষর দিন"
                pw != pw2 -> error = "পাসওয়ার্ড দুটি মিলছে না"
                else -> { auth.setPassword(pw); onUnlocked() }
            }
        } else {
            if (auth.verify(pw)) onUnlocked() else error = "ভুল মাস্টার পাসওয়ার্ড"
        }
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("VaultPass", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (setup) "শুরু করতে একটি শক্তিশালী মাস্টার পাসওয়ার্ড তৈরি করুন। এটি ভুলে গেলে ফিরে পাওয়া যাবে না।"
            else "আপনার ভল্ট লক করা আছে",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = pw, onValueChange = { pw = it; error = null },
            label = { Text("মাস্টার পাসওয়ার্ড") }, singleLine = true,
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { show = !show }) {
                    Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility, "দেখান")
                }
            }
        )
        if (setup) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = pw2, onValueChange = { pw2 = it; error = null },
                label = { Text("আবার লিখুন") }, singleLine = true,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { submit() }, modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text(if (setup) "ভল্ট তৈরি করুন" else "আনলক", fontSize = 16.sp) }

        if (bio) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { activity.showBiometric(onUnlocked) },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Fingerprint, null)
                Spacer(Modifier.width(8.dp))
                Text("বায়োমেট্রিক দিয়ে আনলক")
            }
        }
    }
}
