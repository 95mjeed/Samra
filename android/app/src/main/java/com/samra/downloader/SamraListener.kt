package com.samra.downloader

/**
 * Callback the Python bridge (samra.py) invokes while a download runs.
 * Implemented on the Kotlin side and passed into Chaquopy.
 */
interface SamraListener {
    fun onLog(line: String)
    fun onProgress(fraction: Double)
    fun onBook(title: String, index: Int, total: Int)
}
