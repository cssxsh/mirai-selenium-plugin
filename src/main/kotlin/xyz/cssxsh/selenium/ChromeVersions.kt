package xyz.cssxsh.selenium

import kotlinx.serialization.*

/**
 * [json-api-endpoints](https://github.com/GoogleChromeLabs/chrome-for-testing#json-api-endpoints)
 */
@Serializable
public data class ChromeVersions(
    @SerialName("timestamp")
    val timestamp: String = "",
    @SerialName("versions")
    val versions: List<ChromeVersion> = emptyList()
)