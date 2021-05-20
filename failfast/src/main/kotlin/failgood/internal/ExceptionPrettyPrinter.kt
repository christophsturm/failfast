package failgood.internal

class ExceptionPrettyPrinter(private val throwable: Throwable, testStackTrace: StackTraceElement? = null) {
    val stackTrace = run {
        val onlyElementsWithLineNumber = throwable.stackTrace
            .filter { it.lineNumber > 0 }

        val onlyFromFailFastUp = onlyElementsWithLineNumber.dropLastWhile { !it.className.startsWith("failgood") }
        val onlyInTest = if (throwable !is AssertionError) onlyFromFailFastUp else
            onlyFromFailFastUp.filter { testStackTrace == null || it.className.contains(testStackTrace.className) }
        onlyInTest.ifEmpty { onlyFromFailFastUp.ifEmpty { onlyElementsWithLineNumber } }
    }

    fun prettyPrint(): String {
        val cause = (throwable.cause?.let { "\ncause: $it\n${it.stackTraceToString()}" }) ?: ""
        val throwableName = if (throwable is AssertionError) "" else "${throwable::class.qualifiedName} : "
        return "${throwableName}${throwable.message}\n\tat " +
                stackTrace.joinToString("\n\tat ") + cause
    }
}
