package it.melodia.playback

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import it.melodia.MelodiaApp
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

/**
 * Android Auto only loads browse-item artwork from content:// URIs, so this provider downloads
 * the cover images and serves them from a disk cache.
 */
class ArtworkProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val url = uri.getQueryParameter("u") ?: throw FileNotFoundException(uri.toString())
        val ctx = context ?: throw FileNotFoundException("no context")
        val dir = File(ctx.cacheDir, "artwork").apply { mkdirs() }
        val file = File(dir, sha1(url))
        if (!file.exists()) {
            val http = (ctx.applicationContext as MelodiaApp).http
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw FileNotFoundException("HTTP ${resp.code}")
                val tmp = File(dir, file.name + ".tmp")
                resp.body!!.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(file)
            }
            trim(dir)
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun trim(dir: File) {
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        if (files.size > MAX_FILES) files.take(files.size - MAX_FILES).forEach { it.delete() }
    }

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    override fun getType(uri: Uri): String = "image/jpeg"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "it.melodia.app.artwork"
        private const val MAX_FILES = 500

        fun uriFor(url: String?): Uri? =
            url?.let { Uri.parse("content://$AUTHORITY/img").buildUpon().appendQueryParameter("u", it).build() }
    }
}
