package failfast

import failfast.docs.ObjectMultipleContextsTest
import strikt.api.expectThat
import strikt.assertions.*

fun main() {
    FailFast.runTest()
}

object ObjectContextProviderTest {
    val context =
        describe(ObjectContextProvider::class) {
            it("provides a context from an object in a java class (MyTest::class.java)") {
                expectThat(ObjectContextProvider(TestFinderTest::class.java).getContexts()).single()
                    .isA<RootContext>()
                    .and { get(RootContext::name).isEqualTo("test finder") }
            }
            it("provides a context from an object in a kotlin class (MyTest::class)") {
                expectThat(ObjectContextProvider(TestFinderTest::class).getContexts()).single()
                    .isA<RootContext>()
                    .and { get(RootContext::name).isEqualTo("test finder") }
            }
            it("provides a list of contexts from an object in a kotlin class (MyTest::class)") {
                expectThat(ObjectContextProvider(ObjectMultipleContextsTest::class).getContexts()).hasSize(2).all {
                    isA<RootContext>()
                }
            }
            it("provides a top level context from a kotlin class") {
                expectThat(ObjectContextProvider(ObjectContextProviderTest::class.java.classLoader.loadClass("failfast.docs.TestContextOnTopLevelTest").kotlin).getContexts()).single()
                    .isA<RootContext>()
                    .and { get(RootContext::name).isEqualTo("test context declared on top level") }
            }
        }
}
