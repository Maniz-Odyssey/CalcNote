package com.manish.calcnote.ui.note

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import com.manish.calcnote.domain.calculation.InlineResult

class InlineResultVisualTransformation(
    results: List<InlineResult>,
    private val resultColor: Color,
) : VisualTransformation {
    private val results = results.sortedBy { it.sourceEndOffset }

    override fun filter(text: AnnotatedString): TransformedText {
        val insertions = results
            .filter { it.sourceEndOffset in 0..text.length }
            .map { result ->
                val prefix = if (result.followsExplicitEquals) " " else "  = "
                Insertion(
                    originalOffset = result.sourceEndOffset,
                    text = prefix + result.formattedValue,
                )
            }

        val transformed = buildAnnotatedString {
            var sourceCursor = 0
            insertions.forEach { insertion ->
                append(text.subSequence(sourceCursor, insertion.originalOffset))
                withStyle(SpanStyle(color = resultColor, fontWeight = FontWeight.SemiBold)) {
                    append(insertion.text)
                }
                sourceCursor = insertion.originalOffset
            }
            append(text.subSequence(sourceCursor, text.length))
        }

        return TransformedText(
            text = transformed,
            offsetMapping = InsertionOffsetMapping(text.length, insertions),
        )
    }

    private data class Insertion(
        val originalOffset: Int,
        val text: String,
    )

    private class InsertionOffsetMapping(
        private val originalLength: Int,
        private val insertions: List<Insertion>,
    ) : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            val safeOffset = offset.coerceIn(0, originalLength)
            val insertedBefore = insertions
                .filter { it.originalOffset < safeOffset }
                .sumOf { it.text.length }
            return safeOffset + insertedBefore
        }

        override fun transformedToOriginal(offset: Int): Int {
            var insertedLength = 0
            insertions.forEach { insertion ->
                val transformedStart = insertion.originalOffset + insertedLength
                val transformedEnd = transformedStart + insertion.text.length
                if (offset <= transformedStart) {
                    return (offset - insertedLength).coerceIn(0, originalLength)
                }
                if (offset <= transformedEnd) {
                    return insertion.originalOffset
                }
                insertedLength += insertion.text.length
            }
            return (offset - insertedLength).coerceIn(0, originalLength)
        }
    }
}
