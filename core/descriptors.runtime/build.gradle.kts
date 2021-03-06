import org.jetbrains.kotlin.gradle.tasks.UsesKotlinJavaToolchain

plugins {
    kotlin("jvm")
    id("jps-compatible")
}

// Only compilation tasks should use JDK 1.6
project.disableDeprecatedJvmTargetWarning()
tasks
    .matching { it.name == "compileKotlin" && it is UsesKotlinJavaToolchain }
    .configureEach {
        (this as UsesKotlinJavaToolchain).kotlinJavaToolchain.toolchain.use(project.getToolchainLauncherFor(JdkMajorVersion.JDK_1_6))
    }

tasks
    .matching { it.name == "compileJava" && it is JavaCompile }
    .configureEach {
        (this as JavaCompile).javaCompiler.set(project.getToolchainCompilerFor(JdkMajorVersion.JDK_1_6))
    }

dependencies {
    compileOnly(project(":core:util.runtime"))
    compileOnly(project(":core:descriptors"))
    compileOnly(project(":core:descriptors.jvm"))

    testCompile(projectTests(":compiler:tests-common"))
    testCompile(projectTests(":generators:test-generator"))

    testRuntimeOnly(intellijCoreDep()) { includeJars("intellij-core") }
    testRuntimeOnly(intellijDep()) { includeJars("platform-concurrency") }
    testRuntimeOnly(jpsStandalone()) { includeJars("jps-model") }
}

sourceSets {
    "main" { projectDefault() }
    "test" { projectDefault() }
}

val generateTests by generator("org.jetbrains.kotlin.generators.tests.GenerateRuntimeDescriptorTestsKt")

projectTest(parallel = true) {
    dependsOn(":dist")
    workingDir = rootDir
}

testsJar()
