package com.samra.downloader

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File

/** Where books are saved. Prefers the public Download/Samra folder (visible in
 *  any file manager); falls back to the app-private dir until All-files access
 *  is granted. */
object Storage {

    fun canWritePublic(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

    fun publicDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Samra")

    fun appDir(ctx: Context): File = File(ctx.getExternalFilesDir(null), "Samra")

    /** The directory new downloads should be written to. */
    fun outputDir(ctx: Context): File {
        val d = if (canWritePublic()) publicDir() else appDir(ctx)
        d.mkdirs()
        return d
    }

    /** All directories the library should scan (public + app-private), so books
     *  appear no matter where they were saved. */
    fun scanDirs(ctx: Context): List<File> =
        listOf(publicDir(), appDir(ctx)).filter { it.exists() }
}
