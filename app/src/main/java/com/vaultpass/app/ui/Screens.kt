@file:OptIn(ExperimentalMaterial3Api::class)

package com.vaultpass.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import android.provider.Settings
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vaultpass.app.VaultViewModel
import com.vaultpass.app.data.Auth
import com.vaultpass.app.data.Entry
import com.vaultpass.app.data.Gen

fun copySecret(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    val clip = ClipData.newPlainText(label, text)
    clip.description.extras = PersistableBundle().apply {
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    cm.setPrimaryClip(clip)
    Toast.makeText(ctx, "$label কপি হয়েছে", Toast.LENGTH_SHORT).show()
}

sealed interface Route {
    data object Home : Route
    data class Edit(val id: String?) : Route
    data object Settings : Route
}

@Composable
fun AppRoot(activity: FragmentActivity, auth: Auth, onLock: () -> Unit) {
    val vm: VaultViewModel = viewModel()
    var route by remember { mutableStateOf<Route>(Route.Home) }
    LaunchedEffect(Unit) { vm.load() }
    BackHandler(enabled = route != Route.Home) { route = Route.Home }

    when (val r = route) {
        Route.Home -> HomeScreen(
            entries = vm.entries,
            onAdd = { route = Route.Edit(null) },
            onOpen = { route = Route.Edit(it.id) },
            onSettings = { route = Route.Settings },
            onLock = onLock
        )
        is Route.Edit -> EditScreen(
            entry = r.id?.let { id -> vm.entries.firstOrNull { it.id == id } },
            onSave = { vm.upsert(it); route = Route.Home },
            onDelete = { vm.delete(it); route = Route.Home },
            onBack = { route = Route.Home }
        )
        Route.Settings -> SettingsScreen(
            activity = activity, auth = auth, count = vm.entries.size,
            onBack = { route = Route.Home }, onLock = onLock
        )
    }
}

@Composable
fun EntryCard(e: Entry, showCopy: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Card(
        onClick = onClick, shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    e.title.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    e.username.ifBlank { e.site }, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showCopy) {
                IconButton(onClick = { copySecret(ctx, "পাসওয়ার্ড", e.password) }) {
                    Icon(Icons.Default.ContentCopy, "কপি", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    entries: List<Entry>, onAdd: () -> Unit, onOpen: (Entry) -> Unit,
    onSettings: () -> Unit, onLock: () -> Unit
) {
    var q by remember { mutableStateOf("") }
    val filtered = remember(entries, q) {
        if (q.isBlank()) entries
        else entries.filter { it.title.contains(q, true) || it.username.contains(q, true) || it.site.contains(q, true) }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("VaultPass", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = onLock) { Icon(Icons.Default.Lock, "লক") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "সেটিংস") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd, icon = { Icon(Icons.Default.Add, null) }, text = { Text("নতুন") },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                placeholder = { Text("একাউন্ট খুঁজুন...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "${entries.size}টি সংরক্ষিত একাউন্ট", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Shield, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (entries.isEmpty()) "এখনো কোনো পাসওয়ার্ড নেই\n+ নতুন চেপে প্রথম একাউন্ট যোগ করুন" else "কিছু পাওয়া যায়নি",
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(filtered, key = { it.id }) { e -> EntryCard(e, true) { onOpen(e) } }
                }
            }
        }
    }
}

@Composable
private fun Field(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    singleLine: Boolean = true, trailing: @Composable (() -> Unit)? = null,
    transformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine,
        shape = RoundedCornerShape(14.dp), modifier = modifier.fillMaxWidth(),
        trailingIcon = trailing, visualTransformation = transformation
    )
}

@Composable
fun EditScreen(entry: Entry?, onSave: (Entry) -> Unit, onDelete: (String) -> Unit, onBack: () -> Unit) {
    var title by remember { mutableStateOf(entry?.title ?: "") }
    var site by remember { mutableStateOf(entry?.site ?: "") }
    var user by remember { mutableStateOf(entry?.username ?: "") }
    var pass by remember { mutableStateOf(entry?.password ?: "") }
    var notes by remember { mutableStateOf(entry?.notes ?: "") }
    var show by remember { mutableStateOf(false) }
    var len by remember { mutableFloatStateOf(16f) }
    var confirm by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val strength = remember(pass) { Gen.strength(pass) }
    val colors = listOf(Color(0xFF94A3B8), Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF84CC16), Color(0xFF22C55E))
    val labels = listOf("", "দুর্বল", "মোটামুটি", "ভালো", "খুব শক্তিশালী")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (entry == null) "নতুন একাউন্ট" else "একাউন্ট সম্পাদনা") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "পেছনে") } },
                actions = {
                    if (entry != null) IconButton(onClick = { confirm = true }) {
                        Icon(Icons.Default.Delete, "মুছুন", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Field(title, { title = it }, "নাম (যেমন Facebook)")
            Field(site, { site = it }, "ওয়েবসাইট বা অ্যাপ প্যাকেজ (অটোফিলের জন্য)")
            Field(user, { user = it }, "ইউজারনেম / ইমেইল", trailing = {
                if (user.isNotEmpty()) IconButton(onClick = { copySecret(ctx, "ইউজারনেম", user) }) { Icon(Icons.Default.ContentCopy, "কপি") }
            })
            Field(
                pass, { pass = it }, "পাসওয়ার্ড",
                transformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    Row {
                        IconButton(onClick = { show = !show }) {
                            Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility, "দেখান")
                        }
                        if (pass.isNotEmpty()) IconButton(onClick = { copySecret(ctx, "পাসওয়ার্ড", pass) }) {
                            Icon(Icons.Default.ContentCopy, "কপি")
                        }
                    }
                }
            )
            if (pass.isNotEmpty()) {
                Column {
                    LinearProgressIndicator(
                        progress = { strength / 4f }, color = colors[strength],
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(labels[strength], fontSize = 12.sp, color = colors[strength])
                }
            }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("পাসওয়ার্ড জেনারেটর  •  দৈর্ঘ্য ${len.toInt()}", fontWeight = FontWeight.SemiBold)
                    Slider(value = len, onValueChange = { len = it }, valueRange = 8f..32f, steps = 23)
                    FilledTonalButton(onClick = { pass = Gen.generate(len.toInt()); show = true }) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("নতুন পাসওয়ার্ড তৈরি করুন")
                    }
                }
            }
            Field(notes, { notes = it }, "নোট (ঐচ্ছিক)", singleLine = false, modifier = Modifier.heightIn(min = 90.dp))
            Button(
                onClick = {
                    val base = entry ?: Entry(title = "", username = "", password = "")
                    onSave(
                        base.copy(
                            title = title.trim(), site = site.trim(), username = user.trim(),
                            password = pass, notes = notes.trim(), updatedAt = System.currentTimeMillis()
                        )
                    )
                },
                enabled = title.isNotBlank() && pass.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)
            ) { Text("সংরক্ষণ করুন", fontSize = 16.sp) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirm && entry != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("মুছে ফেলবেন?") },
            text = { Text("\"${entry.title}\" স্থায়ীভাবে মুছে যাবে।") },
            confirmButton = { TextButton(onClick = { onDelete(entry.id) }) { Text("মুছুন", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("বাতিল") } }
        )
    }
}

