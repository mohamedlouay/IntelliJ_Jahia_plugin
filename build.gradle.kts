import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(
            providers.gradleProperty("platformType"),
            providers.gradleProperty("platformVersion"),
        )
        bundledPlugins(
            providers.gradleProperty("platformBundledPlugins").map { it.split(',').map(String::trim) },
        )
        pluginVerifier()
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(providers.gradleProperty("javaVersion").get().toInt())
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = providers.gradleProperty("javaVersion").get().toInt()
    options.encoding = "UTF-8"
}

// ---------------------------------------------------------------------------
// Layout mapping: this project predates the Maven convention. Nothing moves.
//
//   src/  -> Java source root AND resource root (39 icon PNGs live inside the
//            package tree, plus 2 GUI Designer .form files)
//   gen/  -> second Java source root (Grammar-Kit output, committed)
//   resources/ -> plugin.xml, colorSchemes/, default/, jahia/
//
// WARNING: do NOT exclude "**/*.form" from the resources set. Gradle's java
// SourceDirectorySet hard-codes an "**/*.java" include filter, so .form files
// can only reach `sourceSets.main.allSource` -- which is how instrumentCode
// discovers them -- via the resources set. Excluding them makes instrumentation
// silently do nothing, and CreateCndFileDialog.contentPane is then null at
// runtime: setContentPane(null) -> NPE on "New > CND File", with no compile
// error and no build warning.
// ---------------------------------------------------------------------------
sourceSets {
    main {
        java.setSrcDirs(listOf("src", "gen"))
        resources.setSrcDirs(listOf("resources", "src"))
        resources.exclude(
            "**/*.java",
            "**/*.bnf",
            "**/*.flex",
            "**/icons/img/old/**", // 4 dead PNGs, zero references
        )
    }
    test {
        java.setSrcDirs(listOf("test"))
        resources.setSrcDirs(listOf("testResources"))
    }
}

// ---------------------------------------------------------------------------
// Manifest of the bundled Jahia .cnd files, read at runtime by JahiaBundledCndService
// to know what to extract. Generated rather than discovered by walking jar entries at
// runtime: enumerating a jar depends on how the plugin happens to be installed, whereas a
// generated list is deterministic and fails loudly at build time when it comes out empty.
// ---------------------------------------------------------------------------
val bundledCndFolder = layout.projectDirectory.dir("resources/jahia")

val generateCndIndex = tasks.register("generateCndIndex") {
    val source = bundledCndFolder
    val target = layout.buildDirectory.file("generated/bundled-cnd/index.txt")
    inputs.dir(source).withPropertyName("bundledCndFiles")
    outputs.file(target).withPropertyName("cndIndex")

    doLast {
        val names = source.asFile.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".cnd") }
            .map { it.name }
            .sorted()
        require(names.isNotEmpty()) { "No .cnd file found in ${source.asFile}" }
        target.get().asFile.apply {
            parentFile.mkdirs()
            writeText(names.joinToString(separator = "\n", postfix = "\n"))
        }
    }
}

tasks.processResources {
    from(generateCndIndex) { into("jahia") }
}

intellijPlatform {
    instrumentCode = true // GUI Designer .form binding + @NotNull assertions
    buildSearchableOptions = false // the plugin contributes no settings UI

    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
        description = providers.fileContents(
            layout.projectDirectory.file("plugin-description.html"),
        ).asText
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")

            // No upper bound, which is the JetBrains recommendation for 2024.3+ (build 243+) and
            // what verifyPluginProjectConfiguration asks for on every build.
            //
            // It is also what makes the weekly compatibility job worth running. ides { recommended() }
            // is not merely filtered by this range, it is derived from it: measured with
            // printProductsReleases, untilBuild = 252.* selected IU-2025.2 and IU-2025.1 -- two IDEs
            // the ordinary build already covers -- while an open range selects IU-2026.2, IU-2026.1
            // and IU-2025.3 as well. A ceiling here does not make the job fail; it makes it pass
            // while checking nothing new, which is worse, because it is silent.
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
        )

        // The Marketplace rejects plugin IDs containing "intellij" (a leftover from the
        // template id com.your.company.unique.plugin.id). This id has been published
        // since 2016; renaming it would orphan existing installations and settings.
        // Muting is the remedy JetBrains documents for already-published ids.
        // Without this the verifier reports INVALID_PLUGIN and schedules 0 verifications,
        // so no compatibility check runs at all.
        freeArgs = listOf("-mute", "TemplateWordInPluginId")

        // -PverifyRecommended switches to the latest releases and EAPs, which is what the weekly
        // compatibility workflow runs. Every other build checks the one version this plugin
        // compiles against, so a normal push is not held hostage to an EAP breaking overnight.
        ides {
            if (providers.gradleProperty("verifyRecommended").isPresent) {
                recommended()
            } else if (providers.gradleProperty("verifyIde").isPresent) {
                create(IntelliJPlatformType.IntellijIdeaUltimate, providers.gradleProperty("verifyIde").get())
            } else {
                create(
                    IntelliJPlatformType.IntellijIdeaUltimate,
                    providers.gradleProperty("platformVersion").get(),
                )
            }
        }
    }
}

tasks.test {
    useJUnit()
}
