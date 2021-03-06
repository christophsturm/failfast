package failgood.internal

import failgood.FailGoodException
import failgood.Failed
import failgood.RootContext
import failgood.Success
import failgood.describe
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.junit.platform.commons.annotation.Testable
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.all
import strikt.assertions.containsExactly
import strikt.assertions.doesNotContain
import strikt.assertions.endsWith
import strikt.assertions.get
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThanOrEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotNull
import strikt.assertions.map
import strikt.assertions.single

@Testable
class ContextExecutorTest {
    private var assertionError: AssertionError? = null
    val context =
        describe(ContextExecutor::class) {
            describe("with a valid root context") {
                val ctx =
                    RootContext("root context") {
                        test("test 1") {}
                        test("test 2") {}
                        test("failed test") {
                            assertionError = AssertionError("failed")
                            throw assertionError!!
                        }
                        context("context 1")
                        {
                            context("context 2")
                            { test("test 3") {} }
                        }
                        context("context 4") { test("test 4") {} }
                    }

                val contextInfo = coroutineScope {
                    ContextExecutor(ctx, this).execute()
                }
                it("returns tests in the same order as they are declared in the file") {
                    expectThat(contextInfo.tests.keys).map { it.testName }
                        .containsExactly(
                            "test 1",
                            "test 2",
                            "failed test",
                            "test 3",
                            "test 4"
                        )
                }
                it("returns deferred test results") {
                    val testResults = contextInfo.tests.values.awaitAll()
                    val successful = testResults.filter { it.isSuccess }
                    val failed = testResults - successful
                    expectThat(successful.map { it.test.testName })
                        .containsExactly(
                            "test 1",
                            "test 2",
                            "test 3",
                            "test 4"
                        )
                    expectThat(failed).map { it.test.testName }.containsExactly("failed test")
                }

                it("returns contexts in the same order as they appear in the file") {
                    expectThat(contextInfo.contexts).map { it.name }
                        .containsExactly("root context", "context 1", "context 2", "context 4")
                }
                it("reports time of successful tests") {
                    expectThat(contextInfo.tests.values.awaitAll().map { it.result }
                        .filterIsInstance<Success>()).isNotEmpty()
                        .all { get { timeMicro }.isGreaterThanOrEqualTo(1) }
                }
                describe("reports failed tests") {
                    val failure =
                        contextInfo.tests.values.awaitAll().map { it.result }.filterIsInstance<Failed>().single()
                    it("reports exception for failed tests") {
                        expectThat(assertionError).isNotNull()
                        val assertionError = assertionError!!
                        expectThat(failure.failure) {
                            get { stackTraceToString() }.isEqualTo(assertionError.stackTraceToString())
                        }
                    }
                }
            }
            describe("reports line numbers") {
                var rootContextLine = 0
                var context1Line = 0
                var context2Line = 0
                var test1Line = 0
                var test2Line = 0
                val ctx =
                    RootContext("root context") {
                        rootContextLine = RuntimeException().stackTrace.first().lineNumber - 1
                        describe("context 1") {
                            context1Line = RuntimeException().stackTrace.first().lineNumber - 1
                            it("test1") {
                                test1Line = RuntimeException().stackTrace.first().lineNumber - 1
                            }
                        }
                        describe("context 2") {
                            context2Line = RuntimeException().stackTrace.first().lineNumber - 1
                            it("test2") {
                                test2Line = RuntimeException().stackTrace.first().lineNumber - 1
                            }
                        }
                    }
                val contextInfo = coroutineScope {
                    ContextExecutor(ctx, this).execute()
                }

                it("returns file info for all subcontexts") {
                    expectThat(contextInfo.contexts).all {
                        get { stackTraceElement }.isNotNull().and {
                            get { fileName }.isEqualTo("ContextExecutorTest.kt")
                        }
                    }
                }
                it("returns line number for contexts") {
                    expectThat(contextInfo.contexts) {
                        get(0).get { stackTraceElement }.isNotNull().get { lineNumber }.isEqualTo(rootContextLine)
                        get(1).get { stackTraceElement }.isNotNull().get { lineNumber }.isEqualTo(context1Line)
                        get(2).get { stackTraceElement }.isNotNull().get { lineNumber }.isEqualTo(context2Line)
                    }
                }
                it("reports file name for all tests") {
                    expectThat(contextInfo.tests.keys).all {
                        get { stackTraceElement }.and {
                            get { fileName }.isEqualTo("ContextExecutorTest.kt")
                        }
                    }
                }
                it("reports line number for all tests") {
                    expectThat(contextInfo.tests.keys.toList()) {
                        get(0).get { stackTraceElement }.get { lineNumber }.isEqualTo(test1Line)
                        get(1).get { stackTraceElement }.get { lineNumber }.isEqualTo(test2Line)
                    }
                }

            }
            describe("supports lazy execution") {
                it("postpones test execution until the deferred is awaited when lazy is set to true") {
                    var testExecuted = false
                    val ctx =
                        RootContext("root context") {
                            test("test 1") {
                                testExecuted = true
                            }
                        }
                    coroutineScope {
                        val contextInfo =
                            ContextExecutor(ctx, this, lazy = true).execute()
                        expectThat(testExecuted).isEqualTo(false)
                        val deferred = contextInfo.tests.values.single()
                        expectThat(deferred.await().result).isA<Success>()
                        expectThat(testExecuted).isEqualTo(true)
                    }

                }
            }

            describe("handles failing contexts")
            {
                var error: Throwable? = null

                val ctx =
                    RootContext("root context") {
                        test("test 1") {}
                        test("test 2") {}
                        context("context 1") {
                            error = NotImplementedError("")
                            throw error!!
                        }
                        context("context 4") { test("test 4") {} }
                    }
                val results = coroutineScope {
                    ContextExecutor(ctx, this).execute()
                }
                it("reports a failing context as a failing test") {
                    expectThat(results.tests.values.awaitAll().filter { it.isFailed }).single().and {
                        get { test }.and {
                            get { testName }.isEqualTo("context 1")
                            get { container.name }.isEqualTo("root context")
                            get { stackTraceElement.toString() }.endsWith(
                                "ContextExecutorTest.kt:${
                                    getLineNumber(
                                        error
                                    ) - 1
                                })"
                            )
                        }
                    }
                }
                it("does not report a failing context as a context") {
                    expectThat(results.contexts).map { it.name }.doesNotContain("context 1")
                }
            }
            describe("detects duplicated tests")
            {
                it("fails with duplicate tests in one context") {
                    val ctx =
                        RootContext {
                            test("duplicate test name") {}
                            test("duplicate test name") {}
                        }
                    coroutineScope {
                        expectThrows<FailGoodException> {
                            ContextExecutor(
                                ctx,
                                this
                            ).execute()
                        }
                    }
                }
                it("does not fail when the tests with the same name are in different contexts") {
                    val ctx =
                        RootContext {
                            test("duplicate test name") {}
                            context("context") { test("duplicate test name") {} }
                        }
                    coroutineScope { ContextExecutor(ctx, this).execute() }
                }
            }
            describe("detects duplicate contexts") {
                it("fails with duplicate contexts in one context") {
                    val ctx =
                        RootContext {
                            context("duplicate test name") {}
                            context("duplicate test name") {}
                        }
                    coroutineScope {
                        expectThrows<FailGoodException> {
                            ContextExecutor(
                                ctx,
                                this
                            ).execute()
                        }
                    }
                }
                it("does not fail when the contexts with the same name are in different contexts") {
                    val ctx =
                        RootContext {
                            test("same context name") {}
                            context("context") { test("same context name") {} }
                        }
                    coroutineScope { ContextExecutor(ctx, this).execute() }
                }
                it("fails when a context has the same name as a test in the same contexts") {
                    val ctx =
                        RootContext {
                            test("same name") {}
                            context("same name") {}
                        }
                    coroutineScope {
                        expectThrows<FailGoodException> {
                            ContextExecutor(
                                ctx,
                                this
                            ).execute()
                        }
                    }
                }

            }
            describe("handles strange contexts correctly") {
                it("a context with only one pending test") {
                    val context = RootContext {
                        describe("context") {
                            pending("pending") {
                            }
                        }
                        test("test") {}

                    }
                    coroutineScope {
                        ContextExecutor(context, this).execute().tests.values.awaitAll()
                    }
                }
                test("tests can not contain nested contexts") {
                    //context("this should not even compile work") {}
                }
            }
        }

    private fun getLineNumber(runtimeException: Throwable?): Int =
        runtimeException!!.stackTrace.first().lineNumber
}
