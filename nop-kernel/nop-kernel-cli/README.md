# Nop Platform - Kernel CLI (nop-kernel-cli)

The nop-kernel-cli module provides a command-line interface for the Nop Platform, enabling users to perform various operations like code generation, conversion, and other platform-specific tasks from the terminal.

## Core Features

### Command-Line Interface
- Built with **Picocli** library for a rich command-line experience
- Support for nested commands and subcommands
- Automatic help generation
- Tab completion (when available in the shell)

### Code Generation
- Generate code from templates
- Support for multiple target languages
- Integration with the Nop code generation framework

### Conversion Tools
- Convert between different file formats
- Transform data structures
- Integration with record mapping functionality

### Markdown Support
- Process and transform Markdown files
- Generate documentation from Markdown

### Native Image Support
- Can be compiled to a GraalVM native image for improved performance

## Installation

### From Source

1. Clone the repository:
   ```bash
   git clone https://github.com/entropy-cloud/nop-platform.git
   cd nop-platform/nop-kernel/nop-kernel-cli
   ```

2. Build the project:
   ```bash
   mvn clean package
   ```

3. The shaded JAR will be available in the `target` directory.

### Native Image

To build a native image:

```bash
mvn clean package -Pnative
```

## Usage

### Basic Usage

```bash
java -jar nop-kernel-cli-2.0.0-SNAPSHOT.jar [command] [options]
```

Or with the native image:

```bash
nop-kernel-cli [command] [options]
```

### Available Commands

#### Main Command
- `help`: Show help information about any command

#### Generate Command (`gen`)
- Generate code or other artifacts from templates

```bash
java -jar nop-kernel-cli.jar gen --template <template-path> --output <output-dir> [options] <model-file>
```

#### Convert Command (`convert`)
- Convert between different file formats or data structures

```bash
java -jar nop-kernel-cli.jar convert --output <output-file> [options] <input-file>
```

## Command Details

### Generate Command

The `gen` command is used for code generation tasks:

```
Usage: kernel-cli gen [-hV] [-F] [-i=<input>] [-o=<outputDir>] -t=<templates>... [-P=<KEY=VALUE>]... <file>
Generate code from model file path using one or more template directories
      <file>               Model file path
  -F, --force              Force overwrite existing files in output directory
  -h, --help               Show this help message and exit.
  -i, --input=<input>      Input parameters (JSON)
  -o, --output=<outputDir> Output directory (default: current directory)
  -P, --dynamicParam=<KEY=VALUE>
                           Dynamic parameter (format: -Pname=value)
  -t, --template=<templates>...
                           Template path(s); at least one template is required
  -V, --version            Print version information and exit.
```

### Convert Command

The `convert` command is used for format conversion tasks:

```
Usage: kernel-cli convert [-hV] [-a=<attachmentDir>] [-p=<phase>] -o=<outputFile>
                          <inputFile>
Convert between DSL model file formats (XML/JSON/YAML/XLSX etc.)
      <inputFile>          Model file name
  -a, --attachment-dir=<attachmentDir>
                           Attachment directory
  -h, --help               Show this help message and exit.
  -o, --output=<outputFile>
                           Output file
  -p, --phase=<phase>      Resolve Phase
  -V, --version            Print version information and exit.
```

## Directory Structure

```
io.nop.kernel.cli/
├── commands/          # Command implementations
│   ├── KernelCliConvertCommand.java  # Convert command
│   ├── KernelCliGenCommand.java      # Generate command
│   └── KernelMainCommand.java        # Main command
└── KernelCliApplication.java         # Main application entry point
```

## Key Classes

### KernelCliApplication
- Main entry point for the CLI application
- Configures and runs the Picocli command-line interface

### KernelMainCommand
- Root command for the CLI
- Defines global options and subcommands

### KernelCliGenCommand
- Implements the `gen` command for code generation
- Integrates with the Nop code generation framework

### KernelCliConvertCommand
- Implements the `convert` command for format conversion
- Uses record mapping and other Nop platform features

## Dependencies

- **Picocli**: Command-line interface framework
- **nop-codegen**: Nop code generation framework
- **nop-xlang**: Nop expression language
- **nop-record-mapping**: Record mapping functionality
- **nop-markdown**: Markdown processing support

## Native Image

The CLI can be compiled to a native image using GraalVM for improved startup time and reduced memory footprint:

```bash
mvn clean package -Pnative
```

This creates a standalone executable file that doesn't require a JVM to run.


