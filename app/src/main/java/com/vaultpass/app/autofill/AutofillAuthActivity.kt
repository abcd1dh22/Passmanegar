@file:OptIn(ExperimentalMaterial3Api::class)

package com.vaultpass.app.autofill

import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import com.vaultpass.app.R
import com.vaultpass.app.data.Auth
import com.vaultpass.app.data.Entry
import com.vaultpass.app.data.VaultStore
import com.vaultpass.app.data.siteMatches
import com.vaultpass.app.ui.EntryCard
import com.vaultpass.app.ui.UnlockScreen
import com.vaultpass.app.ui.VaultTheme

class AutofillAuthActivity : FragmentActivity() {
    private var userId: AutofillId? = null
    private var passId: AutofillId? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        userId = IntentCompat.getParcelableExtra(intent, EX_USER, AutofillId::class.java)
        passId = IntentCompat.getParcelableExtra(intent, EX_PASS, AutofillId::class.java)
        val site = intent.getStringExtra(EX_SITE).orEmpty()
        val auth = Auth(this)
        setResult(RESULT_CANCELED)

        setContent {
            VaultTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var unlocked by remember { mutableStateOf(false) }
                    if (!unlocked) {
                        UnlockScreen(this@AutofillAuthActivity, auth) { unlocked = true }
                    } else {
                        val all = remember { runCatching { VaultStore.get(this@AutofillAuthActivity).load() }.getOrDefault(emptyList()) }
                        val matches = remember { all.filter { siteMatches(it.site, site) } }
                        val ordered = remember { matches + all.filter { it !in matches } }
                        LaunchedEffect(Unit) { if (matches.size == 1) deliver(matches[0]) }
                        Picker(ordered, matches.isNotEmpty()) { deliver(it) }
                    }
                }
            }
        }
    }

    private fun deliver(e: Entry) {
        val pres = RemoteViews(packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.label, e.title)
        }
        val b = Dataset.Builder(pres)
        userId?.let { b.setValue(it, AutofillValue.forText(e.username)) }
        passId?.let { b.setValue(it, AutofillValue.forText(e.password)) }
        setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, b.build()))
        finish()
    }

    companion object {
        const val EX_USER = "vp_user_id"
        const val EX_PASS = "vp_pass_id"
        const val EX_SITE = "vp_site"
    }
}

@Composable
private fun Picker(entries: List<Entry>, hasMatch: Boolean, onPick: (Entry) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text("VaultPass", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            if (entries.isEmpty()) "ভল্টে কোনো একাউন্ট নেই। আগে অ্যাপ খুলে যোগ করুন."
            else if (hasMatch) "কোন একাউন্ট দিয়ে পূরণ করবেন?" else "মিলে যাওয়া একাউন্ট নেই, তালিকা থেকে বেছে নিন",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries, key = { it.id }) { e -> EntryCard(e, false) { onPick(e) } }
        }
    }
}
