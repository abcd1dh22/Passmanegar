package com.vaultpass.app.autofill

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import android.widget.RemoteViews
import com.vaultpass.app.R
import com.vaultpass.app.data.Entry
import com.vaultpass.app.data.VaultStore
import com.vaultpass.app.data.siteMatches

class Parsed {
    var pkg = ""
    var domain: String? = null
    var userId: AutofillId? = null
    var passId: AutofillId? = null
    var userVal: String? = null
    var passVal: String? = null
    var lastText: AutofillId? = null
    var lastTextVal: String? = null
    val site: String get() = domain ?: pkg
}

object StructureParser {
    private val passVariations = setOf(
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
    )

    fun parse(s: AssistStructure): Parsed {
        val p = Parsed()
        p.pkg = s.activityComponent?.packageName ?: ""
        for (i in 0 until s.windowNodeCount) walk(s.getWindowNodeAt(i).rootViewNode, p)
        return p
    }

    private fun walk(n: AssistStructure.ViewNode, p: Parsed) {
        n.webDomain?.takeIf { it.isNotBlank() }?.let { if (p.domain == null) p.domain = it }
        val id = n.autofillId
        if (id != null && n.visibility == View.VISIBLE) {
            val hints = n.autofillHints?.toList().orEmpty()
            val cls = n.className ?: ""
            val html = n.htmlInfo
            val htmlType = html?.attributes?.firstOrNull { it.first == "type" }?.second?.lowercase()
            val isInput = cls.contains("EditText") || html?.tag == "input" || hints.isNotEmpty()
            if (isInput) {
                val isText = (n.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
                val variation = n.inputType and InputType.TYPE_MASK_VARIATION
                val value = n.autofillValue?.takeIf { it.isText }?.textValue?.toString()
                val isPass = View.AUTOFILL_HINT_PASSWORD in hints || htmlType == "password" ||
                    (isText && variation in passVariations)
                val isUser = hints.any { it == View.AUTOFILL_HINT_USERNAME || it == View.AUTOFILL_HINT_EMAIL_ADDRESS } ||
                    htmlType == "email" ||
                    (isText && variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
                if (isPass) {
                    if (p.passId == null) {
                        p.passId = id
                        p.passVal = value
                        if (p.userId == null) { p.userId = p.lastText; p.userVal = p.lastTextVal }
                    }
                } else {
                    if (isUser && p.userId == null) { p.userId = id; p.userVal = value }
                    p.lastText = id
                    p.lastTextVal = value
                }
            }
        }
        for (i in 0 until n.childCount) walk(n.getChildAt(i), p)
    }
}

class VaultAutofillService : AutofillService() {

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val p = StructureParser.parse(request.fillContexts.last().structure)
        if (p.pkg == packageName || (p.userId == null && p.passId == null)) {
            callback.onSuccess(null)
            return
        }
        val intent = Intent(this, AutofillAuthActivity::class.java).apply {
            p.userId?.let { putExtra(AutofillAuthActivity.EX_USER, it) }
            p.passId?.let { putExtra(AutofillAuthActivity.EX_PASS, it) }
            putExtra(AutofillAuthActivity.EX_SITE, p.site)
        }
        val sender = PendingIntent.getActivity(
            this, 1, intent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
        ).intentSender

        val pres = RemoteViews(packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.label, "VaultPass দিয়ে পূরণ করুন")
        }
        val ds = Dataset.Builder(pres).setAuthentication(sender)
        p.userId?.let { ds.setValue(it, null) }
        p.passId?.let { ds.setValue(it, null) }

        val resp = FillResponse.Builder().addDataset(ds.build())
        p.passId?.let { pid ->
            val save = SaveInfo.Builder(
                SaveInfo.SAVE_DATA_TYPE_PASSWORD or SaveInfo.SAVE_DATA_TYPE_USERNAME, arrayOf(pid)
            )
            p.userId?.let { save.setOptionalIds(arrayOf(it)) }
            resp.setSaveInfo(save.build())
        }
        callback.onSuccess(resp.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        try {
            val p = StructureParser.parse(request.fillContexts.last().structure)
            val pass = p.passVal
            if (!pass.isNullOrEmpty() && p.pkg != packageName) {
                val store = VaultStore.get(this)
                val list = store.load()
                val user = p.userVal.orEmpty()
                val old = list.firstOrNull { siteMatches(it.site, p.site) && it.username == user }
                val updated: List<Entry> = if (old != null) {
                    list.map { if (it.id == old.id) it.copy(password = pass, updatedAt = System.currentTimeMillis()) else it }
                } else {
                    list + Entry(title = p.site.removePrefix("www.").substringBefore('.').ifBlank { p.site }
                        .replaceFirstChar { it.uppercase() }, username = user, password = pass, site = p.site)
                }
                store.save(updated)
            }
            callback.onSuccess()
        } catch (e: Exception) {
            callback.onFailure("সংরক্ষণ করা যায়নি")
        }
    }
}
