# Agent Guidelines for ForbiddenBlocks

Minecraft Fabric mod (Java 21) using Gradle, Fabric API, ModMenu, AutoConfig, ClothConfig.

## Build/Lint/Test Commands
* **Build:** `./gradlew build` - compiles and assembles mod JAR
* **Run Client:** `./gradlew runClient` - launches Minecraft client with mod
* **Run Server:** `./gradlew runServer` - launches dedicated server with mod
* **Generate Sources:** `./gradlew genSources` - generates decompiled Minecraft sources
* **Test:** `./gradlew test` - runs unit tests (none implemented yet)
* **Single Test:** `./gradlew test --tests "*TestClass.testMethod"` (when tests exist)
* **Lint:** None configured - use IDE linting or add Spotless/Checkstyle if needed

## Code Style Guidelines
* **Formatting:** 4 spaces indentation, opening braces same line, spaces around operators
* **Naming:** PascalCase classes, camelCase methods/fields, SCREAMING_SNAKE_CASE static finals
* **Imports:** Standard Java first, then Fabric API, ModMenu, AutoConfig, ClothConfig
* **Error Handling:** try-catch blocks with SLF4J `LOGGER.error()` for exceptions
* **Comments:** Javadoc for public APIs, inline comments for complex logic only
* **Types:** Use primitives where possible, generics for collections, avoid raw types
