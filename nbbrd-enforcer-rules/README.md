# nbbrd-enforcer-rules

Custom [Maven Enforcer](https://maven.apache.org/enforcer/maven-enforcer-plugin/) rules used by NBBRD projects.

## Rules

| Rule | Description |
| --- | --- |
| [`requireJavadocJar`](#requirejavadocjar) | Ensures that a javadoc jar is attached for every artifact that requires one. |
| [`requireArtifactIdPattern`](#requireartifactidpattern) | Ensures that the artifactId matches a regex, optionally restricted to given packagings. |
| [`requireVersionPropertyNaming`](#requireversionpropertynaming) | Enforces consistent property names for dependency and Maven plugin versions. |

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

### requireVersionPropertyNaming

Checks the names of Maven properties used for explicitly declared dependency and plugin versions:

| Version belongs to | Property name must start with | Property name must end with |
|--------------------|-------------------------------|-----------------------------|
| A dependency or build extension | `dep.` | `.version` |
| A Maven plugin | `dep.plugin.` | `.version` |

For example:

```xml
<properties>
    <dep.gson.version>2.15.0</dep.gson.version>
    <dep.plugin.compiler.version>3.16.0</dep.plugin.compiler.version>
</properties>

<dependencies>
    <dependency>
        <groupId>com.google.code.gson</groupId>
        <artifactId>gson</artifactId>
        <version>${dep.gson.version}</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>${dep.plugin.compiler.version}</version>
        </plugin>
    </plugins>
</build>
```

The version must be a single property reference. `${project.version}` is also
allowed for dependencies, build extensions, and plugins. A literal such as
`3.16.0`, `${compiler.version}`, or `${dep.plugin.compiler.version}` on a
dependency is rejected. Maven still resolves the property and validates
the resulting version; this rule checks the property name only.

The rule checks dependency and plugin declarations, their management sections,
plugin dependencies, reporting plugins, build extensions, and declarations in
profiles (including inactive profiles). It checks only versions written in the
current POM: versions inherited from a parent or supplied to a dependency by
an imported BOM are not inspected. The imported BOM's own declared version is
checked. All violations are reported together.

## Setup

Add the rules as a dependency of the `maven-enforcer-plugin` and invoke them.
Replace `1.5.0` with the release you want to use.

```xml
<properties>
    <dep.plugin.maven-enforcer-plugin.version>3.7.0</dep.plugin.maven-enforcer-plugin.version>
    <dep.nbbrd-enforcer-rules.version>1.5.0</dep.nbbrd-enforcer-rules.version>
</properties>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-enforcer-plugin</artifactId>
            <version>${dep.plugin.maven-enforcer-plugin.version}</version>
            <dependencies>
                <dependency>
                    <groupId>com.github.nbbrd.nbbrd-maven-tools</groupId>
                    <artifactId>nbbrd-enforcer-rules</artifactId>
                    <version>${dep.nbbrd-enforcer-rules.version}</version>
                </dependency>
            </dependencies>
            <executions>
                <execution>
                    <id>enforce-project-policy</id>
                    <phase>verify</phase>
                    <goals>
                        <goal>enforce</goal>
                    </goals>
                    <configuration>
                        <rules>
                            <requireVersionPropertyNaming/>
                            <requireJavadocJar/>
                        </rules>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

