# Nop Platform - Dependencies Management (nop-dependencies)

The nop-dependencies module serves as a centralized dependency management hub for the Nop Platform. It defines and manages version consistency for all third-party libraries used throughout the platform.

## Overview

This module functions as a Maven BOM (Bill of Materials), providing a single source of truth for dependency versions across the entire Nop Platform ecosystem. It ensures that all modules use consistent versions of libraries, preventing version conflicts and ensuring compatibility.

## Core Features

- **Version Centralization**: All third-party library versions are defined in one place
- **BOM Support**: Maven Bill of Materials for easy dependency management
- **Framework Integration**: Compatible with major frameworks like Quarkus and Spring Boot
- **Plugin Management**: Centralized management of Maven plugin versions

## Key Framework Versions

### Core Platforms
- **Quarkus Platform**: ${quarkus.platform.version}
- **Spring Boot**: ${spring-boot.version}
- **Spring Cloud**: ${spring-cloud.version}
- **Spring AI**: ${spring-ai.version}

### Language Processing
- **Antlr4**: ${antlr.version}
- **Janino**: ${janino.version}
- **JavaParser**: ${javaparser.version}

### Database & Data
- **Tablesaw**: ${tablesaw.version}
- **Apache Lucene**: ${lucene.version}
- **Apache Pulsar**: ${pulsar.version}
- **Nacos**: ${nacos.version}

### GraalVM
- **GraalVM**: ${graalvm.version}

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
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

## Major Dependencies

### Core Frameworks
- Quarkus Platform
- Spring Boot
- Spring Cloud
- Spring AI

### Language & Parser
- Antlr4
- JavaParser
- Janino

### Database & Storage
- H2
- MySQL
- PostgreSQL
- Apache Pulsar
- Nacos

### Data Processing
- Tablesaw
- Apache Lucene
- Jackson

### Cloud & Messaging
- AWS SDK
- Apache Pulsar
- Redis/Lettuce

### Security
- JWT (JSON Web Token)
- Keycloak
- Sentinel

### Document Processing
- OpenPDF
- Apache PDFBox

### Testing
- JUnit Jupiter
- TestContainers

## Build & Plugin Management

This module also manages versions for common Maven plugins:

- **Antlr4 Maven Plugin**: ${antlr.version}
- **Compiler Plugin**: ${compiler-plugin.version}
- **Surefire Plugin**: ${surefire-plugin.version}
- **Quarkus Maven Plugin**: ${quarkus.platform.version}

## Directory Structure

```
nop-dependencies/
└── pom.xml  # Main BOM file containing all dependency versions
```


