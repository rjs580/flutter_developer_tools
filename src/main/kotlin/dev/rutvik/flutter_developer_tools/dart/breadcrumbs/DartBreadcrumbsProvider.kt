
package dev.rutvik.flutter_developer_tools.dart.breadcrumbs

import com.intellij.lang.Language
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.util.RefactoringDescriptionLocation
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import com.jetbrains.lang.dart.DartLanguage
import com.jetbrains.lang.dart.DartTokenTypes
import com.jetbrains.lang.dart.psi.*
import javax.swing.Icon

/**
 * Enhanced Breadcrumbs Navigation Provider for Dart files
 *
 * Provides comprehensive breadcrumb navigation for Dart elements including:
 * - Classes (abstract, regular, mixins) with hierarchy
 * - Methods (abstract, regular, getters, setters)
 * - Functions (top-level and local)
 * - Constructors (factory, named, default)
 * - Variables and fields
 * - Enums and enum values
 * - Extensions
 * - Widget instantiations (for Flutter widget tree navigation)
 */
class DartBreadcrumbsProvider : BreadcrumbsProvider {
    override fun getLanguages(): Array<Language> = arrayOf(DartLanguage.INSTANCE)

    override fun acceptElement(e: PsiElement): Boolean = when (e) {
        // Classes and class-like structures
        is DartClassDefinition,
        is DartMixinDeclaration,
        is DartEnumDefinition,
        is DartExtensionDeclaration,
            // Methods and functions
        is DartMethodDeclaration,
        is DartFunctionDeclarationWithBody,
        is DartFunctionDeclarationWithBodyOrNative,
        is DartGetterDeclaration,
        is DartSetterDeclaration,
            // Constructors
        is DartFactoryConstructorDeclaration,
        is DartNamedConstructorDeclaration,
            // Variables and fields
        is DartVarAccessDeclaration,
        is DartVarDeclarationList,
            // Enum values
        is DartEnumConstantDeclaration -> {
            (e as? DartComponent)?.name != null
        }
        // Widget instantiations - call expressions (covers both new and implicit constructors)
        is DartCallExpression -> {
            isWidgetInstantiation(e)
        }
        else -> false
    }

    override fun getElementInfo(e: PsiElement): String = when (e) {
        // Classes
        is DartClassDefinition -> buildClassInfo(e)
        is DartMixinDeclaration -> "mixin ${e.name}"
        is DartEnumDefinition -> "enum ${e.name}"
        is DartExtensionDeclaration -> buildExtensionInfo(e)

        // Methods and functions
        is DartMethodDeclaration -> buildMethodInfo(e)
        is DartFunctionDeclarationWithBody -> buildFunctionInfo(e)
        is DartFunctionDeclarationWithBodyOrNative -> buildFunctionInfo(e)
        is DartGetterDeclaration -> "get ${e.name}"
        is DartSetterDeclaration -> "set ${e.name}()"

        // Constructors
        is DartFactoryConstructorDeclaration -> buildFactoryInfo(e)
        is DartNamedConstructorDeclaration -> buildNamedConstructorInfo(e)

        // Variables
        is DartVarAccessDeclaration -> buildVarInfo(e)
        is DartVarDeclarationList -> buildVarListInfo(e)

        // Enum values
        is DartEnumConstantDeclaration -> e.name ?: ""

        // Widget instantiations
        is DartCallExpression -> buildWidgetInfoFromCall(e)

        else -> e.text.take(50)
    }

    override fun getElementIcon(element: PsiElement): Icon? {
        return when (element) {
            is DartClassDefinition -> {
                when {
                    element.isAbstract -> com.intellij.icons.AllIcons.Nodes.AbstractClass
                    else -> com.intellij.icons.AllIcons.Nodes.Class
                }
            }
            is DartMixinDeclaration -> com.intellij.icons.AllIcons.Nodes.Class
            is DartEnumDefinition -> com.intellij.icons.AllIcons.Nodes.Enum
            is DartMethodDeclaration -> {
                when {
                    element.isStatic -> com.intellij.icons.AllIcons.Nodes.Static
                    element.isAbstract -> com.intellij.icons.AllIcons.Nodes.AbstractMethod
                    else -> com.intellij.icons.AllIcons.Nodes.Method
                }
            }
            is DartGetterDeclaration -> com.intellij.icons.AllIcons.Nodes.Property
            is DartSetterDeclaration -> com.intellij.icons.AllIcons.Nodes.Property
            is DartFunctionDeclarationWithBody -> com.intellij.icons.AllIcons.Nodes.Function
            is DartVarAccessDeclaration -> {
                when {
                    isStaticField(element) -> com.intellij.icons.AllIcons.Nodes.Static
                    isConstant(element) -> com.intellij.icons.AllIcons.Nodes.Constant
                    else -> com.intellij.icons.AllIcons.Nodes.Field
                }
            }
            is DartEnumConstantDeclaration -> com.intellij.icons.AllIcons.Nodes.Enum
            // Widget instantiations - use a tag icon to represent UI elements
            is DartCallExpression -> com.intellij.icons.AllIcons.Nodes.Tag
            else -> super.getElementIcon(element)
        }
    }

