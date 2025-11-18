package dev.rutvik.flutter_developer_tools.pubspec.ui

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.icons.AllIcons
import dev.rutvik.flutter_developer_tools.services.PubPackageCacheService

class PackageLookupRenderer(private val packageName: String) : LookupElementRenderer<LookupElement>() {
    override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {
        val cache = PubPackageCacheService.getInstance()

        val info = cache.getInfo(packageName)

        if (info?.latestVersion == null) {
            presentation.itemText = "$packageName:"
        } else {
            // Show package name with version in itemText (what gets inserted)
            presentation.itemText = "$packageName: ^${info.latestVersion}"

            // Use greyed out icon if not Flutter Favorite
            val favoriteIcon = if (info.isFlutterFavorite) {
                AllIcons.Ide.LikeSelected
            } else {
                AllIcons.Ide.LikeDimmed
            }

            // Show likes count with favorite icon
            presentation.isTypeIconRightAligned = true
            presentation.setTypeText("${info.likes ?: 0} likes", favoriteIcon)
            presentation.isTypeGrayed = true
        }
    }
}