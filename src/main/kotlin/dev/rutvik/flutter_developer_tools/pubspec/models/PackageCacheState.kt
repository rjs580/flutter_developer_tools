package dev.rutvik.flutter_developer_tools.pubspec.models

data class PackageCacheState(
    var packages: List<PubPackage> = emptyList(),
    var lastPackageListUpdate: Long = 0L,
    var packageDetailsTimestamps: MutableMap<String, Long> = mutableMapOf()
)