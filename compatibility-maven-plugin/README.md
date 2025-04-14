# compatibility-maven-plugin

Compatibility is a Maven plugin that checks the compatibility between independent projects.

Its main use case is when a library/application has extensions/plugins developed outside the main repository, the developers need to ensure that everything stays compatible.

## Usage

### check-downstream

With a pom file:

```xml
<plugin>
    <groupId>com.github.nbbrd.nbbrd-maven-tools</groupId>
    <artifactId>compatibility-maven-plugin</artifactId>
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

```bash
mvn com.github.nbbrd.nbbrd-maven-tools:compatibility-maven-plugin::check-downstream \
  -D"compatibility.source=https://github.com/jdemetra/jdplus-main" \
  -D"compatibility.source.ref=v3.4.0" \
  -D"compatibility.targets=https://github.com/nbbrd/jdplus-sdmx" \
  -D"compatibility.property=jdplus-main.version" \
  -D"compatibility.target.limit=2"
```

### merge-reports

Without a pom file (standalone mode):

```bash
mvn com.github.nbbrd.nbbrd-maven-tools:compatibility-maven-plugin::merge-reports \
  -D"compatibility.reports=r1.json,r2.json" \
  -D"compatibility.report.file=merged.md"
```
