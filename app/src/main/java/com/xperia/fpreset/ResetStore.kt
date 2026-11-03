package com.xperia.fpreset

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.DateFormat
import java.util.Date

data class ResetState(val busy: Boolean = false, val title: String = "Ready", val log: String = "", val root: String = "Not checked", val hal: String = "Unknown", val pid: String = "—", val completedReset: Long = 0)
object ResetStore {
    private val mutable = MutableStateFlow(ResetState())
    val state = mutable.asStateFlow()
    private var loaded = false
    @Synchronized fun load(context: Context) {
        if (loaded) return
        val prefs = context.getSharedPreferences("result", Context.MODE_PRIVATE)
        val title = prefs.getString("title", "Ready") ?: "Ready"
        mutable.value = ResetState(false, if (title == "Working…") "Previous operation interrupted; inspect log" else title,
            prefs.getString("log", "") ?: "")
        loaded = true
    }
    @Synchronized fun begin(context: Context, title: String) {
        mutable.value = mutable.value.copy(busy = true, title = "Working…", log = "${DateFormat.getDateTimeInstance().format(Date())}\n$title\n")
        persist(context)
    }
    @Synchronized fun append(context: Context, text: String) {
        mutable.value = mutable.value.copy(log = (mutable.value.log + "\n" + text).takeLast(100000))
        persist(context)
    }
    @Synchronized fun finish(context: Context, title: String) {
        mutable.value = mutable.value.copy(busy = false, title = title)
        persist(context)
    }
    @Synchronized fun status(root: String, hal: String, pid: String) {
        mutable.value = mutable.value.copy(root = root, hal = hal, pid = pid)
    }
    @Synchronized fun resetCompleted() { mutable.value = mutable.value.copy(completedReset = System.nanoTime()) }
    private fun persist(context: Context) {
        context.getSharedPreferences("result", Context.MODE_PRIVATE).edit()
            .putString("title", mutable.value.title).putString("log", mutable.value.log).apply()
    }
}
