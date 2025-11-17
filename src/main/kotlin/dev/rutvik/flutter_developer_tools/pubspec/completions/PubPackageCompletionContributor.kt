package dev.rutvik.flutter_developer_tools.pubspec.completions

import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile

class PubPackageCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            psiElement().inFile(psiFile().withName("pubspec.yaml")),
            PubPackageCompletionProvider()
        )
    }
}