import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.jfrog.bintray.gradle.BintrayExtension
import info.solidsoft.gradle.pitest.PitestPluginExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "1.4.21-2"
    id("com.github.ben-manes.versions") version "0.36.0"
    `maven-publish`
    id("com.jfrog.bintray") version "1.8.5"
    id("info.solidsoft.pitest") version "1.5.2"
    id("tech.formatter-kt.formatter") version "0.6.15"
}

group = "failfast"
version = "0.1"

val coroutinesVersion = "1.4.2"

repositories {
    mavenCentral()
    jcenter()
}
dependencies {
    implementation(enforcedPlatform("org.jetbrains.kotlin:kotlin-bom:1.4.21-2"))
    implementation(kotlin("stdlib-jdk8"))
    compileOnly("org.pitest:pitest:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    testImplementation("io.strikt:strikt-core:0.28.1")
    testImplementation("org.pitest:pitest:1.6.2")
}

tasks {
    create<Jar>("sourceJar") {
        from(sourceSets.main.get().allSource)
        archiveClassifier.set("sources")
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

tasks.withType<KotlinCompile> { kotlinOptions.jvmTarget = "1.8" }

artifacts {
    add("archives", tasks["jar"])
    add("archives", tasks["sourceJar"])
}
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(tasks["sourceJar"])
            groupId = project.group as String
            artifactId = "failfast"
            version = project.version as String
        }
    }
}
// BINTRAY_API_KEY= ... ./gradlew clean build publish bintrayUpload
bintray {
    user = "christophsturm"
    key = System.getenv("BINTRAY_API_KEY")
    publish = true
    setPublications("mavenJava")
    pkg(
        delegateClosureOf<BintrayExtension.PackageConfig> {
            repo = "maven"
            name = "failfast"
            version(
                delegateClosureOf<BintrayExtension.VersionConfig> {
                    name = project.version as String
                }
            )
        }
    )
}


tasks.wrapper { distributionType = Wrapper.DistributionType.ALL }

val testMain =
    task("testMain", JavaExec::class) {
        main = "failfast.FailFastBootstrapKt"
        classpath = sourceSets["test"].runtimeClasspath
    }
task("autotest", JavaExec::class) {
    main = "failfast.AutoTestMainKt"
    classpath = sourceSets["test"].runtimeClasspath
}

tasks.check { dependsOn(testMain) }

plugins.withId("info.solidsoft.pitest") {
    configure<PitestPluginExtension> {
        //        verbose.set(true)
        jvmArgs.set(listOf("-Xmx512m"))
        //        testPlugin.set("junit5")
        testPlugin.set("failfast")
        avoidCallsTo.set(setOf("kotlin.jvm.internal", "kotlin.Result"))
        targetClasses.set(setOf("failfast.*")) //by default "${project.group}.*"
        targetTests.set(setOf("failfast.*Test", "failfast.**.*Test"))
        pitestVersion.set("1.6.2")
        threads.set(
            System.getenv("PITEST_THREADS")?.toInt() ?: Runtime.getRuntime().availableProcessors()
        )
        outputFormats.set(setOf("XML", "HTML"))
    }
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
    val filtered =
        listOf("alpha", "beta", "rc", "cr", "m", "preview", "dev", "eap")
            .map { qualifier -> Regex("(?i).*[.-]$qualifier[.\\d-]*.*") }
    resolutionStrategy {
        componentSelection {
            all {
                if (filtered.any { it.matches(candidate.version) }) {
                    reject("Release candidate")
                }
            }
        }
        // optional parameters
        checkForGradleUpdate = true
        outputFormatter = "json"
        outputDir = "build/dependencyUpdates"
        reportfileName = "report"
    }
}
