package com.example.journal

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader
import java.io.BufferedOutputStream
import java.io.OutputStream

/** Hosts the journal UI (assets/www/index.html) in a locked-down WebView served from a virtual https origin. */
class MainActivity : ComponentActivity() {
    private lateinit var web: WebView
    private var chooser: ValueCallback<Array<Uri>>? = null
    private var geoCb: GeolocationPermissions.Callback? = null
    private var geoOrigin: String? = null
    private var out: OutputStream? = null
    private var outUri: Uri? = null

    // <input type="file"> (photos, backup import)
    private val pickFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        chooser?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        chooser = null
    }

    // Runtime location permission, only after the user taps "Use current location"
    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { g ->
        geoCb?.invoke(geoOrigin, g.values.any { it }, false)
        geoCb = null
    }

    // Backup export: user picks the destination (Storage Access Framework), JS then streams the file in chunks
    private val createDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        var ok = false
        if (uri != null) {
            try {
                out = BufferedOutputStream(contentResolver.openOutputStream(uri, "wt")!!, 1 shl 16)
                outUri = uri
                ok = true
            } catch (e: Exception) {
            }
        }
        js("window.__expReady&&window.__expReady($ok)")
    }

    private fun js(s: String) = runOnUiThread { web.evaluateJavascript(s, null) }

    private fun applyTheme(mode: String) {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dark = when (mode) {
            "dark" -> true
            "light" -> false
            else -> night
        }
        window.decorView.setBackgroundColor(if (dark) 0xFF0B1014.toInt() else 0xFFF6F8FA.toInt())
        WindowCompat.getInsetsController(window, window.decorView).let {
            it.isAppearanceLightStatusBars = !dark
            it.isAppearanceLightNavigationBars = !dark
        }
    }

    /** Exposed to the page as `Native`. */
    inner class Bridge {
        @JavascriptInterface
        fun exportBegin(name: String) {
            runOnUiThread { createDoc.launch(name) }
        }

        @JavascriptInterface
        fun exportWrite(s: String): Boolean = try {
            out!!.write(s.toByteArray(Charsets.UTF_8)); true
        } catch (e: Exception) {
            false
        }

        @JavascriptInterface
        fun exportEnd(ok: Boolean): Boolean {
            var good = ok
            val u = outUri
            try { out?.close() } catch (e: Exception) { good = false }
            out = null
            outUri = null
            if (!good && u != null) {
                try { DocumentsContract.deleteDocument(contentResolver, u) } catch (e: Exception) { }
            }
            return good
        }

        @JavascriptInterface
        fun setTheme(mode: String) {
            runOnUiThread { applyTheme(mode) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true // IndexedDB + localStorage
            allowFileAccess = false
            setGeolocationEnabled(true)
            setSupportZoom(false)
        }
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) WebView.setWebContentsDebuggingEnabled(true)

        web.webViewClient = object : WebViewClient() {
            // Only the bundled app is served; every other request is blocked.
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                request?.let { loader.shouldInterceptRequest(it.url) }
                    ?: WebResourceResponse("text/plain", "utf-8", 404, "Blocked", emptyMap(), "".byteInputStream())

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                chooser?.onReceiveValue(null)
                chooser = filePathCallback
                val p = fileChooserParams ?: return false
                val i = p.createIntent()
                // Backups are plain .json files that some providers label with odd MIME types.
                if (p.acceptTypes.any { it.contains("json") }) {
                    i.type = "*/*"
                    i.removeExtra(Intent.EXTRA_MIME_TYPES)
                }
                return try {
                    pickFile.launch(i)
                    true
                } catch (e: Exception) {
                    chooser = null
                    filePathCallback?.onReceiveValue(null)
                    false
                }
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    .any { ContextCompat.checkSelfPermission(this@MainActivity, it) == PackageManager.PERMISSION_GRANTED }
                if (granted) {
                    callback?.invoke(origin, true, false)
                } else {
                    geoCb = callback
                    geoOrigin = origin
                    askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }
        }
        web.addJavascriptInterface(Bridge(), "Native")

        val root = FrameLayout(this)
        root.addView(web, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val s = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(s.left, s.top, s.right, s.bottom)
            WindowInsetsCompat.CONSUMED
        }
        applyTheme("system")

        // Back closes the topmost screen/dialog inside the page first, then leaves the app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("window.__back&&window.__back()===true") { r ->
                    if (r != "true") {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        web.loadUrl("https://appassets.androidplatform.net/assets/www/index.html")
    }

    override fun onPause() {
        js("window.__flush&&window.__flush()") // keep an unsaved draft safe
        web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
