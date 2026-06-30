package com.samra.downloader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide, observable download state. Written by [DownloadService] (which
 * keeps running when the UI is gone) and read by the Compose UI when it's open.
 */
object DownloadController {
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _currentBook = MutableStateFlow("")
    val currentBook: StateFlow<String> = _currentBook.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    data class QItem(val url: String, val title: String, val status: String, val pct: Float)

    private val _queue = MutableStateFlow<List<QItem>>(emptyList())
    val queue: StateFlow<List<QItem>> = _queue.asStateFlow()

    private val _event = MutableStateFlow<String?>(null)
    val event: StateFlow<String?> = _event.asStateFlow()
    fun emitEvent(msg: String) { _event.value = msg }
    fun clearEvent() { _event.value = null }

    fun setQueue(items: List<QItem>) { _queue.value = items }

    fun updateItem(i: Int, transform: (QItem) -> QItem) {
        val list = _queue.value.toMutableList()
        if (i in list.indices) { list[i] = transform(list[i]); _queue.value = list }
    }

    fun addLog(line: String) {
        if (line.isBlank()) return
        _log.update { old ->
            val next = old + line
            if (next.size > 600) next.subList(next.size - 600, next.size).toList() else next
        }
    }

    fun setProgress(value: Float) { _progress.value = value.coerceIn(0f, 1f) }
    fun setCurrentBook(value: String) { _currentBook.value = value }
    fun setRunning(value: Boolean) { _running.value = value }

    fun reset() {
        _log.value = emptyList()
        _progress.value = 0f
        _currentBook.value = ""
    }
}
