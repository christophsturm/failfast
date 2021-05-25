package failgood.problematic

import failgood.describe
import org.junit.platform.commons.annotation.Testable

// this is confusing and should not compile:
@Testable
class RootLevelDescribe {
    val context = describe("root") {
/*        describe(RootLevelDescribe::class) {
            it("should not compile") {

            }
        }*/
    }
}
