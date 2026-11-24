package io.ymusic.android

/**
 * Songs-only build: the address of the allowlist file — the channels whose videos are always allowed,
 * one channel per line (`UC… = artist name`, or any line holding the id), `#` starts a comment. Empty = no allowlist.
 * See io.ymusic.dynamicengine.yt.SongsOnlyAllowlist.
 */
const val SONGS_ALLOWLIST_URL = "https://raw.githubusercontent.com/ezerphone/YMusicV4/allowlist/allowlist/allowlist.txt"
