package com.manish.calcnote.ui.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manish.calcnote.domain.calculation.CalculationEngine
import com.manish.calcnote.ui.theme.CalcNoteTheme

private val DinnerExample = """
Dinner

pizza = 48
drinks = 36
salad = 12

ana = salad + 15%
group = pizza + drinks + 15%
each friend = group / 3
total = ana + group
""".trimIndent()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalcNoteScreen(modifier: Modifier = Modifier) {
    var note by rememberSaveable { mutableStateOf(DinnerExample) }
    val calculation = remember(note) { CalculationEngine.evaluate(note) }
    val resultColor = MaterialTheme.colorScheme.tertiary

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("CalcNote") },
                actions = {
                    TextButton(onClick = { note = "" }) {
                        Text("Clear")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Write normally. Calculations stay live.",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Use named values, + − × ÷, parentheses, and percentages.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 28.sp,
                ),
                label = { Text("Note") },
                placeholder = { Text("total = 120 + 15%") },
                supportingText = {
                    val message = when {
                        calculation.errors.isNotEmpty() -> calculation.errors.first().message
                        calculation.inlineResults.isEmpty() -> "Add a formula to see a result."
                        else -> "${calculation.inlineResults.size} live result${if (calculation.inlineResults.size == 1) "" else "s"}"
                    }
                    Text(message)
                },
                isError = calculation.errors.isNotEmpty(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                visualTransformation = InlineResultVisualTransformation(
                    results = calculation.inlineResults,
                    resultColor = resultColor,
                ),
                shape = RoundedCornerShape(24.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        note = note.trimEnd() + "\npeople = 3\neach person = total / people"
                    },
                ) {
                    Text("Add 3-person split")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CalcNoteScreenPreview() {
    CalcNoteTheme(dynamicColor = false) {
        CalcNoteScreen()
    }
}
