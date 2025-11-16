package dev.rutvik.flutter_developer_tools.pubspec.models

data class PubPackage(
    val name: String,
    val latest: String,
    val description: String?,
    val isFlutterFavorite: Boolean = false,
    val likes: Int = 0,
    val pubPoints: Int = 0
)

