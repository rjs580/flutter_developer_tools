package dev.rutvik.flutter_developer_tools.dart.hints

import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DartStaticTypeResolverTest {

    // Hover markdown as returned by the Dart 3.13.4 analysis server for textDocument/hover.

    @Test
    fun parsesSimpleType() {
        assertEquals("int", DartStaticTypeResolver.parseStaticType("```dart\nint count\n```\nType: `int`"))
    }

    @Test
    fun parsesGenericType() {
        assertEquals(
            "Map<String, int>",
            DartStaticTypeResolver.parseStaticType("```dart\nMap<String, int> map\n```\nType: `Map<String, int>`")
        )
    }

    @Test
    fun parsesRecordAndFunctionTypes() {
        assertEquals(
            "(int, String)",
            DartStaticTypeResolver.parseStaticType("```dart\n(int, String) rec\n```\nType: `(int, String)`")
        )
        assertEquals(
            "dynamic Function(dynamic, dynamic)",
            DartStaticTypeResolver.parseStaticType(
                "```dart\ndynamic Function(dynamic, dynamic) cb\n```\nType: `dynamic Function(dynamic, dynamic)`"
            )
        )
    }

    @Test
    fun ignoresTrailingDocumentation() {
        val markdown = "```dart\nString name\n```\nType: `String`\n\n---\nSome `doc` text."
        assertEquals("String", DartStaticTypeResolver.parseStaticType(markdown))
    }

    @Test
    fun returnsNullWithoutTypeLine() {
        assertNull(DartStaticTypeResolver.parseStaticType("```dart\nclass Foo\n```"))
    }

    @Test
    fun parsesMarkupContentHover() {
        val hover = Hover(MarkupContent(MarkupKind.MARKDOWN, "```dart\nFuture<int> fut\n```\nType: `Future<int>`"))
        assertEquals("Future<int>", DartStaticTypeResolver.parseStaticType(hover))
    }
}
