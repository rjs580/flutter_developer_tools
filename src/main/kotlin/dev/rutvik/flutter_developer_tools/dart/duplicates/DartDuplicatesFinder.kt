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
        val lineNumber: Int
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
                val duplicateInfo = DuplicateInfo(block, file, lineNumber)

                duplicateGroups.getOrPut(hash) { mutableListOf() }.add(duplicateInfo)
            }
        }

        // Filter out groups with less than 2 elements (no duplicates)
        return duplicateGroups
            .filter { it.value.size > 1 }
            .mapKeys { it.value.first().element }
    }

    /**
     * Finds all code blocks that could potentially be duplicates.
     */
    private fun findCodeBlocks(file: PsiFile): List<PsiElement> {
        val blocks = mutableListOf<PsiElement>()

        // Find method bodies
        PsiTreeUtil.findChildrenOfType(file, DartFunctionBody::class.java).forEach { body ->
            blocks.add(body)

            // Also check statement blocks within the method
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

        // Find constructor bodies
        PsiTreeUtil.findChildrenOfType(file, DartFactoryConstructorDeclaration::class.java).forEach {
            it.functionBody?.let { body -> blocks.add(body) }
        }

        PsiTreeUtil.findChildrenOfType(file, DartNamedConstructorDeclaration::class.java).forEach {
            it.functionBody?.let { body -> blocks.add(body) }
        }

        return blocks
    }

    /**
     * Checks if a code block is a valid candidate for duplicate detection.
     * Uses multiple heuristics to avoid flagging trivial code.
     */
    private fun isValidDuplicateCandidate(element: PsiElement): Boolean {
        // Check token count
        val tokenCount = countSignificantTokens(element)
        if (tokenCount < MIN_TOKEN_COUNT) return false

        // Check line count (simple heuristic)
        val lineCount = getLineCount(element)
        if (lineCount < MIN_LINES_OF_CODE) return false

        // Optionally: Check complexity (more sophisticated)
        // You could add a complexity score based on nesting depth, control flow, etc.

        return true
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
        } catch (e: Exception) {
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
                    is DartCallExpression -> builder.append("CALL|")
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