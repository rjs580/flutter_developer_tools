
package dev.rutvik.flutter_developer_tools.dart.duplicates

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.lang.dart.psi.*
import java.security.MessageDigest

/**
 * Utility class for finding duplicate code fragments in Dart files.
 * Uses AST-based comparison with structural hashing for performance.
 */
object DartDuplicatesFinder {

    data class DuplicateInfo(
        val element: PsiElement,
        val file: PsiFile,
        val lineNumber: Int,
        val lineCount: Int
    )

    // Minimum number of statements required to consider a block for duplicate detection
    // Lower values will detect smaller duplicates but may produce more false positives
    private const val MIN_STATEMENTS_COUNT = 5

    // Minimum number of significant tokens (AST nodes) required
    // Kotlin uses 40-50, but Dart's AST is slightly different
    // 30 is a good balance - catches meaningful duplicates without being too noisy
    private const val MIN_TOKEN_COUNT = 30

    // Minimum lines of code (rough heuristic)
    private const val MIN_LINES_OF_CODE = 3

    // Minimum complexity units to consider for duplicate detection
    // Formula: 2 * statements + expressions (matches IntelliJ's approach)
    // Default ~50 is a good balance for Dart
    private const val MIN_COMPLEXITY_UNITS = 50

    /**
     * Finds all duplicate code fragments in the given file.
     * Returns a map of original fragment to list of its duplicates.
     */
    fun findDuplicates(file: PsiFile): Map<PsiElement, List<DuplicateInfo>> {
        val duplicateGroups = mutableMapOf<String, MutableList<DuplicateInfo>>()

        // Find all potential code blocks to check for duplicates
        val codeBlocks = findCodeBlocks(file)

        codeBlocks.forEach { block ->
            ProgressManager.checkCanceled()

            if (!isValidDuplicateCandidate(block)) return@forEach

            // Generate structural hash for the block
            val hash = generateStructuralHash(block)
            if (hash != null) {
                val lineNumber = getLineNumber(block)
                val lineCount = getLineCount(block)
                val duplicateInfo = DuplicateInfo(block, file, lineNumber, lineCount)

                duplicateGroups.getOrPut(hash) { mutableListOf() }.add(duplicateInfo)
            }
        }

        // Filter out groups with less than 2 elements (no duplicates)
        val allDuplicates = duplicateGroups
            .filter { it.value.size > 1 }
            .mapKeys { it.value.first().element }

        // Remove nested duplicates - keep only the outermost/largest ones
        return filterNestedDuplicates(allDuplicates)
    }

    /**
     * Filters out duplicate fragments that are contained within other larger duplicates.
     * This ensures we report the largest/most meaningful duplicate, not nested fragments.
     */
    private fun filterNestedDuplicates(
        duplicates: Map<PsiElement, List<DuplicateInfo>>
    ): Map<PsiElement, List<DuplicateInfo>> {
        val elementsToRemove = mutableSetOf<PsiElement>()

        // For each duplicate, check if it's contained in another duplicate
        duplicates.keys.forEach { element1 ->
            duplicates.keys.forEach { element2 ->
                if (element1 != element2) {
                    // Check if element1 is a child of element2
                    if (PsiTreeUtil.isAncestor(element2, element1, true)) {
                        // element1 is nested inside element2, so remove the smaller one
                        elementsToRemove.add(element1)
                    }
                }
            }
        }

        return duplicates.filterKeys { it !in elementsToRemove }
    }

