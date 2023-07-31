package xyz.cssxsh.selenium

import kotlinx.serialization.*

@Serializable
public data class ChromeVersionDownload(
    @SerialName("platform")
    val platform: String = "",
    @SerialName("url")
    val url: String = ""
)