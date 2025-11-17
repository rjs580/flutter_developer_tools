package dev.rutvik.flutter_developer_tools.pubspec.ui

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.icons.AllIcons
import com.intellij.platform.ml.impl.turboComplete.SuggestionGenerator.Companion.suggestionGenerator
import com.intellij.ui.AnimatedIcon
import dev.rutvik.flutter_developer_tools.pubspec.api.PubDevApi
import dev.rutvik.flutter_developer_tools.pubspec.models.PubPackage
import dev.rutvik.flutter_developer_tools.pubspec.services.PubPackageCacheService

class PackageLookupRenderer(private val packageName: String, private val top5: Boolean) : LookupElementRenderer<LookupElement>() {
    override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {
        val cache = PubPackageCacheService.getInstance()

        val info = if (top5) cache.getInfo(packageName) else null

        if (info?.latestVersion == null) {
            presentation.itemText = "$packageName:"
        } else {
            // Show package name with version in itemText (what gets inserted)
            presentation.itemText = "$packageName: ^${info.latestVersion}"

            // Use greyed out icon if not Flutter Favorite
            val favoriteIcon = if (info.isFlutterFavorite == true) {
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