    override fun getElementTooltip(element: PsiElement): String {
        val description = ElementDescriptionUtil.getElementDescription(
            element,
            RefactoringDescriptionLocation.WITH_PARENT
        )

        // Add additional context information that's not in the base description
        return when (element) {
            is DartClassDefinition -> buildClassTooltip(element, description)
            is DartMethodDeclaration -> buildMethodTooltip(element, description)
            is DartFunctionDeclarationWithBody -> buildFunctionTooltip(element, description)
            is DartCallExpression -> buildWidgetTooltipFromCall(element)
            else -> description
        }
    }

    // Helper methods for building element info

    private fun buildClassInfo(cls: DartClassDefinition): String {
        val prefix = when {
            cls.isAbstract -> "abstract class "
            else -> "class "
        }
        val name = cls.name ?: ""

        // Always show superclass hierarchy for all classes (including custom widgets)
        val superclassInfo = cls.superclass?.let { superclass ->
            val superName = superclass.text
            // Filter out 'Object' as it's implicit and not useful
            if (superName.isNotEmpty() && superName != "Object") {
                " : $superName"
            } else {
                ""
            }
        } ?: ""

        return "$prefix$name$superclassInfo"
    }

    private fun buildExtensionInfo(ext: DartExtensionDeclaration): String {
        val name = ext.name ?: "extension"
        val type = ext.type.text?.let { " on $it" } ?: ""
        return "$name$type"
    }

    private fun buildMethodInfo(method: DartMethodDeclaration): String {
        val name = method.name ?: return ""
        val params = method.formalParameterList.text?.let {
            if (it.length > 30) "(…)" else it
        } ?: "()"

        val prefix = when {
            method.isStatic -> "static "
            method.isAbstract -> "abstract "
            else -> ""
        }

        return "$prefix$name$params"
    }

    private fun buildFunctionInfo(func: DartComponent): String {
        val name = func.name ?: return ""
        val params = when (func) {
            is DartFunctionDeclarationWithBody -> func.formalParameterList.text
            is DartFunctionDeclarationWithBodyOrNative -> func.formalParameterList.text
            else -> null
        }?.let { if (it.length > 30) "(…)" else it } ?: "()"

        return "$name$params"
    }

    private fun buildFactoryInfo(factory: DartFactoryConstructorDeclaration): String {
        val name = factory.name ?: return "factory"
        return "factory $name()"
    }

    private fun buildNamedConstructorInfo(constructor: DartNamedConstructorDeclaration): String {
        val className = (constructor.parent?.parent as? DartClassDefinition)?.name ?: ""
        val constructorName = constructor.name ?: ""
        return if (className.isNotEmpty() && constructorName.isNotEmpty()) {
            "$className.$constructorName()"
        } else {
            "constructor"
        }
    }

    private fun buildVarInfo(varAccess: DartVarAccessDeclaration): String {
        val name = varAccess.name ?: return ""
        val prefix = when {
            isConstant(varAccess) -> "const "
            isFinal(varAccess) -> "final "
            isStaticField(varAccess) -> "static "
            else -> ""
        }
        return "$prefix$name"
    }

    private fun buildVarListInfo(varList: DartVarDeclarationList): String {
        val firstVar = varList.varAccessDeclaration
        return buildVarInfo(firstVar)
    }

    private fun buildWidgetInfoFromCall(callExpr: DartCallExpression): String {
        val widgetName = getWidgetNameFromCall(callExpr) ?: return "Widget"
        return widgetName
    }

    // Helper methods for tooltips

