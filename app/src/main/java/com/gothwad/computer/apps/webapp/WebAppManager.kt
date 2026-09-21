package com.gothwad.computer.apps.webapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.gothwad.computer.data.ConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.regex.Pattern

object WebAppManager {

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    // Curated high-res official brand logos for major websites
    private val CURATED_BRAND_LOGOS = mapOf(
        "youtube.com" to "https://logo.clearbit.com/youtube.com",
        "youtu.be" to "https://logo.clearbit.com/youtube.com",
        "music.youtube.com" to "https://logo.clearbit.com/youtube.com",
        "telegram.org" to "https://logo.clearbit.com/telegram.org",
        "t.me" to "https://logo.clearbit.com/telegram.org",
        "whatsapp.com" to "https://logo.clearbit.com/whatsapp.com",
        "web.whatsapp.com" to "https://logo.clearbit.com/whatsapp.com",
        "spotify.com" to "https://logo.clearbit.com/spotify.com",
        "open.spotify.com" to "https://logo.clearbit.com/spotify.com",
        "twitter.com" to "https://logo.clearbit.com/x.com",
        "x.com" to "https://logo.clearbit.com/x.com",
        "instagram.com" to "https://logo.clearbit.com/instagram.com",
        "github.com" to "https://logo.clearbit.com/github.com",
        "reddit.com" to "https://logo.clearbit.com/reddit.com",
        "discord.com" to "https://logo.clearbit.com/discord.com",
        "netflix.com" to "https://logo.clearbit.com/netflix.com",
        "chatgpt.com" to "https://logo.clearbit.com/openai.com",
        "chat.openai.com" to "https://logo.clearbit.com/openai.com",
        "openai.com" to "https://logo.clearbit.com/openai.com",
        "claude.ai" to "https://logo.clearbit.com/anthropic.com",
        "gemini.google.com" to "https://logo.clearbit.com/google.com",
        "notion.so" to "https://logo.clearbit.com/notion.so",
        "figma.com" to "https://logo.clearbit.com/figma.com",
        "canva.com" to "https://logo.clearbit.com/canva.com",
        "photopea.com" to "https://logo.clearbit.com/photopea.com",
        "linkedin.com" to "https://logo.clearbit.com/linkedin.com",
        "pinterest.com" to "https://logo.clearbit.com/pinterest.com",
        "tiktok.com" to "https://logo.clearbit.com/tiktok.com",
        "twitch.tv" to "https://logo.clearbit.com/twitch.tv",
        "amazon.com" to "https://logo.clearbit.com/amazon.com",
        "primevideo.com" to "https://logo.clearbit.com/primevideo.com",
        "disneyplus.com" to "https://logo.clearbit.com/disneyplus.com",
        "wikipedia.org" to "https://logo.clearbit.com/wikipedia.org",
        "chess.com" to "https://logo.clearbit.com/chess.com",
        "lichess.org" to "https://logo.clearbit.com/lichess.org",
        "duolingo.com" to "https://logo.clearbit.com/duolingo.com",
        "slack.com" to "https://logo.clearbit.com/slack.com",
        "zoom.us" to "https://logo.clearbit.com/zoom.us",
        "codepen.io" to "https://logo.clearbit.com/codepen.io",
        "replit.com" to "https://logo.clearbit.com/replit.com",
        "stackoverflow.com" to "https://logo.clearbit.com/stackoverflow.com",
        "medium.com" to "https://logo.clearbit.com/medium.com",
        "quora.com" to "https://logo.clearbit.com/quora.com"
    )

