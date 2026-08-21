# Changelog

All notable changes to this plugin.

## [Unreleased]

## [3.0.0]

Revival release. The plugin had not been touched since February 2023 and no
longer loaded on a current IDE: the platform had removed `WriteCommandAction.Simple`,
dropped commons-lang 2 from the classpath and made `RegisterToolWindowTask` internal,
and nothing had ever reported it. Everything below follows from bringing it back
(#1).

### Requirements

- (**IMPORTANT**) Requires **IntelliJ IDEA 2025.1 or later**. There is no upper
  bound, so the plugin stays installable on newer IDEs rather than being
  disabled by an update.
- (**IMPORTANT**) The bundled **Properties** plugin is now a required
  dependency. It was effectively required before as well -- non-optional code
  linked against it -- but declaring it optional produced a
  `NoClassDefFoundError` instead of an honest "missing dependency" message.
- IntelliJ IDEA **Ultimate** remains necessary for the JSP and EL features
  (completion, references, annotations inside JSP). They degrade quietly in
  Community, as before.

### Fixed

- (**IMPORTANT FIX**) The custom gutter icon -- a PNG dropped in `icons/` and
  named after a node type -- **had never worked on Windows** since it was
  announced in 2.0.0. The path was passed as `"file:/" + path`, which does not
  form a valid URL for a Windows path.
- (**IMPORTANT FIX**) On Linux and macOS, views and templates were **never
  found**: the resource lookup appended a hard-coded `\` to a path whose
  separators had already been normalised to `/`, so every lookup came back
  empty. No error, no log line.
- Files created by "New -> CND File" and "Create new view" now appear
  **immediately**, with the caret placed. They used to be written behind the
  IDE's back and stayed invisible until the next refresh.
- Renaming a module's Jahia work folder is picked up **without restarting the
  IDE**. The path was cached in a static map keyed by project, which also leaked
  every project ever opened.
- Opening a `.cnd` file no longer triggers `Slow operations are prohibited` on
  the UI thread.

### Changed

- The bundled node type definitions (44 `.cnd` files) and the JSP completion
  jars are now served as **libraries the IDE owns**, instead of being written
  into the plugin installation folder and attached to each module as a library.
  Consequences: your `.iml` and `workspace.xml` are **no longer modified**, no
  `jahia-plugin-cnds.jar` is written anywhere, and opening a non-Jahia project
  costs nothing.
- The plugin now installs correctly as a **single jar**. Templates are read from
  the jar rather than resolved as paths inside an exploded installation
  directory.

### Removed

- (**IMPORTANT**) The **"Jahia" tool window is gone**. It duplicated the Project
  view, its content was never refreshed after the first build, and the platform
  API it was built on became internal. Node type navigation is unaffected:
  Ctrl+click, Find Usages, the structure view and the gutter markers all work as
  before.
- The "Rename" handler on views has been removed, so **Shift+F6 on a view now
  uses the IDE's own rename**, which actually renames the file. The custom
  handler had become a no-op.

### Known issue

- On **2026.2 EAP**, JSP EL completion for the Jahia implicit variables
  (`currentNode`, `renderContext`, `url`, ...) does not work: the platform
  package `com.intellij.jsp.javaee` was removed. No released IDE is affected --
  2025.1, 2025.2, 2025.3 and 2026.1 are all verified compatible. Tracked in #17.

### Internal

- Build rebuilt on Gradle 9 with the IntelliJ Platform Gradle Plugin 2.x; the
  plugin compiles from a fresh clone again.
- 33 golden tests over the CND grammar: the full PSI tree of 16 real definition
  files, the token stream of 6 lexer states, and the resource path handling.
- Two GitHub Actions workflows: one per push, and a weekly run of the Plugin
  Verifier against the latest releases and EAPs -- the thing whose absence let
  this plugin die quietly the first time.
- The lexer is generated from `Cnd.flex` at build time instead of being
  committed.

## [2.2.0]

- (**feature**) Better syntax highlighting of node properties (#69)
- (**feature**) Line marker for property overrides (#70)
- (**IMPORTANT FIX**) Fixed plugin jars dependencies not being correctly added to jahia modules anymore, thus preventing CND and JSP completions from working properly on newly created modules
- Fixed grammar issue with escaped quotes in property constraint (#64)

## [2.1.1]

- (**IMPORTANT FIX**) Refactored code for IntelliJ 2022.2 API (#65)
- Fixed grammar issue with escaped quotes in property default value (#64)

## [2.1.0]

- (**IMPORTANT FIX**) Refactored tool window for IntelliJ 2022.1 API (#61)
- (**IMPORTANT FIX**) Fixed JSP EL variables completion and references (apparently not working since official JSP plugin has been separated into JavaEE + JSP plugins)
- Fixed grammar-breaking issue with property default value not being recognized as such when containing ")" character between simple or double quotes (#62)
- Fixed stack-overflow error that could occur when getting all properties of a nodetype recursively (#60)
- Fixed template:include view completion and references not including extends views (#54)
- Fixed template:addResources completion and references (#52)
- Added several CND files for nodetypes completion purposes (#55)
- Way better nodetypes options and properties attributes grammar and syntax validation
- Better syntax highlighting

## [2.0.6]

- (**IMPORTANT FIX**) Refactored code for IntelliJ 2021.3 API (#58)

## [2.0.5]

- (**IMPORTANT FIX**) Refactored code for IntelliJ 2021.2 API
- Fixed issue with namespaces declarations without linebreak in CND file
- Fixed CND file auto formatting between right parenthesis and equal

## [2.0.4]

- (**IMPORTANT FIX**) Fixed forced reindexing of Project, Project's JDK, and Local Maven repo happening in IntelliJ 2020.1 (#53)

## [2.0.3]

- (**IMPORTANT FIX**) Fixed plugin breaking issue with IntelliJ 2019.3 (coming from a malformed cnd files jar)

## [2.0.2]

- Fixed issue with 'resourceBundle' property default value
- Fixed issue with empty string as property default value

## [2.0.1]

- (**feature**) `<template:addResources/>` completion, references, and line markers (#41)
- Fixed CND formatting adding spaces between property type and default value (#46)
- Fixed issue with choicelist options being allowed to contain + and - even when not between quotes (#47)
- Fixed ${currentNode} checks not being trimmed (#48)
- Fixed views not being identified as views if nodetype name contains underscores (#49)
- Fixed 'currentNode' property access being flagged as a non 'currentNode' property access when in a JSTL function (#50)
- Fixed issue with property multiple default value (#51)
- Added various missing completion values and default cache properties options

## [2.0.0]

- (**IMPORTANT FEATURE**) Properties completion in JSP (both in ${currentNode.properties} and `<jcr:nodeProperty/>` expressions)
- (**feature**) Jahia Tool Window (showing mixins/nodetypes, Jahia actions, and Jahia filters trees)
- (**feature**) Views virtual folder (#40)
- (**feature**) Icons for Jahia technical folders (css, javascript, icons, errors, img)
- (**feature**) Custom icons (in 'icons' folder) displayed in the gutter in CND files next to their corresponding nodetype/mixin
- (**enhancement**) Better nodetypes reference and completion in JSP: nodetypes are now recognized in EL expressions and tag text
- (**enhancement**) `<template:option/>` 'view' attribute completion, reference, and line marker (#38)
- (**enhancement**) Better base Jahia modules .cnd loading (no need to restart project anymore)
- (**enhancement**) 'Create new view' helper when right clicking on a nodetype in a .cnd file or on a nodetype directory/subdirectory in the project explorer
- (**enhancement**) 'Create new CND file' helper when right clicking on 'META-INF' directory/subdirectory in the project explorer
- Fixed issue with spaces not being allowed in property default values delimited by double quotes (#43)
- Fixed CND files containing only namespaces flagged as invalid (#37)

## [1.3.3]

- (**IMPORTANT FIX**) Fixed several issues happening when current IntelliJ project contains several Jahia modules
- (**IMPORTANT FIX**) Fixed several issues happening when several Jahia IntelliJ projects are opened at the same time

## [1.3.2]

- JSP features are now optional, allowing the plugin to work under IntelliJ Community Edition (#34)

## [1.3.1]

- (**feature**) `<template:include/>` 'view' attribute completion and reference is now based on node types hierarchy (because a node can access its ancestors views)
- (**feature**) `<template:module/>` 'view' attribute completion and reference (both are affected by 'templateType' attribute if provided)
- (**feature**) `<template:module/>` and `<template:include/>` line markers
- Fixed missing accessor in generated JSP code for 'multiple' properties (#30)
- Fixed duplicated view files in 'view folder' when view names start the same (#31)

## [1.3]

- (**feature**) `<template:include/>` 'view' attribute completion and reference (both are affected by 'templateType' attribute if provided)
- (**feature**) 'Create new view' helper now generates `<i>`c:forEach loops for 'multiple' properties and '+ *' subnodes
- Jahia base .cnd files jar library path is now force-refreshed on project opening
- Fixed grammar issue with property attributes recognized as part of default value when default value and choicelist constraint or one of the attributes both contain simple quote

## [1.2.1]

- Fixed completion for IntelliJ 14 (#20)
- Fixed abusive errors in Java, XML and properties files (#21)
- Fixed exception happening sometimes when searching for Jahia work folder (#22)

## [1.2]

- (**IMPORTANT FIX**) Fixed weird huge CPU/Memory consumption when calculating 'virtual' folders for views after editing a file with its view folder opened in Project view
- (**feature**) Jahia JSP variables completion (currentNode, moduleMap, etc... without having to use elvariables)
- (**feature**) Nodetype folders icon in Project View
- Line breaks within properties and subnodes now accepted
- Removed abusive 'Unresolved CND namespace' annotations on strings containing ':' in Java and XML (#19)

## [1.1.1]

- Fixed HUGE issue with the way library jar containing Jahia base .cnd files was generated

## [1.1]

- (**feature**) Completion and other features on Jahia nodetypes (embedded jahia base and main module .cnd files)
- (**feature**) Nodetypes translations properties are no longer flagged as unused in .properties files
- Fixed platform dependent (Windows/Linux/Mac) issue with files and folders paths
- Fixed issue with property type mask option containing '.' not being recognized (for instance 'ckeditor.customConfig')
- Fixed issue with namespace URIs containing '-' being flagged as non-valid URI
