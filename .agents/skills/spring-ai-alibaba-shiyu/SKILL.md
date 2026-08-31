```markdown
# spring-ai-alibaba-shiyu Development Patterns

> Auto-generated skill from repository analysis

## Overview
This skill provides guidance on contributing to the `spring-ai-alibaba-shiyu` Java codebase. It covers the project's coding conventions, commit message patterns, and testing approaches. While no specific frameworks or automated workflows were detected, this guide outlines best practices for file organization, code style, and common development tasks.

## Coding Conventions

### File Naming
- Use **PascalCase** for all file names.
  - Example: `MyService.java`, `UserRepository.java`

### Import Style
- Use **relative imports** within the codebase.
  - Example:
    ```java
    import com.example.project.service.UserService;
    ```

### Export Style
- Use **named exports** (Java's `public` classes and interfaces).
  - Example:
    ```java
    public class UserService {
        // ...
    }
    ```

### Commit Messages
- Follow **Conventional Commits** with prefixes such as `fix` and `feat`.
- Keep commit messages concise (average 73 characters).
  - Example:
    ```
    feat: add support for Alibaba Cloud integration
    fix: resolve null pointer in ShiyuClient initialization
    ```

## Workflows

### Creating a New Feature
**Trigger:** When adding new functionality  
**Command:** `/create-feature`

1. Create a new branch: `git checkout -b feat/short-description`
2. Implement your feature in a PascalCase-named file if new.
3. Use relative imports and named exports.
4. Write or update tests as needed.
5. Commit with a message like: `feat: brief description of the feature`
6. Open a pull request for review.

### Fixing a Bug
**Trigger:** When resolving a defect  
**Command:** `/fix-bug`

1. Create a new branch: `git checkout -b fix/short-description`
2. Locate and fix the bug.
3. Add or update tests to cover the fix.
4. Commit with a message like: `fix: brief description of the bug fix`
5. Open a pull request for review.

## Testing Patterns

- Test files follow the pattern: `*.test.ts`
- The specific testing framework is **unknown**; inspect existing test files for conventions.
- Place tests in the same directory as the code or in a dedicated `test` folder.
- Example test file name: `UserService.test.ts`

## Commands
| Command         | Purpose                                  |
|-----------------|------------------------------------------|
| /create-feature | Start a new feature development workflow  |
| /fix-bug        | Begin a bugfix workflow                  |
```