    /**
     * Finds all code blocks that could potentially be duplicates.
     */
    private fun findCodeBlocks(file: PsiFile): List<PsiElement> {
        val blocks = mutableListOf<PsiElement>()

        // Find method/function declarations (not just bodies)
        // This captures the entire method including signature
        PsiTreeUtil.findChildrenOfType(file, DartMethodDeclaration::class.java).forEach { method ->
            blocks.add(method) // Add the whole method, not just body
        }

        PsiTreeUtil.findChildrenOfType(file, DartFunctionDeclarationWithBody::class.java).forEach { func ->
            blocks.add(func) // Add the whole function
        }

        PsiTreeUtil.findChildrenOfType(file, DartFunctionDeclarationWithBodyOrNative::class.java).forEach { func ->
            blocks.add(func) // Add the whole function
        }

        // Find getter/setter declarations
        PsiTreeUtil.findChildrenOfType(file, DartGetterDeclaration::class.java).forEach { getter ->
            blocks.add(getter)
        }

        PsiTreeUtil.findChildrenOfType(file, DartSetterDeclaration::class.java).forEach { setter ->
            blocks.add(setter)
        }

        // Find constructor declarations
        PsiTreeUtil.findChildrenOfType(file, DartFactoryConstructorDeclaration::class.java).forEach { constructor ->
            blocks.add(constructor)
        }

        PsiTreeUtil.findChildrenOfType(file, DartNamedConstructorDeclaration::class.java).forEach { constructor ->
            blocks.add(constructor)
        }

        // Find widget instantiations (CallExpressions that look like widgets)
        // We collect ALL widget calls first, then filter nested ones later
        PsiTreeUtil.findChildrenOfType(file, DartCallExpression::class.java).forEach { callExpr ->
            if (isLikelyWidgetInstantiation(callExpr)) {
                blocks.add(callExpr)
            }
        }

        // Also check inner blocks for duplicated logic within methods
        PsiTreeUtil.findChildrenOfType(file, DartFunctionBody::class.java).forEach { body ->
            PsiTreeUtil.findChildrenOfType(body, DartBlock::class.java).forEach { block ->
                val statements = PsiTreeUtil.findChildrenOfType(block, DartStatements::class.java).firstOrNull()
                if (statements != null) {
                    val statementCount = PsiTreeUtil.findChildrenOfType(statements, PsiElement::class.java)
                        .count { it.parent == statements }
                    if (statementCount >= MIN_STATEMENTS_COUNT) {
                        blocks.add(block)
                    }
                }
            }
        }

        return blocks
    }

    /**
     * Checks if a call expression is likely a widget instantiation.
     * Widgets typically:
     * - Start with uppercase letter
     * - Have named arguments
     * - Are used in build methods or widget trees
     */
    private fun isLikelyWidgetInstantiation(callExpr: DartCallExpression): Boolean {
        // Get the called name
        val name = when (val expression = callExpr.expression) {
            is DartReferenceExpression -> expression.text
            else -> expression?.text?.substringBefore('(')?.substringBefore('.')
        } ?: return false

        // Check if name starts with uppercase (typical for widgets)
        if (name.firstOrNull()?.isUpperCase() != true) return false

        // Check if it has arguments (widgets usually do)
        val arguments = callExpr.arguments ?: return false
        val argumentList = arguments.argumentList ?: return false

        // Check if it's sufficiently complex (has children, actions, etc.)
        val namedArgCount = argumentList.namedArgumentList.size

        // Consider it a widget if:
        // - It has at least 2 named arguments (typical for widgets)
        // - Or it's clearly a widget name (common Flutter widgets)
        return namedArgCount >= 2 || isCommonFlutterWidget(name)
    }

    /**
     * Checks if the name matches common Flutter widget patterns.
     */
    private fun isCommonFlutterWidget(name: String): Boolean {
        val commonWidgets = setOf(
            "Scaffold", "AppBar", "Container", "Column", "Row", "Stack",
            "ListView", "GridView", "Card", "Text", "Icon", "IconButton",
            "ElevatedButton", "TextButton", "OutlinedButton", "FilledButton",
            "TextField", "Padding", "Center", "Align", "SizedBox",
            "Expanded", "Flexible", "Drawer", "BottomNavigationBar"
        )
        return name in commonWidgets || name.endsWith("AppBar") || name.endsWith("Button")
    }

