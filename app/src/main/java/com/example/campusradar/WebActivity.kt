package com.example.campusradar

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.campusradar.ui.Edge
import com.google.android.material.appbar.MaterialToolbar

/**
 * 内置浏览器：用来打开网申页面。
 *
 * 为什么不用系统浏览器：网申页经常要登录、填简历、传附件，
 * 留在 App 里体验更连贯，返回时还能接着看列表。
 * 想用系统浏览器的话，设置页里有开关。
 */
class WebActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private lateinit var tvUrl: TextView
    private lateinit var toolbar: MaterialToolbar

    private var startUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web)

        startUrl = intent.getStringExtra(EXTRA_URL) ?: ""
        val label = intent.getStringExtra(EXTRA_TITLE) ?: "网申"
        android.util.Log.i("CampusRadar", "WebActivity onCreate url=$startUrl")

        toolbar = findViewById(R.id.toolbar)
        toolbar.title = label
        toolbar.setNavigationOnClickListener { finish() }
        Edge.setup(findViewById(R.id.root), toolbar)

        progress = findViewById(R.id.progress)
        tvUrl = findViewById(R.id.tvUrl)
        web = findViewById(R.id.web)

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_refresh -> { web.reload(); true }
                R.id.action_browser -> { openExternal(web.url ?: startUrl); true }
                R.id.action_copy -> { copyLink(); true }
                else -> false
            }
        }

        findViewById<View>(R.id.btnWebBack).setOnClickListener {
            if (web.canGoBack()) web.goBack() else finish()
        }
        findViewById<View>(R.id.btnWebForward).setOnClickListener {
            if (web.canGoForward()) web.goForward()
        }
        findViewById<View>(R.id.btnWebRefresh).setOnClickListener { web.reload() }
        findViewById<View>(R.id.btnWebOpenOut).setOnClickListener { openExternal(web.url ?: startUrl) }

        setupWebView()

        // 返回键优先网页内部后退，退到首页再关闭
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        if (startUrl.isBlank()) {
            Toast.makeText(this, "这条信息没有链接", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            web.loadUrl(startUrl)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val s = web.settings
        s.javaScriptEnabled = true            // 网申页基本都是前端渲染
        s.domStorageEnabled = true            // 登录态、表单草稿要靠它
        s.databaseEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true              // 平板上按屏幕宽度排版
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.javaScriptCanOpenWindowsAutomatically = true

        // 允许 Cookie，不然登录状态存不住
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val u = request?.url ?: return false
                // http(s) 留在内置浏览器，其它协议（微信/支付宝/电话等）交给系统
                return if (u.scheme == "http" || u.scheme == "https") {
                    false
                } else {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, u))
                    }
                    true
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                progress.visibility = View.VISIBLE
                progress.progress = 0
                tvUrl.text = url ?: ""
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                tvUrl.text = url ?: ""
                val t = view?.title
                if (!t.isNullOrBlank()) toolbar.title = t.take(28)
            }

            override fun onReceivedError(
                view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?
            ) {
                // 主页面加载失败才提示，图片之类的资源失败不用管
                if (request?.isForMainFrame == true) {
                    Toast.makeText(this@WebActivity, "页面加载失败，可以点右上角用浏览器打开", Toast.LENGTH_LONG).show()
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                if (newProgress >= 100) progress.visibility = View.GONE
            }
        }
    }

    private fun openExternal(url: String) {
        if (url.isBlank()) return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        }.onFailure {
            Toast.makeText(this, "没找到能打开网页的应用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyLink() {
        val url = web.url ?: startUrl
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("url", url))
        Toast.makeText(this, "链接已复制", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        // 避免 WebView 持有的资源泄漏
        runCatching {
            if (::web.isInitialized) {
                web.stopLoading()
                web.webViewClient = WebViewClient()
                web.destroy()
            }
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"

        fun intent(context: android.content.Context, url: String, title: String): Intent =
            Intent(context, WebActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
    }
}
