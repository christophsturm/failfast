package failfast.internal

import failfast.*
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.*

object ContextExecutorTest {
    val context =
        describe(ContextExecutor::class) {
            describe("with a valid root context") {
                val ctx =
                    RootContext("root context") {
                        test("test 1") {}
                        test("test 2") {}
                        context("context 1") { context("context 2") { test("test 3") {} } }
                        context("context 4") { test("test 4") {} }
                    }

                val rootContext = Context("root context", null)
                val context1 = Context("context 1", rootContext)
                val context2 = Context("context 2", context1)
                val context4 = Context("context 4", rootContext)
                it("returns tests") {
                    coroutineScope {
                        val contextInfo = ContextExecutor(ctx, this).execute()
                        expectThat(contextInfo.tests.keys)
                            .containsExactlyInAnyOrder(
                                TestDescriptor(rootContext, "test 1"),
                                TestDescriptor(rootContext, "test 2"),
                                TestDescriptor(context2, "test 3"),
                                TestDescriptor(context4, "test 4")
                            )
                    }
                }
                it("returns deferred test results") {
                    coroutineScope {
                        val contextInfo = ContextExecutor(ctx, this).execute()
                        val testResults = contextInfo.tests.values.awaitAll()
                        expectThat(testResults).all { isA<Success>() }
                        expectThat(testResults.map { it.test })
                            .containsExactlyInAnyOrder(
                                TestDescriptor(rootContext, "test 1"),
                                TestDescriptor(rootContext, "test 2"),
                                TestDescriptor(context2, "test 3"),
                                TestDescriptor(context4, "test 4")
                            )
                    }
                }

                it("returns contexts") {
                    coroutineScope {
                        val contextInfo = ContextExecutor(ctx, this).execute()
                        expectThat(contextInfo.contexts)
                            .containsExactlyInAnyOrder(rootContext, context1, context2, context4)
                    }
                }
                it("measures time") {
                    coroutineScope {
                        val results = ContextExecutor(ctx, this).execute()

                        expectThat(results.tests.values.awaitAll())
                            .all { isA<Success>().get { timeMicro }.isGreaterThan(1) }
                    }
                }
                itWill("report file name and line number to all tests")
                describe("supports lazy execution") { itWill("find tests without executing them") {} }
            }

            describe("detects duplicated tests") {
                it("fails with duplicate tests in one context") {
                    val ctx =
                        RootContext {
                            test("duplicate test name") {}
                            test("duplicate test name") {}
                        }
                    coroutineScope {
                        expectThrows<FailFastException> { ContextExecutor(ctx, this).execute() }
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
            it("can close resources") {
                val events = mutableListOf<String>()
                var closeCalled = false
                val closable = AutoCloseable { closeCalled = true }
                var resource: AutoCloseable? = null
                Suite {
                    resource = autoClose(closable) {
                        it.close()
                        events.add("close callback")
                    }
                    test("a test") { events.add("test") }
                }.run()
                expectThat(events).containsExactly("test", "close callback")
                expectThat(resource).isEqualTo(closable)
                expectThat(closeCalled).isTrue()
            }
        }
}
