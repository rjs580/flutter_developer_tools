package dev.rutvik.flutter_developer_tools.models

data class PubPackage(
    var name: String = "",
    var latestVersion: String? = null,
    var description: String? = null,
    var isFlutterFavorite: Boolean? = null,
    var likes: Int? = null,
    var pubPoints: Int? = null,
    var repositoryUrl: String? = null
) {
    companion object {
        fun withName(name: String) = PubPackage(
            name = name,
            latestVersion = null,
            description = null,
            isFlutterFavorite = null,
            likes = null,
            pubPoints = null,
            repositoryUrl = null
        )
    }
}

