<!-- OPENSPEC:START -->
# OpenSpec Instructions

These instructions are for AI assistants working in this project.

Always open `@/openspec/AGENTS.md` when the request:
- Mentions planning or proposals (words like proposal, spec, change, plan)
- Introduces new capabilities, breaking changes, architecture shifts, or big performance/security work
- Sounds ambiguous and you need the authoritative spec before coding

Use `@/openspec/AGENTS.md` to learn:
- How to create and apply change proposals
- Spec format and conventions
- Project structure and guidelines

Keep this managed block so 'openspec update' can refresh the instructions.

<!-- OPENSPEC:END -->

# AGENTS.md - Nop Platform Development Guidelines

Essential guidelines for coding, building, testing, and contributing to Nop Platform.

## Build Commands

### Full Build
```bash
mvn clean install -DskipTests
mvn clean install -DskipTests -Dquarkus.package.type=uber-jar
mvn clean install -DskipTests -T 1C
```

### Testing
```bash
mvn test
mvn test -Dtest=AiConverterTest
mvn test -Dtest=AiConverterTest#testConvertOrm
mvn test -Pcoverage
mvn test -X
mvn verify
mvn clean install -DskipTests
```

### Code Quality
```bash
mvn checkstyle:check
```

## Code Style Guidelines

### Import Organization
```java
package io.nop.example.service;

import java.util.Date;
import java.util.List;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.CommonErrors;
import io.nop.orm.IOrmTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static io.nop.api.core.ApiErrors.*;
```

### Naming Conventions
- Classes: PascalCase, Interfaces: PascalCase with 'I', Abstract classes: PascalCase with 'Abstract'
- Methods: camelCase
- Constants: UPPER_SNAKE_CASE
- Variables: camelCase
- Boolean: prefix with 'is' (e.g., isAvailable, hasPermission)

### Formatting
- 4 spaces indentation (no tabs)
- 80-120 char line length
- Blank line between methods
- Space after commas in parameters
- Space around operators


### Error Handling
```java
public class UserService {
    public User getUser(String userId) {
        if (userId == null || userId.isEmpty()) {
            throw new NopException(CommonErrors.ARG_USER_ID)
                .param(CommonErrors.ARG_USER_ID, userId);
        }
        
        User user = dao.findById(userId);
        if (user == null) {
            throw new NopException(CommonErrors.ERR_ENTITY_NOT_FOUND)
                .param(CommonErrors.ARG_CLASS_NAME, User.class.getName())
                .param(CommonErrors.ARG_ID, userId);
        }
        return user;
    }
}
```

### Testing
```java
import io.nop.autotest.junit.JunitBaseTestCase;

public class UserServiceTest extends JunitBaseTestCase {
    @Inject
    IUserService userService;
    
    @Test
    public void testGetUserById() {
        String userId = "test-user-001";
        User user = userService.getUserById(userId);
        
        assertNotNull(user);
        assertEquals(userId, user.getId());
    }
    
    @Test
    public void testGetUserById_NotFound() {
        String userId = "non-existent-id";
        
        NopException exception = assertThrows(NopException.class, () -> {
            userService.getUserById(userId);
        });
        
        assertEquals(CommonErrors.ERR_ENTITY_NOT_FOUND, exception.getErrorCode());
    }
}
```

### Configuration
```java
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.config.AppConfig;

@Locale("zh-CN")
public interface MyConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(MyConfigs.class);

    @Description("连接超时时间（秒）")
    IConfigReference<Integer> CFG_CONNECTION_TIMEOUT = varRef(s_loc,
            "myapp.connection.timeout", Integer.class, 30);
}
```

## DO's and DON'Ts

### DO
✅ Use parameterized queries
✅ Log all exceptions with context
✅ Throw meaningful exceptions with error codes
✅ Use SLF4J logging
✅ Use configuration references
✅ Follow module patterns for consistency

### DON'T
❌ Use raw SQL with user input
❌ Suppress exceptions without logging
❌ Return null when exceptions are more appropriate
❌ Use System.out or System.err
❌ Hardcode configuration values
❌ Use Chinese in error messages
❌ Use deprecated APIs

## IDE Setup
- Java 11+ (compilation), 17+ (recommended)
- Maven 3.9.3+
- UTF-8 encoding
- Enable annotation processing
