# compatibility-maven-plugin

Compatibility is a Maven plugin that checks the compatibility between independent projects.

Its main use case is when a library/application has extensions/plugins developed outside the main repository, the developers need to ensure that everything stays compatible.

## Usage

With a pom file:

```xml
<plugin>
    <groupId>com.github.nbbrd.nbbrd-maven-tools</groupId>
    <artifactId>compatibility-maven-plugin</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <executions>
        <execution>
            <phase>validate</phase>
            <goals>
                <goal>check-downstream</goal>
            </goals>
            <configuration>
                <source>https://github.com/jdemetra/jdplus-main</source>
                <sourceRef>v3.4.0</sourceRef>
                <targets>
                    <target>https://github.com/nbbrd/jdplus-sdmx</target>
                </targets>
                <property>jdplus-main.version</property>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Without a pom file (standalone mode):

`mvn com.github.nbbrd.nbbrd-maven-tools:compatibility-maven-plugin::check-downstream -Dcompatibility.source=https://github.com/jdemetra/jdplus-main -Dcompatibility.source.ref=v3.4.0 -Dcompatibility.targets=https://github.com/nbbrd/jdplus-sdmx -Dcompatibility.property=jdplus-main.version -Dcompatibility.target.limit=2`