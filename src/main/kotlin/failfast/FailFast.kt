package failfast

import failfast.internal.ExceptionPrettyPrinter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import kotlin.reflect.KClass
import kotlin.system.exitProcess

data class ContextInfo(val contexts: Set<Context>, val tests: Int)

data class RootContext(val name: String, val function: ContextLambda)


typealias ContextLambda = suspend ContextDSL.() -> Unit

typealias TestLambda = suspend () -> Unit

fun Any.context(function: ContextLambda): RootContext =
    RootContext(this::class.simpleName ?: throw FailFastException("could not determine object name"), function)

fun context(description: String, function: ContextLambda): RootContext =
    RootContext(description, function)


fun describe(subjectDescription: String, function: ContextLambda): RootContext =
    RootContext(subjectDescription, function)

fun describe(subjectType: KClass<*>, function: ContextLambda): RootContext =
    RootContext(subjectType.simpleName!!, function)

interface ContextDSL {
    suspend fun test(name: String, function: TestLambda)
    suspend fun test(ignoredTestName: String)
    suspend fun context(name: String, function: ContextLambda)
    suspend fun describe(name: String, function: ContextLambda)
    fun <T> autoClose(wrapped: T, closeFunction: (T) -> Unit): T
    suspend fun it(behaviorDescription: String, function: TestLambda)
    suspend fun itWill(behaviorDescription: String)
    suspend fun itWill(behaviorDescription: String, function: TestLambda)
}

data class SuiteResult(
    val allTests: List<TestResult>,
    val failedTests: Collection<Failed>,
    val contextInfos: List<ContextInfo>
) {
    private val writer = PrintWriter(FileWriter(File("failfast.log"), true), true)
    val allOk = failedTests.isEmpty()
    private fun println(message: Any) {
        writer.println(message)
        kotlin.io.println(message)
    }

    fun check(throwException: Boolean = true) {

        println(ContextTreeReporter(allTests, contextInfos.flatMap { it.contexts }).stringReport().joinToString("\n"))
        if (allOk) {
            println("${allTests.size} tests. time: ${uptime()}")
            return

        }
        if (throwException)
            throw SuiteFailedException()
        else {
            val message = failedTests.joinToString {
                val testDescription = it.test.toString()
                val exceptionInfo = ExceptionPrettyPrinter().prettyPrint(it.failure)

                "$testDescription failed with $exceptionInfo"
            }
            println(message)
            println("${allTests.size} tests. ${failedTests.size} failed. total time: ${uptime()}")
            exitProcess(-1)
        }
    }
}

data class TestDescriptor(val parentContext: Context, val testName: String) {
    override fun toString(): String {
        return "${parentContext.asStringWithPath()} : $testName"
    }
}

data class Context(val name: String, val parent: Context?) {
    fun asStringWithPath(): String {
        val path = mutableListOf(this)
        var ctx = this
        while (true) {
            ctx = ctx.parent ?: break
            path.add(ctx)
        }
        return path.asReversed().joinToString(" > ") { it.name }
    }
}


object FailFast {
    /**
     * finds test classes
     * @param anyTestClass you can pass any test class here, its just used to find the classloader and source root
     * @param excludePattern if not null, classes that match this pattern are excluded
     * @param newerThan only return classes that are newer than this
     */
    fun findTestClasses(
        anyTestClass: KClass<*>,
        excludePattern: String? = null,
        newerThan: FileTime? = null
    ): List<Class<*>> {
        val classloader = anyTestClass.java.classLoader
        val root = Paths.get(anyTestClass.java.protectionDomain.codeSource.location.path)
        val results = mutableListOf<Class<*>>()
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                val path = root.relativize(file!!).toString()
                if (path.endsWith("Test.class") && (newerThan == null || attrs!!.lastModifiedTime() > newerThan)
                    && (excludePattern == null || !path.contains(excludePattern))
                ) {
                    results.add(classloader.loadClass(path.substringBefore(".class").replace("/", ".")))
                }
                return FileVisitResult.CONTINUE
            }

        })
        return results
    }
}
