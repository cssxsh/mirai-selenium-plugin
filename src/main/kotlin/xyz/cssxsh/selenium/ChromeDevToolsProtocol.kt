package xyz.cssxsh.selenium

import kotlinx.serialization.*

/**
 * 支持的 Chrome Dev Tools Protocol 内容
 */
@Serializable
public data class ChromeDevToolsProtocol(
    @SerialName("domains")
    val domains: List<Domain>,
    @SerialName("version")
    val version: Version
) {

    /**
     * 细项
     * @param ref 引用
     * @param type 类型
     */
    @Serializable
    public data class Items(
        @SerialName("\$ref")
        val ref: String = "",
        @SerialName("type")
        val type: String = ""
    )

    /**
     * 参数/返回值
     * @param deprecated 主版本
     * @param description 描述
     * @param enum 枚举
     * @param experimental 实验性
     * @param items 细项
     * @param name 名字
     * @param optional 可选的
     * @param ref 引用
     * @param type 类型
     */
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

    /**
     * 命令
     * @param deprecated 主版本
     * @param description 描述
     * @param experimental 实验性
     * @param name 名字
     * @param parameters 参数
     * @param redirect 重定向
     * @param returns 返回值
     */
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

    /**
     * 事件
     * @param deprecated 主版本
     * @param description 描述
     * @param experimental 实验性
     * @param name 名字
     * @param parameters 参数
     */
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

    /**
     * 类型信息
     * @param deprecated 主版本
     * @param description 描述
     * @param enum 枚举
     * @param experimental 实验性
     * @param id ID
     * @param items 细项
     * @param properties 配置
     * @param type 类型
     */
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

    /**
     * 包
     * @param commands 命令
     * @param dependencies 依赖
     * @param deprecated 主版本
     * @param description 描述
     * @param domain 包名
     * @param events 事件
     * @param experimental 实验性
     * @param types 类型
     */
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

    /**
     * 版本
     * @param major 主版本
     * @param minor 副版本
     */
    @Serializable
    public data class Version(
        @SerialName("major")
        val major: String,
        @SerialName("minor")
        val minor: String
    )
}