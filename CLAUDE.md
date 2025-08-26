# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

Apache Calcite uses Gradle with Kotlin DSL. Set `JAVA_HOME` before running commands:

```bash
export JAVA_HOME=$(jenv prefix)  # or your Java installation path
./gradlew build                  # Build all modules
./gradlew test                   # Run unit tests
./gradlew testSlow              # Run slow/integration tests  
./gradlew check                 # Run all checks (tests + style)
./gradlew style                 # Check/fix code style (checkstyle + formatting)
./gradlew autostyleApply        # Apply code formatting
```

### Running Specific Tests
```bash
./gradlew :core:test --tests "CalciteAssertTest"           # Single test class
./gradlew :core:test --tests "*Sql*Test"                   # Pattern matching
./gradlew :example-csv:test                                 # Module-specific tests
```

### Integration Tests
```bash  
./gradlew integTestH2          # H2 database integration tests
./gradlew integTestAll         # All database integration tests
```

## Architecture Overview

### Core Components
- **`core/`** - Main SQL engine: parser, validator, optimizer, relational algebra
- **`linq4j/`** - Language Integrated Query for Java (query execution runtime)
- **Adapter modules** - Database connectors (cassandra, druid, elasticsearch, etc.)
- **`babel/`** - Multi-dialect SQL parser (BigQuery, PostgreSQL, etc.)

### Key Classes and Packages
- `org.apache.calcite.sql` - SQL AST nodes and parser
- `org.apache.calcite.rel` - Relational algebra operators  
- `org.apache.calcite.plan` - Query optimizer and rules
- `org.apache.calcite.adapter` - Database adapter interfaces
- `org.apache.calcite.test.CalciteAssert` - Main testing utility

### Module Structure
Each adapter follows the pattern:
```
[adapter]/
├── src/main/java/org/apache/calcite/adapter/[name]/
└── src/test/java/org/apache/calcite/test/[Name]Test.java
```

## Development Notes

### Code Generation
- Uses FMPP templates and JavaCC for parser generation
- Generated code is in `target/generated-sources/`
- Run `./gradlew :core:compileJava` to regenerate

### Testing Framework
- Tests extend `CalciteAssert` or use `CalciteAssert.that()` fluent API
- SQL tests use `.sql()` files with expected results
- Slow tests are marked with `@Test(timeout=...)` or `@Slow` annotation

### SQL Dialects
- Default dialect is CALCITE (extended ANSI SQL)
- Adapter-specific dialects in `SqlDialect` implementations
- Use `babel` module for parsing non-standard SQL variants