# Buf Maven Plugin

Lint protobuf files with [buf](https://buf.build/docs/lint/) and custom Java rules
as part of your Maven build.

The plugin resolves the buf executable for the build machine's platform from
Maven Central (`build.buf:buf:exe:<platform>:<version>`), like any other Maven
artifact: repository mirrors, proxies, checksums and the local repository apply.
No global installation is needed.

## Quick start

Add the plugin to `<build><plugins>` of the project containing `src/main/proto`:

```xml
<plugin>
    <groupId>com.github.nbbrd.nbbrd-maven-tools</groupId>
    <artifactId>buf-maven-plugin</artifactId>
    <version>1.5.1-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>lint</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Run:

```shell
mvn validate
```

Problems are logged as `file:line:column: message [rule]` and fail the build:

```text
[ERROR] src\main\proto\demo\v1\greeter.proto:6:10: Field name "userName" should be lower_snake_case, such as "user_name". [FIELD_LOWER_SNAKE_CASE]
[ERROR] Failed to execute goal ...:lint (default) on project demo: buf found 1 problem(s)
```

The goal binds to `validate` by default and is skipped when the input does not
exist.

### Rules configuration

buf style and structural rules are organized in
[categories](https://buf.build/docs/lint/rules/) (`MINIMAL`, `BASIC`,
`STANDARD`, `COMMENTS`, `UNARY_RPC`) and configured in the `lint` section of a
[`buf.yaml`](https://buf.build/docs/configuration/v2/buf-yaml/) file. Without
configuration, buf applies its `STANDARD` rules.

```yaml
version: v2
modules:
  - path: src/main/proto
lint:
  use:              # categories and/or rules to enable
    - STANDARD
    - COMMENT_SERVICE
  except:           # categories and/or rules to disable
    - RPC_REQUEST_STANDARD_NAME
  ignore:           # files or directories to skip
    - src/main/proto/legacy
  ignore_only:      # files or directories to skip for some rules
    FIELD_LOWER_SNAKE_CASE:
      - src/main/proto/legacy.proto
```

A rule can also be suppressed on an element with a
`// buf:lint:ignore <RULE>` comment just before it, unless
`disallow_comment_ignores: true` is set.

buf looks up `buf.yaml` in the input directory and its parents; another file
can be set with `configFile`. The input must be a module of that file:

- a `buf.yaml` in the input directory needs no `modules` section;
- a `buf.yaml` elsewhere, or a `configFile`, must list the input in `modules`,
  with paths relative to its own directory (`buf.yaml`) or to the project
  directory (`configFile`).

Custom rules are not affected by this configuration, except for comment
ignores (see [custom rules](#custom-rules)).

### Multi-module projects

Each Maven module lints its own input and is skipped when it has none, so the
plugin can be declared once in the parent POM. Rules can be shared either with
a `buf.yaml` workspace at the root that lists every module input:

```yaml
version: v2
modules:
  - path: module-a/src/main/proto
  - path: module-b/src/main/proto
lint:
  use:
    - STANDARD
```

or with a shared `configFile` that lists `src/main/proto` as module, which
resolves against each project directory (imports between Maven modules then
require the workspace):

```xml
<configuration>
  <configFile>${maven.multiModuleProjectDirectory}/buf-lint.yaml</configFile>
</configuration>
```

### Standalone execution

The goal also runs without `pom.xml`, from the current directory; custom rules
are not available in this mode:

```shell
mvn com.github.nbbrd.nbbrd-maven-tools:buf-maven-plugin:lint -Dbuf.input=proto
```

The buf executable is then cached in `.cache/buf` of the local repository.

## Custom rules

Custom rules are written in Java against `buf-api` and run on the files
linted by buf, after buf has compiled them. They receive linked
[protobuf-java descriptors](https://protobuf.dev/reference/java/api-docs/com/google/protobuf/Descriptors.html),
so imported types can be inspected too.

1. Create a jar project depending on the API:

   ```xml
   <dependency>
       <groupId>com.github.nbbrd.nbbrd-maven-tools</groupId>
       <artifactId>buf-api</artifactId>
       <version>1.5.1-SNAPSHOT</version>
       <scope>provided</scope>
   </dependency>
   ```

2. Implement `nbbrd.buf.spi.LintRule` and report problems on descriptors; their
   locations are resolved from the source code info:

   ```java
   public final class RpcDtoSuffixRule implements LintRule {

       @Override
       public String getLintRuleId() {
           return "RPC_DTO_SUFFIX";
       }

       @Override
       public void check(FileDescriptor file, LintReporter reporter) {
           for (ServiceDescriptor service : file.getServices()) {
               for (MethodDescriptor method : service.getMethods()) {
                   if (!method.getInputType().getName().endsWith("RequestDto")) {
                       reporter.report(method, "RPC request type should end with \"RequestDto\".");
                   }
               }
           }
       }
   }
   ```

   Rule identifiers must be in `SCREAMING_SNAKE_CASE`; other rules are ignored.

3. Register the rule in `META-INF/services/nbbrd.buf.spi.LintRule`, or with
   `@ServiceProvider(LintRule.class)` from
   [java-service-util](https://github.com/nbbrd/java-service-util).

4. Add the jar to the plugin dependencies:

   ```xml
   <plugin>
       <groupId>com.github.nbbrd.nbbrd-maven-tools</groupId>
       <artifactId>buf-maven-plugin</artifactId>
       ...
       <dependencies>
           <dependency>
               <groupId>com.example</groupId>
               <artifactId>my-proto-rules</artifactId>
               <version>1.0.0</version>
           </dependency>
       </dependencies>
   </plugin>
   ```

Built-in buf rules that conflict with custom conventions can be disabled with
`except` in `buf.yaml`. Custom rules are skipped when buf reports compilation
errors.

Custom rules are disabled by identifier with `disabledRules`, since buf rejects
unknown identifiers in `buf.yaml`:

```xml
<configuration>
    <disabledRules>
        <disabledRule>RPC_DTO_ID</disabledRule>
    </disabledRules>
</configuration>
```

Like buf rules, they are also ignored on an element with a
`// buf:lint:ignore <RULE>` leading comment on the element or one of its
parents, or on the `syntax` or `package` statement for file-level problems.
Unlike buf rules, these comments are always honored, regardless of
`disallow_comment_ignores` or of the presence of `buf.yaml`.

See [`src/it/setup-rules`](src/it/setup-rules) for sample rules that check the
naming, `id` field and `oneof result { ok; err }` structure of RPC messages.

## Configuration reference

| Parameter | Property | Default | Purpose |
| --- | --- | --- | --- |
| `version` | `buf.version` | `1.75.0` | buf version to resolve from Maven repositories. |
| `executable` | `buf.executable` | Unset | Local buf executable; disables resolution. |
| `input` | `buf.input` | `src/main/proto` | Directory of protobuf files or buf workspace to lint. |
| `configFile` | `buf.configFile` | `buf.yaml` in the input or its parents | buf configuration file (`--config`). |
| `disabledRules` | `buf.disabledRules` | Unset | Identifiers of custom rules to disable (comma-separated as property). |
| `failOnError` | `buf.failOnError` | `true` | Fail the build when problems are found; otherwise log warnings. |
| `githubAnnotations` | `buf.githubAnnotations` | `true` on GitHub Actions | Print workflow commands that annotate problems on pull requests. |
| `skip` | `buf.skip` | `false` | Skip linting. |

The resolved executable is copied to `${project.build.directory}/buf` to be made
executable, and the image built for custom rules is written there.

## GitHub Actions annotations

When running on GitHub Actions (`GITHUB_ACTIONS=true`), each problem is also
printed as a [workflow command](https://docs.github.com/actions/reference/workflow-commands-for-github-actions)
(`::error` or, with `failOnError=false`, `::warning`), so that it is shown inline
on pull requests. Set `githubAnnotations` to `false` to disable it.
