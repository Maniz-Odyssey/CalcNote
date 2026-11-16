package com.manish.calcnote.domain.calculation

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculationEngineTest {
    @Test
    fun dinnerExample_calculatesPercentagesAndSplit() {
        val calculation = CalculationEngine.evaluate(
            """
            pizza = 48
            drinks = 36
            salad = 12
            ana = salad + 15%
            group = pizza + drinks + 15%
            each friend = group / 3
            total = ana + group
            """.trimIndent(),
        )

        assertEquals("13.80", calculation.resultForLine(3))
        assertEquals("96.60", calculation.resultForLine(4))
        assertEquals("32.20", calculation.resultForLine(5))
        assertEquals("110.40", calculation.resultForLine(6))
        assertTrue(calculation.errors.isEmpty())
    }

    @Test
    fun changingSourceValue_recalculatesEveryDependentLine() {
        val calculation = CalculationEngine.evaluate(
            """
            pizza = 48
            drinks = 54
            salad = 12
            ana = salad + 15%
            group = pizza + drinks + 15%
            each friend = group / 3
            total = ana + group
            """.trimIndent(),
        )

        assertEquals("13.80", calculation.resultForLine(3))
        assertEquals("117.30", calculation.resultForLine(4))
        assertEquals("39.10", calculation.resultForLine(5))
        assertEquals("131.10", calculation.resultForLine(6))
    }

    @Test
    fun parser_honorsPrecedenceParenthesesAndMultiwordNames() {
        val calculation = CalculationEngine.evaluate(
            """
            base price = 10
            first = base price + 2 * 3
            second = (base price + 2) * 3
            """.trimIndent(),
        )

        assertEquals(BigDecimal("16"), calculation.variables["first"])
        assertEquals(BigDecimal("36"), calculation.variables["second"])
    }

    @Test
    fun trailingEquals_evaluatesStandaloneExpression() {
        val calculation = CalculationEngine.evaluate("(10 + 5) / 3 =")

        assertEquals("5.00", calculation.resultForLine(0))
    }

    @Test
    fun invalidInput_reportsErrorWithoutThrowing() {
        val unknownValue = CalculationEngine.evaluate("total = missing + 2")
        val divisionByZero = CalculationEngine.evaluate("total = 10 / 0")

        assertEquals("Unknown value 'missing'", unknownValue.errors.single().message)
        assertEquals("Cannot divide by zero", divisionByZero.errors.single().message)
    }

    private fun DocumentCalculation.resultForLine(lineIndex: Int): String? =
        inlineResults.firstOrNull { it.lineIndex == lineIndex }?.formattedValue
}
