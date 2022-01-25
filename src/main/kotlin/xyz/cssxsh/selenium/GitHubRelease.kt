package xyz.cssxsh.selenium

import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
internal data class GitHubRelease(
    @SerialName("assets")
    val assets: List<Asset>,
    @SerialName("assets_url")
    val assetsUrl: String,
    @SerialName("author")
    val author: JsonObject,
    @SerialName("body")
    val body: String?,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("discussion_url")
    val discussionUrl: String? = null,
    @SerialName("draft")
    val draft: Boolean,
    @SerialName("html_url")
    val htmlUrl: String,
    @SerialName("id")
    val id: Long,
    @SerialName("mentions_count")
    val mentionsCount: Int = 0,
    @SerialName("name")
    val name: String,
    @SerialName("node_id")
    val nodeId: String,
    @SerialName("prerelease")
    val prerelease: Boolean,
    @SerialName("published_at")
    val publishedAt: String? = null,
    @SerialName("reactions")
    val reactions: JsonObject? = null,
    @SerialName("tag_name")
    val tagName: String,
    @SerialName("tarball_url")
    val tarballUrl: String,
    @SerialName("target_commitish")
    val targetCommitish: String,
    @SerialName("upload_url")
    val uploadUrl: String,
    @SerialName("url")
    val url: String,
    @SerialName("zipball_url")
    val zipballUrl: String
) {

    @Serializable
    data class Asset(
        @SerialName("browser_download_url")
        val browserDownloadUrl: String,
        @SerialName("content_type")
        val contentType: String,
        @SerialName("created_at")
        val createdAt: String,
        @SerialName("download_count")
        val downloadCount: Int,
        @SerialName("id")
        val id: Long,
        @SerialName("label")
        val label: String?,
        @SerialName("name")
        val name: String,
        @SerialName("node_id")
        val nodeId: String,
        @SerialName("size")
        val size: Int,
        @SerialName("state")
        val state: String,
        @SerialName("updated_at")
        val updatedAt: String,
        @SerialName("uploader")
        val uploader: JsonObject,
        @SerialName("url")
        val url: String
    )
}