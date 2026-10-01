import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.vanniktech.mavenPublish)
}

kotlin {
    android {
        namespace = "wang.harlon.chatbase"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        androidResources {
            enable = true
        }
        withHostTest {
            // Robolectric 内存 Room 库跑 DAO/迁移测试
            isIncludeAndroidResources = true
        }
    }

    // markdown 解析的 iOS 侧绑 cmark-gfm（apple/swift-cmark 源，tag 与产物见 native/build-cmark.sh）
    listOf(
        iosArm64() to "ios_arm64",
        iosSimulatorArm64() to "ios_simulator_arm64",
    ).forEach { (target, libDir) ->
        target.compilations.getByName("main").cinterops.create("cmarkgfm") {
            definitionFile.set(project.file("native/cmarkgfm.def"))
            includeDirs(project.file("native/out/$libDir/include"))
            extraOpts("-libraryPath", project.file("native/out/$libDir").absolutePath)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // ChatHost 的签名里有 Composable slot 与 HttpClientConfig，宿主实现它时要看得见这两个类型
            api(libs.compose.runtime)
            api(libs.ktor.client.core)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.icons.extended)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.jetbrains.navigationevent.compose)

            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.coil.compose)
            implementation(libs.highlights)

            implementation(libs.room.runtime)
            implementation(libs.okio)
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)

            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.exifinterface)
            implementation(libs.androidx.activity.compose)

            implementation(libs.commonmark)
            implementation(libs.commonmark.ext.gfm.tables)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.test.junit)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
        }
    }
}

compose.resources {
    // 固定资源类的包名，不跟工程名走
    packageOfResClass = "wang.harlon.chatbase.resources"
}

room {
    // schema 入库：迁移测试的权威参照
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

// cinterop 前先把 cmark-gfm 静态库编出来（已就绪则秒退，--force 见脚本）
val buildCmarkGfm by tasks.registering(Exec::class) {
    workingDir = projectDir
    commandLine("bash", "native/build-cmark.sh")
    inputs.file("native/build-cmark.sh")
    outputs.dir("native/out/ios_arm64")
    outputs.dir("native/out/ios_simulator_arm64")
}
tasks.matching { it.name.startsWith("cinteropCmarkgfm") }.configureEach {
    dependsOn(buildCmarkGfm)
}

mavenPublishing {
    publishToMavenCentral()
    // CI 注入 signingInMemoryKey 时启用签名；本地无密钥跳过
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates(groupId = "wang.harlon", artifactId = "chatbase-kmp")

    pom {
        name.set("chatbase-kmp")
        description.set("Compose Multiplatform chat UI and client for chatbase — streaming, model picker, voice, images and local history.")
        url.set("https://github.com/HarlonWang/chatbase-kmp")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("HarlonWang")
                name.set("HarlonWang")
                url.set("https://github.com/HarlonWang")
            }
        }
        scm {
            url.set("https://github.com/HarlonWang/chatbase-kmp")
            connection.set("scm:git:git://github.com/HarlonWang/chatbase-kmp.git")
            developerConnection.set("scm:git:ssh://git@github.com/HarlonWang/chatbase-kmp.git")
        }
    }
}