    private fun buildClassTooltip(cls: DartClassDefinition, baseDescription: String): String {
        // Use base description as-is since it already contains hierarchy info
        // Only add supplementary information not included in the description
        val parts = mutableListOf<String>()
        parts.add(baseDescription)

        // Only add additional info if not already in description
        // Check if description already contains "with" or "implements"
        val hasWith = baseDescription.contains("with")
        val hasImplements = baseDescription.contains("implements")

        // Add mixins info only if not already present
        if (!hasWith) {
            cls.mixins?.let { mixinsElement ->
                val mixinTypes = PsiTreeUtil.findChildrenOfType(mixinsElement, DartType::class.java)
                    .mapNotNull { it.text }
                if (mixinTypes.isNotEmpty()) {
                    parts.add("with ${mixinTypes.joinToString(", ")}")
                }
            }
        }

        // Add interfaces info only if not already present
        if (!hasImplements) {
            cls.interfaces?.let { interfacesElement ->
                val interfaceTypes = PsiTreeUtil.findChildrenOfType(interfacesElement, DartType::class.java)
                    .mapNotNull { it.text }
                if (interfaceTypes.isNotEmpty()) {
                    parts.add("implements ${interfaceTypes.joinToString(", ")}")
                }
            }
        }

        return parts.joinToString("\n")
    }

    private fun buildMethodTooltip(method: DartMethodDeclaration, baseDescription: String): String {
        // Use base description and add supplementary info
        val parts = mutableListOf<String>()
        parts.add(baseDescription)

        // Add return type info if present and not in description
        method.returnType?.let { returnType ->
            val returnText = returnType.text
            if (!baseDescription.contains(returnText)) {
                parts.add("Returns: $returnText")
            }
        }

        return parts.joinToString("\n")
    }

    private fun buildFunctionTooltip(func: DartFunctionDeclarationWithBody, baseDescription: String): String {
        // Use base description and add supplementary info
        val parts = mutableListOf<String>()
        parts.add(baseDescription)

        // Add return type info if present and not in description
        func.returnType?.let { returnType ->
            val returnText = returnType.text
            if (!baseDescription.contains(returnText)) {
                parts.add("Returns: $returnText")
            }
        }

        return parts.joinToString("\n")
    }

    private fun buildWidgetTooltipFromCall(callExpr: DartCallExpression): String {
        val widgetName = getWidgetNameFromCall(callExpr) ?: return "Widget instantiation"

        val parts = mutableListOf<String>()
        parts.add("$widgetName()")

        // Add key parameter if present
        val arguments = callExpr.arguments
        arguments?.argumentList?.namedArgumentList?.find {
            it.parameterReferenceExpression?.text == "key"
        }?.let {
            parts.add("key: ${it.expression?.text ?: "..."}")
        }

        return parts.joinToString("\n")
    }

    // Utility methods for widgets

    private fun isWidgetInstantiation(callExpr: DartCallExpression): Boolean {
        // Check if this looks like a widget constructor call
        // Widgets typically start with uppercase letter
        val name = getWidgetNameFromCall(callExpr) ?: return false
        return name.firstOrNull()?.isUpperCase() == true
    }

    private fun getWidgetNameFromCall(callExpr: DartCallExpression): String? {
        // Get the expression being called
        val expression = callExpr.expression ?: return null

        // Handle different call patterns:
        // 1. Simple: IconButton(...)
        // 2. Named constructor: FilledButton.icon(...)
        return when (expression) {
            is DartReferenceExpression -> {
                // Simple constructor call
                expression.text
            }
            else -> {
                // For named constructors or other patterns, get the first identifier
                val text = expression.text
                text.substringBefore('(').substringBefore('.')
            }
        }
    }

    // Utility methods

    private fun isStaticField(element: DartVarAccessDeclaration): Boolean {
        val parent = element.parent?.parent
        if (parent !is DartVarDeclarationList) return false

        // Check if it's a static field by searching for 'static' keyword in the PSI tree
        return PsiTreeUtil.findChildrenOfType(parent, PsiElement::class.java)
            .any { it.node?.elementType == DartTokenTypes.STATIC }
    }

    private fun isConstant(element: DartVarAccessDeclaration): Boolean {
        val parent = element.parent?.parent
        if (parent !is DartVarDeclarationList) return false

        // Check for 'const' keyword by searching in the PSI tree
        return PsiTreeUtil.findChildrenOfType(parent, PsiElement::class.java)
            .any { it.node?.elementType == DartTokenTypes.CONST }
    }

    private fun isFinal(element: DartVarAccessDeclaration): Boolean {
        val parent = element.parent?.parent
        if (parent !is DartVarDeclarationList) return false

        // Check for 'final' keyword by searching in the PSI tree
        return PsiTreeUtil.findChildrenOfType(parent, PsiElement::class.java)
            .any { it.node?.elementType == DartTokenTypes.FINAL }
    }
}