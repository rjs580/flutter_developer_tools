package dev.rutvik.flutter_developer_tools.pubspec.referenceContributor

import com.intellij.openapi.paths.GlobalPathReferenceProvider
import com.intellij.openapi.paths.PathReferenceManager
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import dev.rutvik.flutter_developer_tools.pubspec.utils.PubspecUtils
import dev.rutvik.flutter_developer_tools.pubspec.utils.PubspecUtils.isPubPackageName
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * Contributes clickable references for pub.dev packages in pubspec.yaml files.
 *
 * Creates hyperlinks that:
 * - Link package names to the package overview page (pub.dev/packages/{name})
 * - Link version numbers to specific version pages (pub.dev/packages/{name}/versions/{version})
 * - Only activate for valid package names in dependencies/dev_dependencies sections
 * - Ignore local paths, git dependencies, and other non-pub.dev packages
 */
class PubPackageReferenceContributor : PsiReferenceContributor() {

    private val globalPathProvider by lazy {
        PathReferenceManager.getInstance().globalWebPathReferenceProvider as GlobalPathReferenceProvider
    }

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        // Register provider for package names (keys)
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(YAMLKeyValue::class.java),
            PackageNameReferenceProvider()
        )

        // Register provider for version numbers (scalar values)
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(YAMLScalar::class.java),
            VersionReferenceProvider()
        )
    }

    /**
     * Validates that a YAMLKeyValue represents a valid pub.dev package dependency.
     * Returns the package name if valid, null otherwise.
     */
    private fun validatePackageDependency(yamlKv: YAMLKeyValue): String? {
        if (!PubspecUtils.isPubspecFile(yamlKv.containingFile)) {
            return null
        }

        if (!PubspecUtils.isInDependencySection(yamlKv)) {
            return null
        }

        val pkgName = yamlKv.keyText
        if (!pkgName.isPubPackageName()) {
            return null
        }

        if (!PubspecUtils.isPubDevPackage(yamlKv)) {
            return null
        }

        return pkgName
    }

    /**
     * Reference provider for package names (YAML keys).
     */
    private inner class PackageNameReferenceProvider : PsiReferenceProvider() {
        override fun getReferencesByElement(
            element: PsiElement,
            context: ProcessingContext
        ): Array<PsiReference> {
            val yamlKv = element as YAMLKeyValue
            val pkgName = validatePackageDependency(yamlKv) ?: return PsiReference.EMPTY_ARRAY

            val references = mutableListOf<PsiReference>()
            val packageUrl = "https://pub.dev/packages/$pkgName"

            globalPathProvider.createUrlReference(
                yamlKv,
                packageUrl,
                TextRange.allOf(pkgName),
                references
            )

            return references.toTypedArray()
        }
    }

    /**
     * Reference provider for version numbers (YAML scalar values).
     */
    private inner class VersionReferenceProvider : PsiReferenceProvider() {
        override fun getReferencesByElement(
            element: PsiElement,
            context: ProcessingContext
        ): Array<PsiReference> {
            val yamlScalar = element as YAMLScalar
            val yamlKv = yamlScalar.parent as? YAMLKeyValue ?: return PsiReference.EMPTY_ARRAY

            // Check if this is the value of a package dependency
            if (yamlKv.value != yamlScalar) {
                return PsiReference.EMPTY_ARRAY
            }

            val pkgName = validatePackageDependency(yamlKv) ?: return PsiReference.EMPTY_ARRAY

            val versionText = yamlScalar.textValue
            if (!PubspecUtils.isSimpleVersion(versionText)) {
                return PsiReference.EMPTY_ARRAY
            }

            val references = mutableListOf<PsiReference>()
            val normalizedVersion = PubspecUtils.normalizeVersionString(versionText)
            val versionUrl = "https://pub.dev/packages/$pkgName/versions/$normalizedVersion"

            globalPathProvider.createUrlReference(
                yamlScalar,
                versionUrl,
                TextRange.allOf(yamlScalar.text),
                references
            )

            return references.toTypedArray()
        }
    }
}