    /**
     * Checks if a code block is a valid candidate for duplicate detection.
     * Uses multiple heuristics to avoid flagging trivial code.
     */
    private fun isValidDuplicateCandidate(element: PsiElement): Boolean {
        // Skip named constructors that only define default parameters
        // These are Dart patterns that cannot be refactored
        if (element is DartNamedConstructorDeclaration) {
            if (isDefaultParameterOnlyConstructor(element)) return false
        }

        // Skip factory constructors that only delegate to another constructor
        // These are variant patterns like factory Theme.light() => const Theme(...)
        if (element is DartFactoryConstructorDeclaration) {
            if (isDelegatingFactoryConstructor(element)) return false
        }

        // For widget call expressions, use different thresholds
        if (element is DartCallExpression && isLikelyWidgetInstantiation(element)) {
            // Widgets need fewer tokens since they're more declarative
            val tokenCount = countSignificantTokens(element)
            if (tokenCount < 25) return false // Slightly higher threshold for widgets

            val lineCount = getLineCount(element)
            if (lineCount < 8) return false // At least 8 lines to avoid small fragments

            // Check that it has sufficient named arguments
            val namedArgCount = element.arguments?.argumentList?.namedArgumentList?.size ?: 0
            if (namedArgCount < 3) return false // Increase to at least 3 named arguments

            // Additional check: widgets with mostly different argument values aren't true duplicates
            // Only flag if the widget has repeated callback patterns (like multiple identical onPressed)
//            if (!hasRepeatedCallbackPatterns(element)) return false

            return true
        }

        // Use IntelliJ-style complexity units as primary check
        val complexityUnits = calculateComplexityUnits(element)
        if (complexityUnits < MIN_COMPLEXITY_UNITS) return false

        // Secondary check: ensure minimum code volume
        val tokenCount = countSignificantTokens(element)
        if (tokenCount < MIN_TOKEN_COUNT) return false

        val lineCount = getLineCount(element)
        if (lineCount < MIN_LINES_OF_CODE) return false

        if (isTrivialAccessor(element)) return false

        return true
    }

