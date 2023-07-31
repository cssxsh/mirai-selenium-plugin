package xyz.cssxsh.selenium

import kotlinx.serialization.*

@Serializable
public data class ChromeVersion(
    @SerialName("downloads")
    val downloads: Downloads = Downloads(),
    @SerialName("revision")
    val revision: String = "",
    @SerialName("version")
    val version: String = ""
) {
    @Serializable
    public data class Downloads(
        @SerialName("chrome")
        val chrome: List<ChromeVersionDownload> = emptyList(),
        @SerialName("chromedriver")
        val chromedriver: List<ChromeVersionDownload> = emptyList()
    )
}