    fun formatUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "https://www.google.com"
        return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }

    fun getSafeId(url: String): String {
        return runCatching {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(url.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        }.getOrDefault("pwa_${System.currentTimeMillis()}")
    }

    /**
     * Downloads an image bitmap with redirects handling, desktop User-Agent, and validation.
     */
    fun downloadBitmap(urlString: String, maxRedirects: Int = 5): Bitmap? {
        var currentUrl = urlString
        for (i in 0..maxRedirects) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(currentUrl)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", BROWSER_USER_AGENT)
                    setRequestProperty(
                        "Accept",
                        "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
                    )
                }

                val code = conn.responseCode
                if (code in 300..399) {
                    val redirectUrl = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (redirectUrl.isNullOrEmpty()) return null
                    currentUrl = if (redirectUrl.startsWith("http")) {
                        redirectUrl
                    } else {
                        URL(url, redirectUrl).toString()
                    }
                    continue
                }

                if (code in 200..299) {
                    val bmp = conn.inputStream.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                    if (bmp != null && bmp.width > 8 && bmp.height > 8) {
                        return bmp
                    }
                }
                return null
            } catch (_: Exception) {
                return null
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    suspend fun fetchFaviconAndTitle(
        rawUrl: String,
        explicitIconUrl: String? = null
    ): Pair<String?, Bitmap?> = withContext(Dispatchers.IO) {
        val formatted = formatUrl(rawUrl)
        var fetchedTitle: String? = null
        var fetchedIcon: Bitmap? = null

        // 1. Guess clean title and extract clean domain host
        var host = ""
        runCatching {
            host = URL(formatted).host.lowercase().removePrefix("www.")
            val parts = host.split(".")
            if (parts.isNotEmpty()) {
                fetchedTitle = parts[0].replaceFirstChar { it.uppercase() }
            }
        }

        // 2. Try explicit icon URL if provided (from Store or user)
        if (!explicitIconUrl.isNullOrEmpty()) {
            fetchedIcon = downloadBitmap(explicitIconUrl)
        }

        // 3. Try Curated High-Res Brand Logo
        if (fetchedIcon == null && host.isNotEmpty()) {
            for ((domainKey, logoUrl) in CURATED_BRAND_LOGOS) {
                if (host == domainKey || host.endsWith(".$domainKey")) {
                    fetchedIcon = downloadBitmap(logoUrl)
                    if (fetchedIcon != null) break
                }
            }
        }

        // 4. Try Clearbit Official Brand Logo API
        if (fetchedIcon == null && host.isNotEmpty()) {
            val clearbitUrl = "https://logo.clearbit.com/$host"
            fetchedIcon = downloadBitmap(clearbitUrl)
        }

        // 5. Try IconHorse High-Res Favicon API (returns 128px PNG)
        if (fetchedIcon == null && host.isNotEmpty()) {
            val iconHorseUrl = "https://icon.horse/icon/$host"
            fetchedIcon = downloadBitmap(iconHorseUrl)
        }

        // 6. Try Google Favicon V2 (size=128)
        if (fetchedIcon == null && host.isNotEmpty()) {
            val encoded = URLEncoder.encode(formatted, "UTF-8")
            val gFaviconUrl = "https://t2.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=$encoded&size=128"
            fetchedIcon = downloadBitmap(gFaviconUrl)
        }

        // 7. Try Google S2 Favicon API
        if (fetchedIcon == null && host.isNotEmpty()) {
            val gS2Url = "https://www.google.com/s2/favicons?domain=$host&sz=128"
            fetchedIcon = downloadBitmap(gS2Url)
        }

        // 8. Try website HTML scraping for apple-touch-icon or og:image
        if (fetchedIcon == null) {
            runCatching {
                val conn = (URL(formatted).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", BROWSER_USER_AGENT)
                }
                if (conn.responseCode in 200..299) {
                    val html = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()

                    // Parse HTML title if not yet determined
                    val titleMatcher = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (titleMatcher.find()) {
                        val fullTitle = titleMatcher.group(1)?.trim()
                        if (!fullTitle.isNullOrEmpty()) {
                            fetchedTitle = fullTitle.split("|", "-", "—", "•").first().trim()
                        }
                    }

                    // Parse apple-touch-icon or icon
                    val iconMatcher = Pattern.compile("<link[^>]+rel=[\"'](apple-touch-icon|icon)[\"'][^>]+href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (iconMatcher.find()) {
                        val iconHref = iconMatcher.group(2)
                        if (!iconHref.isNullOrEmpty()) {
                            val absoluteIconUrl = if (iconHref.startsWith("http")) iconHref else URL(URL(formatted), iconHref).toString()
                            fetchedIcon = downloadBitmap(absoluteIconUrl)
                        }
                    }
                }
            }
        }

        // 9. Last fallback: domain /apple-touch-icon.png or /favicon.ico
        if (fetchedIcon == null && host.isNotEmpty()) {
            runCatching {
                val hostUrl = URL(formatted)
                val directApple = "${hostUrl.protocol}://${hostUrl.host}/apple-touch-icon.png"
                fetchedIcon = downloadBitmap(directApple)
                if (fetchedIcon == null) {
                    val directIco = "${hostUrl.protocol}://${hostUrl.host}/favicon.ico"
                    fetchedIcon = downloadBitmap(directIco)
                }
            }
        }

        Pair(fetchedTitle, fetchedIcon)
    }

    suspend fun installWebApp(
        context: Context,
        url: String,
        title: String,
        icon: Bitmap? = null,
        iconUrl: String? = null
    ): String = withContext(Dispatchers.IO) {
        val formattedUrl = formatUrl(url)
        val finalTitle = title.trim().ifEmpty {
            URL(formattedUrl).host.removePrefix("www.").replaceFirstChar { it.uppercase() }
        }
        val safeId = getSafeId(formattedUrl)
        val pwaPkg = "pwa://$formattedUrl"

        // Resolve icon bitmap: passed in -> downloaded from iconUrl / fetchFaviconAndTitle -> fallback squircle
        var targetBitmap = icon
        if (targetBitmap == null && !iconUrl.isNullOrEmpty()) {
            targetBitmap = downloadBitmap(iconUrl)
        }
        if (targetBitmap == null) {
            val (_, fetched) = fetchFaviconAndTitle(formattedUrl, iconUrl)
            targetBitmap = fetched
        }
        if (targetBitmap == null) {
            targetBitmap = generateFallbackSquircleIcon(finalTitle)
        }

        // Save icon to app internal storage
        var iconPath: String? = null
        runCatching {
            val iconsDir = File(context.filesDir, "webapp_icons")
            if (!iconsDir.exists()) iconsDir.mkdirs()
            val iconFile = File(iconsDir, "$safeId.png")
            FileOutputStream(iconFile).use { out ->
                targetBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            iconPath = iconFile.absolutePath
        }

        // Save into LauncherConfig
        ConfigStore(context).update { cfg ->
            val updatedOrder = cfg.pcDesktopOrder.toMutableList().apply {
                if (!contains(pwaPkg)) add(pwaPkg)
            }
            val updatedLabels = cfg.pcCustomLabels.toMutableMap().apply {
                put(pwaPkg, finalTitle)
            }
            val updatedIcons = cfg.pcCustomIcons.toMutableMap().apply {
                if (iconPath != null) put(pwaPkg, iconPath!!)
            }
            cfg.copy(
                pcDesktopOrder = updatedOrder,
                pcCustomLabels = updatedLabels,
                pcCustomIcons = updatedIcons
            )
        }

        pwaPkg
    }

    suspend fun uninstallWebApp(context: Context, urlOrPkg: String): Boolean = withContext(Dispatchers.IO) {
        val pwaPkg = if (urlOrPkg.startsWith("pwa://")) urlOrPkg else "pwa://${formatUrl(urlOrPkg)}"
        val safeId = getSafeId(pwaPkg.removePrefix("pwa://"))
        runCatching {
            val iconFile = File(File(context.filesDir, "webapp_icons"), "$safeId.png")
            if (iconFile.exists()) iconFile.delete()
        }
        ConfigStore(context).update { cfg ->
            val updatedOrder = cfg.pcDesktopOrder.filter { it != pwaPkg }
            val updatedLabels = cfg.pcCustomLabels.filterKeys { it != pwaPkg }
            val updatedIcons = cfg.pcCustomIcons.filterKeys { it != pwaPkg }
            val updatedPinned = cfg.pcPinnedApps.filter { it != pwaPkg }
            cfg.copy(
                pcDesktopOrder = updatedOrder,
                pcCustomLabels = updatedLabels,
                pcCustomIcons = updatedIcons,
                pcPinnedApps = updatedPinned
            )
        }
        true
    }

    fun hasSavedIcon(context: Context, pwaPkg: String): Boolean {
        val url = if (pwaPkg.startsWith("pwa://")) pwaPkg.removePrefix("pwa://") else pwaPkg
        val safeId = getSafeId(url)
        val iconFile = File(File(context.filesDir, "webapp_icons"), "$safeId.png")
        return iconFile.exists() && iconFile.length() > 100
    }

    fun saveIcon(context: Context, pwaPkg: String, bitmap: Bitmap) {
        val url = if (pwaPkg.startsWith("pwa://")) pwaPkg.removePrefix("pwa://") else pwaPkg
        val safeId = getSafeId(url)
        runCatching {
            val iconsDir = File(context.filesDir, "webapp_icons").apply { mkdirs() }
            val iconFile = File(iconsDir, "$safeId.png")
            java.io.FileOutputStream(iconFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    fun loadSavedIcon(context: Context, pwaPkg: String): Bitmap? {
        val url = if (pwaPkg.startsWith("pwa://")) pwaPkg.removePrefix("pwa://") else pwaPkg
        val safeId = getSafeId(url)
        val iconFile = File(File(context.filesDir, "webapp_icons"), "$safeId.png")
        if (iconFile.exists()) {
            return runCatching { BitmapFactory.decodeFile(iconFile.absolutePath) }.getOrNull()
        }
        return null
    }

    fun generateFallbackSquircleIcon(title: String): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Generate consistent background color based on title hash
        val colors = intArrayOf(
            0xFF2563EB.toInt(),
            0xFF059669.toInt(),
            0xFFD97706.toInt(),
            0xFF7C3AED.toInt(),
            0xFFDC2626.toInt(),
            0xFF0284C7.toInt()
        )
        val color = colors[Math.abs(title.hashCode()) % colors.size]

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
        }
        val radius = size * 0.24f
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, paint)

        // Draw initial letter
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            textSize = size * 0.5f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val letter = title.trim().take(1).uppercase()
        val textY = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(letter, size / 2f, textY, textPaint)

        return bitmap
    }
}