@Composable
private fun SettingCard(title: String, desc: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SettingsScreen(activity: FragmentActivity, auth: Auth, count: Int, onBack: () -> Unit, onLock: () -> Unit) {
    var enabled by remember { mutableStateOf(false) }
    var bio by remember { mutableStateOf(auth.biometric) }
    val bioOk = remember { activity.canBiometric() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enabled = activity.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("সেটিংস") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "পেছনে") } }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingCard(
                "অটোফিল সার্ভিস",
                if (enabled) "চালু আছে। অন্য অ্যাপে লগইন ফিল্ডে ট্যাপ করলে VaultPass সাজেশন আসবে।"
                else "চালু নেই। চালু করলে অন্য অ্যাপ ও ব্রাউজারে পাসওয়ার্ড অটো-ফিল হবে।"
            ) {
                if (!enabled) Button(
                    onClick = {
                        try {
                            activity.startActivity(
                                Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:${activity.packageName}"))
                            )
                        } catch (_: Exception) {
                            Toast.makeText(activity, "Settings → Passwords & autofill থেকে চালু করুন", Toast.LENGTH_LONG).show()
                        }
                    },
                    shape = RoundedCornerShape(14.dp)
                ) { Text("অটোফিল চালু করুন") }
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF22C55E))
                    Spacer(Modifier.width(8.dp))
                    Text("সক্রিয়", fontWeight = FontWeight.SemiBold)
                }
            }
            SettingCard("বায়োমেট্রিক আনলক", if (bioOk) "মাস্টার পাসওয়ার্ডের বদলে ফিঙ্গারপ্রিন্ট ব্যবহার করুন।" else "এই ডিভাইসে বায়োমেট্রিক সক্রিয় নেই।") {
                Switch(checked = bio && bioOk, enabled = bioOk, onCheckedChange = { bio = it; auth.biometric = it })
            }
            SettingCard("ভল্ট", "মোট $count টি একাউন্ট এনক্রিপ্টেড অবস্থায় সংরক্ষিত।") {
                OutlinedButton(onClick = onLock, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Lock, null)
                    Spacer(Modifier.width(8.dp))
                    Text("এখনই লক করুন")
                }
            }
            SettingCard("নিরাপত্তা তথ্য", "সব পাসওয়ার্ড AES-256-GCM দিয়ে এনক্রিপ্ট হয়, key থাকে Android Keystore-এ। স্ক্রিনশট বন্ধ, ব্যাকআপ বন্ধ। অ্যাপ ব্যাকগ্রাউন্ডে গেলে অটো-লক হয়।") {}
            Spacer(Modifier.height(24.dp))
        }
    }
}
