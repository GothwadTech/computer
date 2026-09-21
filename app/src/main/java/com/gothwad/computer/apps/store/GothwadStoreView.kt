package com.gothwad.computer.apps.store

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.gothwad.computer.apps.webapp.WebAppManager
import com.gothwad.computer.data.ConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Windows 11 Microsoft Store Web Engine for Gothwad PC.
 * Hosts an authentic Windows Store experience built with HTML/CSS/JavaScript,
 * communicating with Android native bridge to install, launch, and uninstall web apps directly.
 */
@SuppressLint("SetJavaScriptEnabled")
class GothwadStoreView(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val onAppListChanged: (() -> Unit)? = null,
    private val onOpenWebApp: ((url: String, title: String) -> Unit)? = null
) {
    val webView: WebView = WebView(context).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
        }

        setBackgroundColor(0xFF1A1B1E.toInt())

        addJavascriptInterface(StoreJsBridge(), "AndroidStoreBridge")

        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {}

        loadUrl("file:///android_asset/store/index.html")
    }

    fun getView(): View = webView

    fun destroy() {
        webView.stopLoading()
        webView.removeJavascriptInterface("AndroidStoreBridge")
        webView.destroy()
    }

    inner class StoreJsBridge {

        @JavascriptInterface
        fun getInstalledAppsJson(): String {
            val pwaUrls = runCatching {
                runBlocking {
                    val config = ConfigStore(context).flow.first()
                    (config.pcCustomLabels.keys + config.pcDesktopOrder)
                        .filter { it.startsWith("pwa://") }
                        .map { it.removePrefix("pwa://") }
                        .distinct()
                }
            }.getOrDefault(emptyList())

            val array = JSONArray()
            pwaUrls.forEach { array.put(it) }
            return array.toString()
        }

        @JavascriptInterface
        fun installApp(name: String, url: String, iconUrl: String, category: String) {
            coroutineScope.launch {
                WebAppManager.installWebApp(
                    context = context,
                    url = url,
                    title = name,
                    icon = null,
                    iconUrl = iconUrl
                )
                withContext(Dispatchers.Main) {
                    onAppListChanged?.invoke()
                    val escapedUrl = url.replace("'", "\\'")
                    webView.evaluateJavascript(
                        "window.onAppInstalled && window.onAppInstalled('$escapedUrl');",
                        null
                    )
                }
            }
        }

        @JavascriptInterface
        fun uninstallApp(url: String) {
            coroutineScope.launch {
                WebAppManager.uninstallWebApp(context, url)
                withContext(Dispatchers.Main) {
                    onAppListChanged?.invoke()
                    val escapedUrl = url.replace("'", "\\'")
                    webView.evaluateJavascript(
                        "window.onAppUninstalled && window.onAppUninstalled('$escapedUrl');",
                        null
                    )
                }
            }
        }

        @JavascriptInterface
        fun openApp(url: String, name: String) {
            coroutineScope.launch(Dispatchers.Main) {
                onOpenWebApp?.invoke(url, name)
            }
        }

        @JavascriptInterface
        fun showToast(message: String) {
            coroutineScope.launch(Dispatchers.Main) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
