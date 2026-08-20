# nbbrd-enforcer-rules

Custom [Maven Enforcer](https://maven.apache.org/enforcer/maven-enforcer-plugin/) rules used by NBBRD projects.

## Rules

| Rule                                    | Description                                                              |
|-----------------------------------------|-------------------------------------------------------------------------|
| [`requireJavadocJar`](#requirejavadocjar) | Ensures that a javadoc jar is attached for every non-pom artifact.      |

### requireJavadocJar

Maven Central requires a `-javadoc.jar` to be published for non-pom artifacts. This rule inspects the attached
artifacts of the project and fails when no artifact with the `javadoc` classifier is present. Projects with `pom`
packaging are always considered valid.

Projects that are not deployed are exempted, since Maven Central only requires a javadoc jar for published artifacts.
Deployment is considered skipped when the `maven.deploy.skip` property is `true` or when the `maven-deploy-plugin` is
configured with `<skip>true</skip>`.

Because it checks the *attached* artifacts, this rule should be bound to a phase that runs after the javadoc jar has
been attached (typically `verify`, once the `maven-javadoc-plugin:jar` goal has run).

## Setup

Add the rules as a dependency of the `maven-enforcer-plugin` and invoke the rule:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.github.nbbrd.nbbrd-maven-tools</groupId>
            <artifactId>nbbrd-enforcer-rules</artifactId>
            <version><!-- latest version --></version>
        </dependency>
    </dependencies>
    <executions>
        <execution>
            <id>enforce-javadoc-jar</id>
            <phase>verify</phase>
            <goals>
                <goal>enforce</goal>
            </goals>
            <configuration>
                <rules>
                    <requireJavadocJar/>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

