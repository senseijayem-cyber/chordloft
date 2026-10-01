package com.rexven.chordloft

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject

/**
 * Chordloft runs its song screens from the bundled web app in assets/index.html.
 * This activity hosts it full screen and connects it to Android: the back button,
 * the file picker, "Share to Chordloft", keeping the screen awake and stage mode.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var pageReady = false
    private val incoming = mutableListOf<Pair<String, String>>()
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val pickFiles =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            fileCallback?.onReceiveValue(uris.toTypedArray())
            fileCallback = null
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        web = findViewById(R.id.web)

        // Keep the app clear of the status bar, navigation bar, camera cutout and keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        val assets = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.setBackgroundColor(getColor(R.color.page_bg))
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true          // the song library is stored here
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            textZoom = 100                    // the app has its own text size buttons
        }
        web.addJavascriptInterface(Bridge(), "Android")

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assets.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == APP_HOST) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                } catch (e: ActivityNotFoundException) {
                }
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
                deliverIncoming()
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    pickFiles.launch(arrayOf("text/*", "application/octet-stream", "application/x-chordpro"))
                    true
                } catch (e: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }
        }

        // Back goes back inside the app (stage mode, song, setlist...) before leaving it.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!pageReady) {
                    finish()
                    return
                }
                web.evaluateJavascript("(window.chordloftBack && window.chordloftBack()) === true") { result ->
                    if (result != "true") {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

        web.loadUrl(START_URL)
        handleIncoming(intent)
        setIntent(Intent(this, MainActivity::class.java))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    override fun onPause() {
        // Stop the metronome and auto-scroll when the app leaves the screen.
        if (pageReady) web.evaluateJavascript("window.chordloftPause && window.chordloftPause()", null)
        web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        web.destroy()
        super.onDestroy()
    }

    /** Collects songs that were shared to, or opened with, Chordloft. */
    private fun handleIncoming(intent: Intent?) {
        if (intent == null) return
        val items = mutableListOf<Pair<String, String>>()
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { readUri(it)?.let(items::add) }
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (uri != null) {
                    readUri(uri)?.let(items::add)
                } else {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!text.isNullOrBlank()) {
                        items.add((intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: "Shared text") to text)
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.forEach { uri -> readUri(uri)?.let(items::add) }
        }
        if (items.isEmpty()) return
        incoming.addAll(items)
        deliverIncoming()
    }

    private fun readUri(uri: Uri): Pair<String, String>? {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Shared file"
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { name = it }
            }
        }
        val text = runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                String(if (bytes.size > MAX_BYTES) bytes.copyOf(MAX_BYTES) else bytes, Charsets.UTF_8)
            }
        }.getOrNull() ?: return null
        return name to text
    }

    private fun deliverIncoming() {
        if (!pageReady || incoming.isEmpty()) return
        val list = JSONArray()
        incoming.forEach { (name, text) -> list.put(JSONObject().put("name", name).put("text", text)) }
        incoming.clear()
        web.evaluateJavascript("window.chordloftReceive && window.chordloftReceive($list)", null)
    }

    /** Called from the web app as window.Android.* */
    inner class Bridge {
        @JavascriptInterface
        fun setKeepAwake(on: Boolean) = runOnUiThread {
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        @JavascriptInterface
        fun setImmersive(on: Boolean) = runOnUiThread {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (on) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    companion object {
        private const val APP_HOST = "appassets.androidplatform.net"
        private const val START_URL = "https://$APP_HOST/assets/index.html"
        private const val MAX_BYTES = 1_000_000
    }
}
