package dev.rutvik.flutter_developer_tools.pubspec.completions

import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns

class PubPackageCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            PubPackageCompletionProvider()
        )
    }
}