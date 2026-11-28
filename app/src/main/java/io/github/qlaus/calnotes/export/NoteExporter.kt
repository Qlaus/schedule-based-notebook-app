package io.github.qlaus.calnotes.export

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.qlaus.calnotes.core.export.ExportMarkdown
import io.github.qlaus.calnotes.core.export.ExportNote
import io.github.qlaus.calnotes.data.local.AppDatabase
import io.github.qlaus.calnotes.ui.common.Formats
import io.github.qlaus.calnotes.ui.note.NoteEditorHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Export of notes / notebooks as Markdown (share or save) and PDF (system print dialog → Save as PDF). */
@Singleton
class NoteExporter @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val db: AppDatabase,
) {
    /** Notes in the given order with their event/date line. */
    suspend fun collect(noteIds: List<String>): List<ExportNote> = withContext(Dispatchers.IO) {
        noteIds.mapNotNull { id ->
            val n = db.noteDao().get(id)?.takeIf { it.deletedAt == null } ?: return@mapNotNull null
            val link = db.eventLinkDao().allForNote(id).firstOrNull { it.deletedAt == null }
            val meta = if (link != null) "${link.eventTitleSnapshot} · ${Formats.dayShort(Formats.date(link.eventStartSnapshot))}"
            else Formats.dayShort(Formats.date(n.createdAt))
            ExportNote(n.title, n.body, n.ink, meta)
        }
    }

    fun markdown(notes: List<ExportNote>): String = ExportMarkdown.build(notes)

    /** Opens the share sheet with a .md file. */
    suspend fun shareMarkdown(activity: Activity, name: String, notes: List<ExportNote>) {
        val file = withContext(Dispatchers.IO) {
            File(appContext.cacheDir, "export").apply { mkdirs() }
                .resolve("${ExportMarkdown.safeFileName(name)}.md")
                .apply { writeText(markdown(notes)) }
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/markdown")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(Intent.createChooser(send, name))
    }

    /** Writes the Markdown to a document the user picked (ACTION_CREATE_DOCUMENT). */
    suspend fun saveMarkdown(uri: Uri, notes: List<ExportNote>) = withContext(Dispatchers.IO) {
        appContext.contentResolver.openOutputStream(uri, "wt")?.use { it.write(markdown(notes).toByteArray(Charsets.UTF_8)) }
    }

    private var printWebView: WebView? = null

    /**
     * Renders the notes with the same page layout as the editor (handwriting in place) in an
     * off-screen WebView and opens the system print dialog, where "Save as PDF" is available.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun printPdf(activity: Activity, name: String, notes: List<ExportNote>) {
        val main = Handler(Looper.getMainLooper())
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(activity))
            .build()
        val web = WebView(activity)
        printWebView = web // keep alive until printing is done
        web.settings.javaScriptEnabled = true
        web.settings.textZoom = 100
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)
        }
        val payload = JsonArray(
            notes.map { n ->
                buildJsonObject {
                    put("title", JsonPrimitive(n.title))
                    put("markdown", JsonPrimitive(n.markdown))
                    put("ink", n.ink?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("meta", JsonPrimitive(n.meta))
                }
            },
        )
        web.addJavascriptInterface(object {
            @JavascriptInterface
            fun onReady() {
                main.post {
                    web.evaluateJavascript("window.calnotes.renderPrint($payload)", null)
                    // Give layout and fonts a moment, then print what is on screen.
                    web.postVisualStateCallback(1, object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                            main.postDelayed({
                                val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                                val jobName = ExportMarkdown.safeFileName(name)
                                printManager.print(
                                    jobName,
                                    web.createPrintDocumentAdapter(jobName),
                                    PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build(),
                                )
                            }, 300)
                        }
                    })
                }
            }

            @JavascriptInterface
            fun onChange(title: String, markdown: String) = Unit

            @JavascriptInterface
            fun onHeight(pageUnits: Double) = Unit
        }, "CalNotesNative")
        web.loadUrl(NoteEditorHost.EDITOR_URL)
    }
}