    /**
     * Checks if an element is a trivial getter or setter.
     * Trivial accessors are simple patterns like:
     * - get x => _field;
     * - get x => _field as Type;
     * - set x(value) { _field = value; }
     */
    private fun isTrivialAccessor(element: PsiElement): Boolean {
        when (element) {
            is DartGetterDeclaration -> {
                // Check if it's just a return statement or expression
                val body = element.functionBody
                if (body != null) {
                    val returnCount = PsiTreeUtil.findChildrenOfType(body, DartReturnStatement::class.java).size
                    val expressionCount = countSignificantExpressions(body)

                    // If it has only 1-2 expressions/returns and no control flow, it's trivial
                    if (returnCount + expressionCount <= 2 && !hasControlFlow(body)) {
                        return true
                    }
                }
            }
            is DartSetterDeclaration -> {
                // Check if it's just an assignment
                val body = element.functionBody
                if (body != null) {
                    val assignmentCount = PsiTreeUtil.findChildrenOfType(body, DartAssignExpression::class.java).size
                    val callCount = PsiTreeUtil.findChildrenOfType(body, DartCallExpression::class.java).size

                    // Setters with only 1-3 simple operations (assignment + method calls) are trivial
                    if (assignmentCount + callCount <= 3 && !hasControlFlow(body)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Counts significant expressions (not including trivial references).
     */
    private fun countSignificantExpressions(element: PsiElement): Int {
        var count = 0
        element.accept(object : DartRecursiveVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is DartCallExpression,
                    is DartNewExpression,
                    is DartAssignExpression -> count++
                }
                super.visitElement(element)
            }
        })
        return count
    }

    /**
     * Checks if an element contains control flow structures.
     */
    private fun hasControlFlow(element: PsiElement): Boolean {
        return PsiTreeUtil.findChildOfType(element, DartIfStatement::class.java) != null ||
                PsiTreeUtil.findChildOfType(element, DartForStatement::class.java) != null ||
                PsiTreeUtil.findChildOfType(element, DartWhileStatement::class.java) != null ||
                PsiTreeUtil.findChildOfType(element, DartSwitchStatement::class.java) != null ||
                PsiTreeUtil.findChildOfType(element, DartTryStatement::class.java) != null
    }

    /**
     * Checks if a named argument contains a lambda/function expression.
     */
    private fun containsLambdaExpression(element: DartNamedArgument): Boolean {
        return PsiTreeUtil.findChildOfType(element, DartFunctionExpression::class.java) != null
    }

    /**
     * Checks if a named constructor only contains default parameter assignments.
     * These are Dart patterns like Theme.light() / Theme.dark() that define
     * default values for each variant and cannot be refactored.
     */
    private fun isDefaultParameterOnlyConstructor(element: DartNamedConstructorDeclaration): Boolean {
        // Check if the constructor has a body with actual statements
        val body = element.functionBody ?: return true

        // Check if body has any real statements (not just initializer list)
        val block = PsiTreeUtil.findChildOfType(body, DartBlock::class.java) ?: return true

        val statements = PsiTreeUtil.findChildOfType(block, DartStatements::class.java)
        return statements == null || statements.textLength == 0
    }

    /**
     * Checks if a factory constructor only delegates to another constructor.
     */
    private fun isDelegatingFactoryConstructor(element: DartFactoryConstructorDeclaration): Boolean {
        val body = element.functionBody ?: return false

        // Check for arrow syntax (=>) with a single expression
        // Arrow functions contain the expression directly without a block
        val bodyText = body.text.trimStart()
        if (!bodyText.startsWith("=>")) return false

        // Check if the expression is a constructor call (starts with const/new or uppercase)
        val expression = PsiTreeUtil.findChildOfType(body, DartCallExpression::class.java)
            ?: PsiTreeUtil.findChildOfType(body, DartNewExpression::class.java)

        return expression != null
    }

    /**
     * Calculates complexity units using IntelliJ's formula: 2 * statements + expressions.
     * This provides a consistent metric for determining if code is complex enough
     * to be worth flagging as a duplicate.
     */
    private fun calculateComplexityUnits(element: PsiElement): Int {
        val statementCount = countStatements(element)
        val expressionCount = countExpressions(element)
        return 2 * statementCount + expressionCount
    }

    /**
     * Counts statements in the element.
     */
    private fun countStatements(element: PsiElement): Int {
        var count = 0
        element.accept(object : DartRecursiveVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is DartVarDeclarationList,
                    is DartIfStatement,
                    is DartForStatement,
                    is DartWhileStatement,
                    is DartDoWhileStatement,
                    is DartSwitchStatement,
                    is DartTryStatement,
                    is DartReturnStatement,
                    is DartBreakStatement,
                    is DartContinueStatement,
                    is DartAssertStatement -> count++
                }
                // Also count expression statements by checking parent relationship
                // Expression statements in Dart are expressions that are direct children of DartStatements
                if (element.parent is DartStatements && element is DartExpression) {
                    count++
                }
                super.visitElement(element)
            }
        })
        return count
    }

    /**
     * Counts expressions in the element.
     */
    private fun countExpressions(element: PsiElement): Int {
        var count = 0
        element.accept(object : DartRecursiveVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is DartCallExpression,
                    is DartNewExpression,
                    is DartAssignExpression,
                    is DartAdditiveExpression,
                    is DartMultiplicativeExpression,
                    is DartCompareExpression,
                    is DartLogicAndExpression,
                    is DartLogicOrExpression,
                    is DartPrefixExpression,
                    is DartTernaryExpression,
                    is DartThrowExpression -> count++
                }
                super.visitElement(element)
            }
        })
        return count
    }

    /**
     * Counts significant tokens (excluding whitespace and comments).
     */
    private fun countSignificantTokens(element: PsiElement): Int {
        var count = 0
        element.accept(object : DartRecursiveVisitor() {
            override fun visitElement(element: PsiElement) {
                val elementType = element.node?.elementType
                if (elementType != null && !elementType.toString().contains("WHITE_SPACE") &&
                    !elementType.toString().contains("COMMENT")
                ) {
                    count++
                }
                super.visitElement(element)
            }
        })
        return count
    }

    /**
     * Counts the number of lines in a code fragment.
     */
    private fun getLineCount(element: PsiElement): Int {
        val document = element.containingFile?.viewProvider?.document ?: return 0
        val startLine = document.getLineNumber(element.textRange.startOffset)
        val endLine = document.getLineNumber(element.textRange.endOffset)
        return endLine - startLine + 1
    }

    /**
     * Generates a structural hash for a code fragment based on its AST structure.
     * Ignores variable names and literal values to detect structural similarities.
     */
    private fun generateStructuralHash(element: PsiElement): String? {
        return try {
            val structure = buildStructuralRepresentation(element)
            if (structure.isEmpty()) return null

            val digest = MessageDigest.getInstance("MD5")
            val hashBytes = digest.digest(structure.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Builds a structural representation of the code fragment.
     * This representation is based on AST node types rather than actual values.
     */
    private fun buildStructuralRepresentation(element: PsiElement): String {
        val builder = StringBuilder()

        element.accept(object : DartRecursiveVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    // Record control flow structures
                    is DartIfStatement -> builder.append("IF|")
                    is DartForStatement -> builder.append("FOR|")
                    is DartWhileStatement -> builder.append("WHILE|")
                    is DartDoWhileStatement -> builder.append("DOWHILE|")
                    is DartSwitchStatement -> builder.append("SWITCH|")
                    is DartTryStatement -> builder.append("TRY|")
                    is DartCatchPart -> builder.append("CATCH|")
                    is DartReturnStatement -> builder.append("RETURN|")
                    is DartThrowExpression -> builder.append("THROW|")

                    // Record expressions (without literal values)
                    is DartCallExpression -> {
                        // For widget calls, include the widget name to distinguish different widgets
                        val widgetName = element.expression?.text?.substringBefore('(')?.substringBefore('.') ?: ""
                        if (widgetName.firstOrNull()?.isUpperCase() == true) {
                            builder.append("WIDGET[$widgetName]|")
                        } else {
                            // Include method name to distinguish different method calls
                            // This prevents false positives on similar delegation patterns
                            builder.append("CALL[$widgetName]|")
                        }
                    }
                    is DartNamedArgument -> {
                        // Include parameter names to preserve widget structure
                        val paramName = element.parameterReferenceExpression?.text ?: "param"

                        // If the argument contains a lambda, don't hash the value here.
                        // The lambda's content will be processed separately and any
                        // duplicates within it will be detected naturally.
                        val valueHash = if (!containsLambdaExpression(element)) {
                            // Include a simplified hash of the argument value
                            val valueText = element.expression?.text?.take(50) ?: ""
                            "_${valueText.hashCode().toString(16)}"
                        } else {
                            ""
                        }
                        builder.append("ARG[$paramName$valueHash]|")
                    }
                    is DartAssignExpression -> builder.append("ASSIGN|")
                    is DartAdditiveExpression -> builder.append("ADD|")
                    is DartMultiplicativeExpression -> builder.append("MULT|")
                    is DartCompareExpression -> builder.append("CMP|")
                    is DartLogicAndExpression -> builder.append("AND|")
                    is DartLogicOrExpression -> builder.append("OR|")
                    is DartPrefixExpression -> builder.append("PREFIX[${element.firstChild.text}]|")
                    is DartNewExpression -> builder.append("NEW|")
                    is DartReferenceExpression -> {
                        // Use a placeholder for identifiers to ignore naming differences
                        builder.append("REF|")
                    }
                    is DartListLiteralExpression -> builder.append("LIST{")
                    is DartArguments -> builder.append("ARGS{")

                    // Record literals as generic types (not actual values)
                    is DartStringLiteralExpression -> builder.append("STR|")
                    is DartLongTemplateEntry -> builder.append("TPL|")

                    // Other significant nodes
                    is DartBlock -> builder.append("BLOCK{")
                    is DartFunctionBody -> builder.append("BODY{")
                }

                super.visitElement(element)

                // Close blocks
                when (element) {
                    is DartBlock, is DartFunctionBody -> builder.append("}")
                    is DartListLiteralExpression, is DartArguments -> builder.append("}")
                }
            }
        })

        return builder.toString()
    }

    /**
     * Gets the line number for a PSI element.
     */
    private fun getLineNumber(element: PsiElement): Int {
        val document = element.containingFile?.viewProvider?.document ?: return 0
        return document.getLineNumber(element.textRange.startOffset) + 1
    }
}