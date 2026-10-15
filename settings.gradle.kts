pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}
rootProject.name = "chatbase-kmp"
include(":library")
include(":sample")

// 本地源码 ↔ Maven 坐标双轨（与 TrendingAI / Tono 同一套）：在 local.properties 里写 `<库>.dir=<路径>` 即启用 composite build，
// 本地构建直接吃那个库的源码；CI 没有 local.properties，一律走 libs.versions.toml 的 Maven 版本。
// 「Maven 坐标 → included build 里的项目路径」由库自己声明在 `gradle/composite-substitutions`。
val localProperties = java.util.Properties().apply {
    val file = rootDir.resolve("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun localSourceDir(key: String): File? {
    val configured = localProperties.getProperty(key)
    if (configured.isNullOrBlank()) return null
    val dir = File(configured).takeIf { it.isAbsolute } ?: rootDir.resolve(configured)
    require(dir.exists()) { "local.properties 配置的 $key 不存在: $dir" }
    logger.lifecycle("$key: 本地源码 $dir（CI 用 libs.versions.toml 的 Maven 版本；改库不发版，CI 不会跟着变）")
    return dir
}

fun compositeSubstitutions(dir: File): Map<String, String> {
    val file = dir.resolve("gradle/composite-substitutions")
    require(file.exists()) { "$dir 缺少 gradle/composite-substitutions（每行 `<group>:<artifactId> = <项目路径>`）" }
    return file.readLines()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .associate { line ->
            val parts = line.split('=', limit = 2).map(String::trim)
            require(parts.size == 2 && parts.none(String::isEmpty)) { "$file 这行不是 `坐标 = 项目路径`: $line" }
            parts[0] to parts[1]
        }
        .also { require(it.isNotEmpty()) { "$file 是空的" } }
}

val toolchainDirKeys = setOf("sdk.dir", "ndk.dir", "cmake.dir")

localProperties.stringPropertyNames()
    .filter { it.endsWith(".dir") && it !in toolchainDirKeys }
    .sorted()
    .forEach { key ->
        val dir = localSourceDir(key) ?: return@forEach
        includeBuild(dir) {
            dependencySubstitution {
                compositeSubstitutions(dir).forEach { (coordinate, projectPath) ->
                    substitute(module(coordinate)).using(project(projectPath))
                }
            }
        }
    }
