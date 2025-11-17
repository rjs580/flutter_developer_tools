package dev.rutvik.flutter_developer_tools.models

data class PubPackageScore(
    val likeCount: Int = 0,
    val grantedPoints: Int = 0,
    val tags: List<String> = emptyList()
)
