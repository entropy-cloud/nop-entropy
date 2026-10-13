# Nop Platform - Dependencies Management (nop-dependencies)

The nop-dependencies module serves as a centralized dependency management hub for the Nop Platform. It defines and manages version consistency for all third-party libraries used throughout the platform.

## Overview

This module functions as a Maven BOM (Bill of Materials), providing a single source of truth for dependency versions across the entire Nop Platform ecosystem. It ensures that all modules use consistent versions of libraries, preventing version conflicts and ensuring compatibility.

## Version Governance Policy

> Established by WI10 (plan 2444, 2026-10-12) of `ai-dev/backlog/jdk25-springboot4-quarkus4-roadmap.md`.

Two upstream BOMs are imported into this BOM: **quarkus-bom** (4.0.0.Beta1) and **spring-boot-dependencies** (4.1.1), plus spring-ai-bom, spring-cloud-dependencies and testcontainers-bom. The governance rules:

1. **Import order + precedence is the divergence ruling mechanism.** The two base BOMs manage ~90 common coordinates, of which 31 have divergent versions (measured 2026-10-12, direct-entry basis). The first-imported BOM wins every divergence. Final import order (deliberate, do not reorder):

   `quarkus-bom` → `testcontainers-bom` → `spring-ai-bom` → `spring-cloud-dependencies` → `spring-boot-dependencies`

   Measured precedence on Maven wrapper 4.0.0-rc-5 (WI10 plan 2444): **local direct entries beat every import; nested imports of a later-imported BOM beat this BOM's own top-level imports regardless of position.** For testcontainers specifically, the version authority is actually **quarkus-bom** (62 managed entries incl. every artifact this platform uses @2.0.5, imported first); the tc-bom import and SB4's nested import happen to agree at 2.0.5 since plan 2451 unified the platform (history: WI6–WI10 pinned 1.21.4 against SB4's nested 2.0.5 line — that divergence no longer exists and the former core pin is gone).

2. **Local pins are allowed only for two reasons:**
   - (a) the artifact is managed by **neither** base BOM (nacos, sentinel, tablesaw, duckdb, lucene, commonmark, protostuff, jsch, keycloak-common, pdfbox, jts-core, h2gis, semver4j, openpdf, aws-java-sdk-s3 v1, java-dataloader, rocksdbjni, etc.) — each pin carries a "why not BOM-managed" comment;
   - (b) the divergence winner is **harmful** and an explicit pin overrides it. Current examples:
     - `kafka-clients` 4.3.1 — both BOMs agree on 4.2.1, but the Debezium 3.7 embed (nop-message-debezium) builds against kafka 4.3.1; a client older than the engine's build version hits `NoSuchFieldError` at startup;
     - `pulsar.*` family 4.0.14 — dropping the family pin yields a 4.2.1 (Q4, client-original) / 4.2.4 (SB4 pulsar-bom, rest of family) mixed stack, the exact shape that caused the WI33 `NoSuchMethodError`;
     - `javaparser-symbol-solver-core` 3.28.2 — managed by neither BOM; must stay version-locked to the quarkus-bom-managed javaparser-core (3.28.2).

   **Never add a version-less placeholder entry** for a BOM-managed artifact: on Maven 4 rc a local entry — even without a version — shadows the imported BOM's managed version and breaks downstream resolution with "version is missing" (observed with ojdbc11). Either no entry (let the import chain rule) or a fully pinned entry.

3. **Before dropping any pin**, verify against BOTH base BOM poms (not just one) that the artifact is consistently managed, and let the focused build/tests confirm the resulting version bump. Pins that duplicate a BOM-managed version were removed in WI10: janino, json-path, json-smart, antlr4/antlr4-runtime (quarkus-bom manages 4.13.2; nop-antlr4's `antlr.plugin.version` must stay in lockstep), avro, javaparser-core, geolatte-geom.

4. **Oracle JDBC uses the correct coordinates**: `com.oracle.database.jdbc:ojdbc11` (the legacy `com.oracle.ojdbc:ojdbc8` groupId is a deprecated namespace). Both BOMs manage it at 23.26.3.0.0, so no local pin.

## Key Framework Versions

- **Quarkus Platform**: 4.0.0.Beta1
- **Spring Boot**: 4.1.1
- **Spring Cloud**: 2025.1.3
- **Spring AI**: 2.0.1
- **Testcontainers**: 2.0.5 (platform-unified line, plan 2451; artifacts use the 2.x `testcontainers-*` names)
- **Pulsar**: 4.0.14 (family pin, WI33 ruling)
- **Kafka clients**: 4.3.1 (Debezium 3.7 floor)
- **JDK**: 25

Other notable resolved versions (all BOM-managed): antlr4 4.13.2, avro 1.12.2, javaparser 3.28.2, geolatte-geom 1.12, janino 3.1.12, json-path 2.10.0, json-smart 2.6.0, ojdbc11 23.26.3.0.0, testcontainers 2.0.5 (platform-unified, plan 2451).

## Usage

To use this BOM in your Maven project, add the following to your pom.xml:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-dependencies</artifactId>
            <version>2.0.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then you can add dependencies without specifying versions:

```xml
<dependencies>
    <dependency>
        <groupId>org.antlr</groupId>
        <artifactId>antlr4-runtime</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>
</dependencies>
```

## Major Dependency Families

- **Core Frameworks**: Quarkus Platform, Spring Boot, Spring Cloud, Spring AI
- **Language & Parser**: Antlr4, JavaParser, Janino
- **Database & Storage**: Oracle (ojdbc11), H2GIS, DuckDB, RocksDB
- **Data Processing**: Tablesaw, Apache Lucene
- **Cloud & Messaging**: Apache Pulsar, Apache Kafka, Nacos, AWS SDK (v1, S3)
- **Security**: JJWT, Keycloak, Sentinel
- **Document Processing**: OpenPDF, Apache PDFBox
- **Testing**: JUnit Jupiter, Testcontainers

## Build & Plugin Management

This module also manages versions for common Maven plugins:

- **Quarkus Maven Plugin**: ${quarkus.platform.version}
- **Site Plugin**: 4.0.0-M2
- **Project Info Reports Plugin**: 3.3.0

(The antlr4 Maven plugin version is owned by `nop-kernel/nop-antlr4` via its `antlr.plugin.version` property and must match the antlr4/antlr4-runtime versions resolved above.)

## Directory Structure

```
nop-dependencies/
└── pom.xml  # Main BOM file containing all dependency versions
```
