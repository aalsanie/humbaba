## Local setup

Build
```shell
gradlew clean build
```

Cli:
```shell
gradlew :cli:build
gradlew :cli:run --args="--help"
gradlew :cli:run --args="format . --dry-run"
## with ai
export OPENAI_API_KEY="xxxxx"
gradlew :cli:run --args="--help" --root . --ai
## one file
gradlew :cli:run --args="format src/main/kotlin/io/humbaba/cli/Main.kt --dry-run"
```

Intellij plugin
```bash

gradlew :intellij-plugin:build
gradlew :intellij-plugin:runIde
```

Gradle plugin
```shell
gradlew :humbaba:build
gradlew :humbaba:test
gradlew humbabaFormat --stacktrace
```

Maven plugin development and publish
```shell
gradlew :humbaba-maven-plugin:build
gradlew :core:signMavenPublication --no-daemon
gradlew :core:publishToMavenCentral --no-daemon
mvn -DskipTests -U deploy ## on maven-plugin module
```