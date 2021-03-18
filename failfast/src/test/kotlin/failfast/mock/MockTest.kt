package failfast.mock

import failfast.FailFast
import failfast.describe
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isEqualTo
import kotlin.reflect.jvm.javaMethod

fun main() {
    FailFast.runTest()
}

object MockTest {
    interface IImpl {
        fun method()
        fun method2()
        fun methodWithParameters(number: Int, name: String)
        suspend fun suspendMethod(number: Int, name: String)
        fun stringReturningFunction(): String
    }

    val context = describe("the mocking framework") {
        val mock = mock<IImpl>()
        describe("can record method calls") {
            mock.method()
            it("verifies method calls") {
                verify(mock) { method() }

            }
            it("throws when a verified method was not called") {
                expectThrows<MockException> {
                    verify(mock) { method2() }
                }
//                expectThat(getCalls(mock)).isEqualTo(listOf(MethodCall(IImpl::method.javaMethod!!, listOf())))
            }
        }
        it("records method parameters") {
            mock.methodWithParameters(10, "string")
            expectThat(getCalls(mock)).isEqualTo(
                listOf(
                    MethodCall(
                        IImpl::methodWithParameters.javaMethod!!,
                        listOf(10, "string")
                    )
                )
            )
        }
        it("records suspend method calls") {
            mock.suspendMethod(10, "string")
            expectThat(getCalls(mock)).isEqualTo(
                listOf(
                    MethodCall(
                        IImpl::suspendMethod.javaMethod!!,
                        listOf(10, "string")
                    )
                )
            )
        }
        it("defines result via calling the mock") {
            whenever(mock) { stringReturningFunction() }.thenReturn("resultString")
            expectThat(mock.stringReturningFunction()).isEqualTo("resultString")
        }
    }


}



