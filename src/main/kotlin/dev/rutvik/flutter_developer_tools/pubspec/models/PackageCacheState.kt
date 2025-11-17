package dev.rutvik.flutter_developer_tools.pubspec.models

data class PackageCacheState(
    var packages: List<PubPackage> = emptyList(),
    var lastPackageListUpdate: Long = 0L,
    var packageDetailsTimestamps: MutableMap<String, Long> = mutableMapOf()
) {
    // Override copy to ensure deep copy of mutable map
    fun copy(): PackageCacheState = PackageCacheState(
        packages = packages.toList(),
        lastPackageListUpdate = lastPackageListUpdate,
        packageDetailsTimestamps = packageDetailsTimestamps.toMutableMap()
    )
}