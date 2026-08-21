import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("org.jetbrains.intellij.platform.grammarkit") version "2.18.1"
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
// Lexer, generated at build time from Cnd.flex.
//
// CndLexer.java used to be generated from the IDE and committed: 61 KB of JFlex
// output sitting in src/, which made every lexer change conditional on somebody
// having JFlex wired into their IDE, and gave no guarantee that the committed
// file still matched the .flex beside it.
//
// The generator moved from JFlex 1.7.0 to 1.10.17, which rewrites the character
// class tables completely -- roughly 1900 lines of diff. The token stream is
// unchanged: the six lexer baselines and the sixteen parsing baselines from the
// golden tests all pass without a single re-record. That check is the only
// reason this was safe to do, and it is why it could not be done before those
// tests existed.
//
// The parser is NOT generated here, and gen/ stays committed. Not an oversight:
// Cnd.bnf declares psiImplUtilClass=CndPsiImplUtil, and Grammar-Kit resolves
// that class by reflection over its classpath. Standalone it has no compiled
// CndPsiImplUtil to look at, so it silently drops all 66 delegating methods --
// and CndPsiImplUtil cannot be compiled first, because its own bodies call
// those very methods on the generated interfaces. The cycle is real, and
// breaking it means moving the implementations into the hand-written mixin
// classes, which is a refactor of behaviour, not of the build. Tracked in #19.
// ---------------------------------------------------------------------------
val generatedLexerDir: Provider<Directory> = layout.buildDirectory.dir("generated/lexer")

tasks.generateLexer {
    sourceFile = layout.projectDirectory.file("src/fr/tolc/jahia/intellij/plugin/cnd/Cnd.flex")
    targetRootOutputDir = generatedLexerDir
    packageName = "fr.tolc.jahia.intellij.plugin.cnd"
    // Required for purgeOldFiles: without it the task cannot tell which file it owns.
    pathToClass = "fr/tolc/jahia/intellij/plugin/cnd/CndLexer.java"
    purgeOldFiles = true
}

tasks.compileJava {
    dependsOn(tasks.generateLexer)
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
        java.setSrcDirs(listOf("src", "gen", generatedLexerDir))
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

// ---------------------------------------------------------------------------
// changeNotes, taken from CHANGELOG.md rather than maintained twice.
//
// Deliberately hand-rolled instead of adding org.jetbrains.changelog: the
// history in this file predates Keep a Changelog and does not follow it, and a
// plugin that wants to own the whole file would either reformat that history or
// refuse it. This only needs to read one section, and it fails the build if
// that section is missing -- which is the failure worth having, since a release
// whose notes silently came out empty is the thing to avoid.
// ---------------------------------------------------------------------------
fun renderChangeNotes(changelog: File, version: String): String {
    val lines = changelog.readLines()
    val start = lines.indexOfFirst { it.startsWith("## [$version]") }
    require(start >= 0) { "No '## [$version]' section in ${changelog.name}" }
    val rest = lines.drop(start + 1)
    val end = rest.indexOfFirst { it.startsWith("## [") }
    val body = if (end >= 0) rest.take(end) else rest

    fun inline(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace(Regex("""\*\*(.+?)\*\*""")) { "<b>" + it.groupValues[1] + "</b>" }
        .replace(Regex("""`(.+?)`""")) { "<code>" + it.groupValues[1] + "</code>" }

    val html = StringBuilder()
    val bullets = mutableListOf<String>()
    val paragraph = mutableListOf<String>()

    fun flushBullets() {
        if (bullets.isEmpty()) return
        html.append("<ul>")
        bullets.forEach { html.append("<li>").append(inline(it)).append("</li>") }
        html.append("</ul>")
        bullets.clear()
    }

    fun flushParagraph() {
        if (paragraph.isEmpty()) return
        html.append("<p>").append(inline(paragraph.joinToString(" "))).append("</p>")
        paragraph.clear()
    }

    body.map { it.trimEnd() }.forEach { line ->
        when {
            line.isBlank() -> { flushBullets(); flushParagraph() }
            line.startsWith("### ") -> {
                flushBullets(); flushParagraph()
                html.append("<h4>").append(inline(line.removePrefix("### "))).append("</h4>")
            }
            line.startsWith("- ") -> { flushParagraph(); bullets.add(line.removePrefix("- ")) }
            // A wrapped bullet: continuation lines are indented under their dash.
            line.startsWith("  ") && bullets.isNotEmpty() -> bullets[bullets.lastIndex] += " " + line.trim()
            else -> { flushBullets(); paragraph.add(line.trim()) }
        }
    }
    flushBullets()
    flushParagraph()

    require(html.isNotEmpty()) { "The '## [$version]' section of ${changelog.name} is empty" }
    return html.toString()
}

intellijPlatform {
    instrumentCode = true // GUI Designer .form binding + @NotNull assertions
    buildSearchableOptions = false // the plugin contributes no settings UI

    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
        description = providers.fileContents(
            layout.projectDirectory.file("plugin-description.html"),
        ).asText
        changeNotes = providers.gradleProperty("pluginVersion").map {
            renderChangeNotes(layout.projectDirectory.file("CHANGELOG.md").asFile, it)
        }
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

