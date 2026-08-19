# Jahia completion library

Three stub interfaces whose only purpose is to give the IDE something to resolve when it types
the implicit EL variable `currentNode` in a Jahia JSP view.

| Interface | Stands in for |
|---|---|
| `JCRNodeWrapperMod` | `org.jahia.services.content.JCRNodeWrapper` |
| `JCRValueWrapperMod` | `org.jahia.services.content.JCRValueWrapper` |
| `LazyPropertyIteratorMod` | `org.jahia.services.content.LazyPropertyIterator` |

`CndJspElVariablesProvider` declares `currentNode` as a `JCRNodeWrapperMod`, which is what makes
`${currentNode.properties['...']}` and `<c:forEach>` over sub-nodes complete in a JSP. The `Mod`
suffix and the separate package exist so these never collide with the real Jahia classes when a
project happens to have them on its classpath.

## Why the jars are committed

`resources/jahia/jahia-plugin-completion-library.jar` and its `-sources.jar` are committed
artifacts, **not** built by the Gradle build. They total about 6 KB, they have not changed in
years, and building them requires reaching `https://devtools.jahia.com/nexus`, an external Nexus
that has no business being a dependency of the plugin build.

They are extracted at runtime to the IDE system directory and served as a library by
`JahiaBundledCndRootsProvider`.

## Regenerating them

Only needed if one of the three interfaces changes.

```bash
cd completion-jar
mvn clean package
cp target/jahia-plugin-completion-library.jar         ../resources/jahia/
cp target/jahia-plugin-completion-library-sources.jar ../resources/jahia/
```

Then rebuild the plugin and commit both jars.

Note that `mvn package` resolves the parent POM `org.jahia.modules:jahia-modules:8.1.3.1` from
the Jahia Nexus declared in `pom.xml`. Off that network, the build fails; the committed jars are
what makes that irrelevant day to day.
