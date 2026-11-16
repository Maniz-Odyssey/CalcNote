package com.manish.calcnote.domain.calculation

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale

data class InlineResult(
    val lineIndex: Int,
    val sourceEndOffset: Int,
    val formattedValue: String,
    val followsExplicitEquals: Boolean,
)

data class CalculationError(
    val lineIndex: Int,
    val message: String,
)

data class DocumentCalculation(
    val variables: Map<String, BigDecimal>,
    val inlineResults: List<InlineResult>,
    val errors: List<CalculationError>,
)

object CalculationEngine {
    private val identifierPattern = Regex("[A-Za-z][A-Za-z0-9_ ]*")
    private val numericLiteralPattern = Regex("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)")

    fun evaluate(source: String): DocumentCalculation {
        val variables = linkedMapOf<String, BigDecimal>()
        val results = mutableListOf<InlineResult>()
        val errors = mutableListOf<CalculationError>()
        var lineStartOffset = 0

        source.split('\n').forEachIndexed { lineIndex, line ->
            evaluateLine(
                line = line,
                lineIndex = lineIndex,
                lineStartOffset = lineStartOffset,
                variables = variables,
                results = results,
                errors = errors,
            )
            lineStartOffset += line.length + 1
        }

        return DocumentCalculation(
            variables = variables.toMap(),
            inlineResults = results,
            errors = errors,
        )
    }

    private fun evaluateLine(
        line: String,
        lineIndex: Int,
        lineStartOffset: Int,
        variables: MutableMap<String, BigDecimal>,
        results: MutableList<InlineResult>,
        errors: MutableList<CalculationError>,
    ) {
        val trimmedLine = line.trim()
        if (trimmedLine.isEmpty()) return

        val firstEquals = line.indexOf('=')
        if (firstEquals >= 0) {
            val possibleName = line.substring(0, firstEquals).trim()
            if (identifierPattern.matches(possibleName)) {
                val rawExpression = line.substring(firstEquals + 1).trim()
                val explicitResult = rawExpression.endsWith('=')
                val expression = if (explicitResult) {
                    rawExpression.dropLast(1).trim()
                } else {
                    rawExpression
                }

                if (expression.isEmpty()) return

                try {
                    val value = ExpressionParser(expression, variables).parseAndEvaluate()
                    variables[normalizeName(possibleName)] = value
                    val shouldShowResult = explicitResult || !numericLiteralPattern.matches(expression)
                    if (shouldShowResult) {
                        results += InlineResult(
                            lineIndex = lineIndex,
                            sourceEndOffset = lineStartOffset + line.length,
                            formattedValue = format(value),
                            followsExplicitEquals = explicitResult,
                        )
                    }
                } catch (exception: CalculationException) {
                    errors += CalculationError(lineIndex, exception.message ?: "Invalid expression")
                }
                return
            }
        }

        if (trimmedLine.endsWith('=')) {
            val expression = trimmedLine.dropLast(1).trim()
            if (expression.isEmpty()) return
            try {
                val value = ExpressionParser(expression, variables).parseAndEvaluate()
                results += InlineResult(
                    lineIndex = lineIndex,
                    sourceEndOffset = lineStartOffset + line.length,
                    formattedValue = format(value),
                    followsExplicitEquals = true,
                )
            } catch (exception: CalculationException) {
                errors += CalculationError(lineIndex, exception.message ?: "Invalid expression")
            }
        }
    }

    private fun normalizeName(name: String): String =
        name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    private fun format(value: BigDecimal): String =
        value.setScale(2, RoundingMode.HALF_UP).toPlainString()

    private class ExpressionParser(
        expression: String,
        private val variables: Map<String, BigDecimal>,
    ) {
        private val tokens = Lexer(expression).scanTokens()
        private var current = 0

        fun parseAndEvaluate(): BigDecimal {
            val expression = parseExpression()
            if (!isAtEnd()) {
                throw CalculationException("Unexpected '${peek().lexeme}'")
            }
            return expression.evaluate(variables)
        }

        private fun parseExpression(): Expression = parseAddition()

        private fun parseAddition(): Expression {
            var expression = parseMultiplication()
            while (match(TokenType.PLUS, TokenType.MINUS)) {
                val operator = previous().type
                val right = parseMultiplication()
                expression = BinaryExpression(expression, operator, right)
            }
            return expression
        }

        private fun parseMultiplication(): Expression {
            var expression = parseUnary()
            while (match(TokenType.STAR, TokenType.SLASH)) {
                val operator = previous().type
                val right = parseUnary()
                expression = BinaryExpression(expression, operator, right)
            }
            return expression
        }

        private fun parseUnary(): Expression {
            if (match(TokenType.PLUS, TokenType.MINUS)) {
                return UnaryExpression(previous().type, parseUnary())
            }
            return parsePostfix()
        }

        private fun parsePostfix(): Expression {
            var expression = parsePrimary()
            while (match(TokenType.PERCENT)) {
                expression = PercentExpression(expression)
            }
            return expression
        }

        private fun parsePrimary(): Expression {
            if (match(TokenType.NUMBER)) {
                return NumberExpression(previous().lexeme.toBigDecimal())
            }
            if (match(TokenType.IDENTIFIER)) {
                return VariableExpression(normalizeName(previous().lexeme))
            }
            if (match(TokenType.LEFT_PAREN)) {
                val expression = parseExpression()
                consume(TokenType.RIGHT_PAREN, "Expected ')'")
                return expression
            }
            throw CalculationException("Expected a number, name, or '('")
        }

        private fun match(vararg types: TokenType): Boolean {
            for (type in types) {
                if (check(type)) {
                    advance()
                    return true
                }
            }
            return false
        }

        private fun consume(type: TokenType, message: String): Token {
            if (check(type)) return advance()
            throw CalculationException(message)
        }

        private fun check(type: TokenType): Boolean = peek().type == type

        private fun advance(): Token {
            if (!isAtEnd()) current += 1
            return previous()
        }

        private fun isAtEnd(): Boolean = peek().type == TokenType.END
        private fun peek(): Token = tokens[current]
        private fun previous(): Token = tokens[current - 1]
    }

