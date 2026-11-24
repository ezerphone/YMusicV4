package io.ymusic.dynamicengine.yt

import io.ymusic.settings.SettingsStore
import kmpcommon.log.Logger
import kmphttp.KmpHttpClient
import kmphttp.RequestBuilder
import kmphttp.utf8String
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Songs-only build: the channels whose videos are always allowed, even without YouTube's music mark.
 *
 * The list is a plain text file on the owner's server ([start]'s `url`), one channel per line:
 * `UCxxxxxxxxxxxxxxxxxxxxxx = artist name`, or any line holding the channel id (a bare id, a
 * `https://www.youtube.com/channel/UC…` link). Only the id is read; blank lines and anything after
 * `#` are ignored.
 *
 * The last list that loaded is kept in the settings, so it applies from the first screen of the next
 * launch, also offline; the file is re-read on every launch and every [REFRESH] while the app runs.
 */
object SongsOnlyAllowlist {
    private const val CACHE_KEY = "songsOnly.allowedChannels"
    private val REFRESH = 6.hours
    private val RETRY = 5.minutes
    private val CHANNEL_ID = Regex("UC[0-9A-Za-z_-]{22}")

    @Volatile
    var channelIds: Set<String> = emptySet()
        private set

    /** Whether [item] may be listed, played or downloaded in the songs-only build. */
    fun allows(item: YtFeedItem): Boolean =
        item.kind != YtFeedItem.KIND_VIDEO || item.musicVideo || channelIdOf(item)?.let { it in channelIds } == true

    /** [item]'s channel id: [YtFeedItem.channelId], else the id inside its [YtFeedItem.channelUrl]. */
    fun channelIdOf(item: YtFeedItem): String? =
        item.channelId ?: item.channelUrl?.let { url -> CHANNEL_ID.find(url)?.value }

    /** The channel ids in an allowlist file's [text]. */
    fun parse(text: String): Set<String> = text.lineSequence()
        .map { it.substringBefore('#').trim() }
        .mapNotNull { line -> CHANNEL_ID.find(line)?.value }
        .toSet()

    /** Applies the cached list now, then keeps it in step with the file at [url]. */
    fun start(scope: CoroutineScope, client: KmpHttpClient, settings: SettingsStore, url: String, logger: Logger?) {
        channelIds = parse(settings.string(CACHE_KEY, "").value)
        if (url.isBlank()) return
        val log = logger?.fork("SongsOnlyAllowlist")
        scope.launch {
            while (true) {
                val ok = try {
                    val text = download(client, url)
                    channelIds = parse(text)
                    settings.putString(CACHE_KEY, text)
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // The cached list stays in force; the failure is logged and retried.
                    log?.log(Logger.Level.WARN, "allowlist download failed", e)
                    false
                }
                delay(if (ok) REFRESH else RETRY)
            }
        }
    }

    private suspend fun download(client: KmpHttpClient, url: String): String {
        val response = client.execute(RequestBuilder().url(url).build(), client.defaultRedirectOptions, null)
        try {
            check(response.code == 200) { "allowlist answered HTTP ${response.code}" }
            return response.body.utf8String()
        } finally {
            response.close()
        }
    }
}
