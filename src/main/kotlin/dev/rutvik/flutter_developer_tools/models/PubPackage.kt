package dev.rutvik.flutter_developer_tools.models

data class PubPackage(
    var name: String = "",
    var latestVersion: String? = null,
    var description: String? = null,
    var likes: Int? = null,
    var pubPoints: Int? = null,
    var tags: List<String>? = null,
    var repositoryUrl: String? = null,
    var homepageUrl: String? = null
) {
    val isFlutterFavorite: Boolean
        get() = tags?.contains("is:favorite") ?: false

    val isDiscontinued: Boolean
        get() = tags?.contains("is:discontinued") ?: false

    val isDart3Incompatible: Boolean
        get() = tags?.contains("is:dart3-incompatible") ?: false

    companion object {
        fun withName(name: String) = PubPackage(
            name = name,
            latestVersion = null,
            description = null,
            likes = null,
            pubPoints = null,
            tags = null,
            repositoryUrl = null,
            homepageUrl = null
        )
    }
}
