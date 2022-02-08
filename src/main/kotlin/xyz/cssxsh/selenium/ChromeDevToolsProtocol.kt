package xyz.cssxsh.selenium

import kotlinx.serialization.*

@Serializable
public data class ChromeDevToolsProtocol(
    @SerialName("domains")
    val domains: List<Domain>,
    @SerialName("version")
    val version: Version
) {

    @Serializable
    public data class Items(
        @SerialName("\$ref")
        val ref: String = "",
        @SerialName("type")
        val type: String = ""
    )

    @Serializable
    public data class Element(
        @SerialName("deprecated")
        val deprecated: Boolean = false,
        @SerialName("description")
        val description: String = "",
        @SerialName("enum")
        val `enum`: List<String> = emptyList(),
        @SerialName("experimental")
        val experimental: Boolean = false,
        @SerialName("items")
        val items: Items? = null,
        @SerialName("name")
        val name: String,
        @SerialName("optional")
        val optional: Boolean = false,
        @SerialName("\$ref")
        val ref: String = "",
        @SerialName("type")
        val type: String = ""
    )

    @Serializable
    public data class Command(
        @SerialName("deprecated")
        val deprecated: Boolean = false,
        @SerialName("description")
        val description: String = "",
        @SerialName("experimental")
        val experimental: Boolean = false,
        @SerialName("name")
        val name: String,
        @SerialName("parameters")
        val parameters: List<Element> = emptyList(),
        @SerialName("redirect")
        val redirect: String = "",
        @SerialName("returns")
        val returns: List<Element> = emptyList()
    )

    @Serializable
    public data class Event(
        @SerialName("deprecated")
        val deprecated: Boolean = false,
        @SerialName("description")
        val description: String = "",
        @SerialName("experimental")
        val experimental: Boolean = false,
        @SerialName("name")
        val name: String,
        @SerialName("parameters")
        val parameters: List<Element> = emptyList()
    )

    @Serializable
    public data class Type(
        @SerialName("deprecated")
        val deprecated: Boolean = false,
        @SerialName("description")
        val description: String = "",
        @SerialName("enum")
        val `enum`: List<String> = emptyList(),
        @SerialName("experimental")
        val experimental: Boolean = false,
        @SerialName("id")
        val id: String,
        @SerialName("items")
        val items: Items? = null,
        @SerialName("properties")
        val properties: List<Element> = emptyList(),
        @SerialName("type")
        val type: String
    )

    @Serializable
    public data class Domain(
        @SerialName("commands")
        val commands: List<Command> = emptyList(),
        @SerialName("dependencies")
        val dependencies: List<String> = emptyList(),
        @SerialName("deprecated")
        val deprecated: Boolean = false,
        @SerialName("description")
        val description: String = "",
        @SerialName("domain")
        val domain: String,
        @SerialName("events")
        val events: List<Event> = emptyList(),
        @SerialName("experimental")
        val experimental: Boolean = false,
        @SerialName("types")
        val types: List<Type> = emptyList()
    )

    @Serializable
    public data class Version(
        @SerialName("major")
        val major: String,
        @SerialName("minor")
        val minor: String
    )
}