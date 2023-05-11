/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.compiler.plugins.kotlin

import androidx.compose.compiler.plugins.kotlin.facade.AnalysisResult
import androidx.compose.compiler.plugins.kotlin.facade.SourceFile
import org.jetbrains.kotlin.checkers.utils.CheckerTestUtil
import org.jetbrains.kotlin.utils.addToStdlib.flatGroupBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows

abstract class AbstractComposeDiagnosticsTest(useFir: Boolean) : AbstractCompilerTest(useFir) {
    protected fun check(
        expectedText: String,
        platformText: String? = null,
        ignoreParseErrors: Boolean = false
    ) {
        val clearText = CheckerTestUtil.parseDiagnosedRanges(expectedText, mutableListOf())
        val clearPlatformText = platformText?.let {
            CheckerTestUtil.parseDiagnosedRanges(platformText, mutableListOf())
        }

        val errors = analyze(
            listOfNotNull(
                SourceFile("test.kt", clearText, ignoreParseErrors),
                clearPlatformText?.let { SourceFile("Actual.kt", it, ignoreParseErrors) }
            )
        ).diagnostics

        assertEquals(expectedText, annotateDiagnostics(clearText, errors["test.kt"] ?: listOf()))
        if (clearPlatformText != null) {
            assertEquals(platformText, annotateDiagnostics(clearPlatformText, errors["Actual.kt"] ?: listOf()))
        }
    }

    private fun annotateDiagnostics(
        clearText: String,
        diagnostics: List<AnalysisResult.Diagnostic>
    ): String {
        val rangeToDiagnostics = diagnostics.flatGroupBy { it.textRanges }.mapValues { entry ->
            entry.value.map { it.factoryName }.toSet()
        }
        val startOffsetToGroups = rangeToDiagnostics.entries.groupBy(
            keySelector = { it.key.startOffset },
            valueTransform = { it.value }
        )
        val endOffsetsToGroups = rangeToDiagnostics.entries.groupBy(
            keySelector = { it.key.endOffset },
            valueTransform = { it.value }
        )
        return buildString {
            for ((i,c) in clearText.withIndex()) {
                endOffsetsToGroups[i]?.let { groups ->
                    repeat(groups.size) { append("<!>") }
                }
                startOffsetToGroups[i]?.let { groups ->
                    for (diagnostics in groups) {
                        append("<!${diagnostics.joinToString(",")}!>")
                    }
                }
                append(c)
            }
        }

    }

    protected fun checkFail(expectedText: String) {
        assertThrows(AssertionError::class.java) {
            check(expectedText)
        }
    }
}
