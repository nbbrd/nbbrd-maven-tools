# nbbrd-enforcer-rules

Custom [Maven Enforcer](https://maven.apache.org/enforcer/maven-enforcer-plugin/) rules used by NBBRD projects.

## Rules

| Rule                                    | Description                                                              |
|-----------------------------------------|--------------------------------------------------------------------------|
| [`requireJavadocJar`](#requirejavadocjar) | Ensures that a javadoc jar is attached for every artifact that requires one. |
| [`requireArtifactIdPattern`](#requireartifactidpattern) | Ensures that the artifactId matches a regex, optionally restricted to given packagings. |

### requireJavadocJar

Maven Central requires a `-javadoc.jar` to be published for non-pom artifacts. This rule inspects the attached
artifacts of the project and fails when no artifact with the `javadoc` classifier is present.

Some packagings never produce a javadoc jar and are therefore exempted by default (`pom` and `maven-archetype`). This
list can be overridden through the `exemptedPackagings` parameter to handle other cases.

Projects that are not deployed are exempted, since Maven Central only requires a javadoc jar for published artifacts.
Deployment is considered skipped when the `maven.deploy.skip` property is `true` or when the `maven-deploy-plugin` is
configured with `<skip>true</skip>`.

Because it checks the *attached* artifacts, this rule should be bound to a phase that runs after the javadoc jar has
been attached (typically `verify`, once the `maven-javadoc-plugin:jar` goal has run).

| Parameter            | Required | Description                                                                                     |
|----------------------|----------|-------------------------------------------------------------------------------------------------|
| `exemptedPackagings` | no       | Packagings that do not require a javadoc jar. Defaults to `pom` and `maven-archetype` when unset. |

For example, to also exempt the `nbm` packaging:

```xml
<requireJavadocJar>
    <exemptedPackagings>
        <exemptedPackaging>pom</exemptedPackaging>
        <exemptedPackaging>maven-archetype</exemptedPackaging>
        <exemptedPackaging>nbm</exemptedPackaging>
    </exemptedPackagings>
</requireJavadocJar>
```


### requireArtifactIdPattern

Ensures that the project `artifactId` fully matches a regular expression. The check can be restricted to specific
packagings: when `packagings` is omitted (or empty), the rule applies to every packaging; otherwise it only applies
when the project packaging is one of the listed values (other packagings pass silently).

| Parameter     | Required | Description                                                                       |
|---------------|----------|-----------------------------------------------------------------------------------|
| `pattern`     | yes      | Regular expression the `artifactId` must fully match.                             |
| `packagings`  | no       | Packagings the rule applies to. When empty, the rule applies to every packaging. |
| `message`     | no       | Custom failure message. A default message is generated otherwise.                |

For example, to enforce that every `nbm` artifact follows the `*-desktop-plugin` naming convention:

```xml
<requireArtifactIdPattern>
    <packagings>
        <packaging>nbm</packaging>
    </packagings>
    <pattern>^.+-desktop-plugin$</pattern>
    <message>nbm artifacts must follow the "*-desktop-plugin" naming convention</message>
</requireArtifactIdPattern>
```

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

