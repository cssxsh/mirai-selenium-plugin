package xyz.cssxsh.selenium

import kotlinx.serialization.*

@Serializable
public data class ChromeVersions(
    @SerialName("timestamp")
    val timestamp: String = "",
    @SerialName("versions")
    val versions: List<ChromeVersion> = emptyList()
)