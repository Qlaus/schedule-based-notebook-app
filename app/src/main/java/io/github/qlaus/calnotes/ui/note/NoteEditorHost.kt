package io.github.qlaus.calnotes.ui.note

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import io.github.qlaus.calnotes.core.ink.InkDoc
import io.github.qlaus.calnotes.core.ink.InkGeometry
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The note page on Android: the shared web editor (WYSIWYG rich text, same code as the web app,
 * bundled in assets/editor) in a WebView, with the native handwriting layer on top.
 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class NoteEditorHost(context: Context) : FrameLayout(context) {

    interface Listener {
        fun onReady()
        fun onContent(title: String, markdown: String)
        fun onInk(ink: String?)
    }

    var listener: Listener? = null

    val webView = WebView(context)
    val inkView = InkCanvasView(context)

    private val main = Handler(Looper.getMainLooper())
    private var doc = InkDoc()
    private val undoStack = ArrayDeque<InkDoc>()
    private var visualRequest = 0L
    var ready = false
        private set

    init {
        addView(webView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(inkView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true // honour <meta viewport width=640>: the page fills the width
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            textZoom = 100
            allowFileAccess = false
            allowContentAccess = false
        }
        webView.isVerticalScrollBarEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            // The editor never navigates; block everything except our own assets.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                request.url.host != ASSET_HOST
        }
        webView.addJavascriptInterface(Bridge(), "CalNotesNative")
        webView.loadUrl(EDITOR_URL)

        inkView.scale = { if (webView.width > 0) webView.width / InkDoc.PAGE_WIDTH.toFloat() else 1f }
        inkView.scrollPx = { webView.scrollY }
        inkView.onStroke = { id, stroke ->
            pushUndo()
            doc = doc.copy(strokes = doc.strokes + stroke)
            sendInk()
            // Remove the wet stroke only once the page has drawn the dry one (no flicker).
            webView.postVisualStateCallback(++visualRequest, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) = inkView.removeWet(id)
            })
        }
        inkView.onErase = { x, y, r ->
            val next = InkGeometry.eraseAt(doc, x, y, r)
            if (next !== doc) {
                pushUndo()
                doc = next
                sendInk()
            }
        }
    }

    fun load(noteKey: String, title: String, markdown: String, ink: String?, titlePlaceholder: String, bodyPlaceholder: String) {
        doc = InkDoc.parse(ink) ?: InkDoc()
        undoStack.clear()
        val args = buildJsonObject {
            put("noteKey", noteKey)
            put("title", title)
            put("markdown", markdown)
            put("ink", if (doc.isEmpty) JsonNull else JsonPrimitive(doc.encode()))
            put("editable", !inkView.penMode)
            put("titlePlaceholder", titlePlaceholder)
            put("bodyPlaceholder", bodyPlaceholder)
        }
        js("window.calnotes && window.calnotes.load($args)")
    }

    fun setPenMode(enabled: Boolean) {
        inkView.penMode = enabled
        js("window.calnotes && (window.calnotes.setEditable(${!enabled}), window.calnotes.setExtraSpace(${if (enabled) 900 else 0}))")
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()

    fun undoInk() {
        val prev = undoStack.removeLastOrNull() ?: return
        doc = prev
        sendInk()
    }

    fun destroy() {
        webView.removeJavascriptInterface("CalNotesNative")
        webView.destroy()
    }

    private fun pushUndo() {
        undoStack.addLast(doc)
        if (undoStack.size > 100) undoStack.removeFirst()
    }

    private fun sendInk() {
        val json = if (doc.isEmpty) "null" else JsonPrimitive(doc.encode()).toString()
        js("window.calnotes && window.calnotes.setInk($json)")
        listener?.onInk(if (doc.isEmpty) null else doc.encode())
    }

    private fun js(code: String) = webView.evaluateJavascript(code, null)

    /** Called by the page (on a background thread). */
    private inner class Bridge {
        @JavascriptInterface
        fun onReady() {
            main.post {
                ready = true
                listener?.onReady()
            }
        }

        @JavascriptInterface
        fun onChange(title: String, markdown: String) {
            main.post { listener?.onContent(title, markdown) }
        }

        @JavascriptInterface
        fun onHeight(@Suppress("UNUSED_PARAMETER") pageUnits: Double) = Unit
    }

    companion object {
        const val ASSET_HOST = "appassets.androidplatform.net"
        val EDITOR_URL: String = Uri.Builder().scheme("https").authority(ASSET_HOST).path("/assets/editor/editor.html").build().toString()
    }
}
