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
            untilBuild = providers.gradleProperty("pluginUntilBuild")
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
        ides {
            create(
                IntelliJPlatformType.IntellijIdeaUltimate,
                providers.gradleProperty("platformVersion").get(),
            )
        }
    }
}

tasks.test {
    useJUnit()
}