    private sealed interface Expression {
        fun evaluate(variables: Map<String, BigDecimal>): BigDecimal
    }

    private data class NumberExpression(val value: BigDecimal) : Expression {
        override fun evaluate(variables: Map<String, BigDecimal>): BigDecimal = value
    }

    private data class VariableExpression(val name: String) : Expression {
        override fun evaluate(variables: Map<String, BigDecimal>): BigDecimal =
            variables[name] ?: throw CalculationException("Unknown value '$name'")
    }

    private data class UnaryExpression(
        val operator: TokenType,
        val operand: Expression,
    ) : Expression {
        override fun evaluate(variables: Map<String, BigDecimal>): BigDecimal {
            val value = operand.evaluate(variables)
            return if (operator == TokenType.MINUS) value.negate() else value
        }
    }

    private data class PercentExpression(val operand: Expression) : Expression {
        fun fraction(variables: Map<String, BigDecimal>): BigDecimal =
            operand.evaluate(variables).divide(BigDecimal(100), MathContext.DECIMAL128)

        override fun evaluate(variables: Map<String, BigDecimal>): BigDecimal = fraction(variables)
    }

    private data class BinaryExpression(
        val left: Expression,
        val operator: TokenType,
        val right: Expression,
    ) : Expression {
        override fun evaluate(variables: Map<String, BigDecimal>): BigDecimal {
            val leftValue = left.evaluate(variables)
            if (right is PercentExpression && (operator == TokenType.PLUS || operator == TokenType.MINUS)) {
                val percentage = leftValue.multiply(right.fraction(variables), MathContext.DECIMAL128)
                return if (operator == TokenType.PLUS) leftValue + percentage else leftValue - percentage
            }

            val rightValue = right.evaluate(variables)
            return when (operator) {
                TokenType.PLUS -> leftValue + rightValue
                TokenType.MINUS -> leftValue - rightValue
                TokenType.STAR -> leftValue.multiply(rightValue, MathContext.DECIMAL128)
                TokenType.SLASH -> {
                    if (rightValue.compareTo(BigDecimal.ZERO) == 0) {
                        throw CalculationException("Cannot divide by zero")
                    }
                    leftValue.divide(rightValue, MathContext.DECIMAL128)
                }
                else -> throw CalculationException("Unsupported operator")
            }
        }
    }

    private class Lexer(private val source: String) {
        private val tokens = mutableListOf<Token>()
        private var current = 0

        fun scanTokens(): List<Token> {
            while (current < source.length) {
                val character = source[current]
                when {
                    character.isWhitespace() -> current += 1
                    character == '+' -> addSingle(TokenType.PLUS)
                    character == '-' -> addSingle(TokenType.MINUS)
                    character == '*' || character == '×' -> addSingle(TokenType.STAR)
                    character == '/' || character == '÷' -> addSingle(TokenType.SLASH)
                    character == '%' -> addSingle(TokenType.PERCENT)
                    character == '(' -> addSingle(TokenType.LEFT_PAREN)
                    character == ')' -> addSingle(TokenType.RIGHT_PAREN)
                    character.isDigit() || character == '.' -> number()
                    character.isLetter() || character == '_' -> identifier()
                    else -> throw CalculationException("Unexpected '$character'")
                }
            }
            tokens += Token(TokenType.END, "")
            return tokens
        }

        private fun addSingle(type: TokenType) {
            tokens += Token(type, source[current].toString())
            current += 1
        }

        private fun number() {
            val start = current
            var decimalPoints = 0
            while (current < source.length && (source[current].isDigit() || source[current] == '.')) {
                if (source[current] == '.') decimalPoints += 1
                current += 1
            }
            val lexeme = source.substring(start, current)
            if (decimalPoints > 1 || lexeme == ".") {
                throw CalculationException("Invalid number '$lexeme'")
            }
            tokens += Token(TokenType.NUMBER, lexeme)
        }

        private fun identifier() {
            val start = current
            while (current < source.length && !source[current].isExpressionBoundary()) {
                current += 1
            }
            val lexeme = source.substring(start, current).trim()
            if (lexeme.isEmpty()) throw CalculationException("Expected a name")
            tokens += Token(TokenType.IDENTIFIER, lexeme)
        }

        private fun Char.isExpressionBoundary(): Boolean =
            this == '+' || this == '-' || this == '*' || this == '×' ||
                this == '/' || this == '÷' || this == '%' || this == '(' || this == ')'
    }

    private data class Token(val type: TokenType, val lexeme: String)

    private enum class TokenType {
        NUMBER,
        IDENTIFIER,
        PLUS,
        MINUS,
        STAR,
        SLASH,
        PERCENT,
        LEFT_PAREN,
        RIGHT_PAREN,
        END,
    }

    private class CalculationException(message: String) : IllegalArgumentException(message)
}
