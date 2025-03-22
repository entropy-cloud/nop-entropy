# Document Writing Guide

Since the document will be automatically processed by some tools, when writing a Markdown document, it's better to follow some guidelines:

- The title of the document should be set with `#` and must be unique. Each document should have only one top-level heading.
- Code blocks should be enclosed within three backticks (`<code>`). Do not exceed three levels of indentation.

# Document Generation Preview

To start, in the project root directory, execute `npm install` to install dependencies, ensuring Node.js version is 14 or higher. Then, run `npm start` to launch the local service and access it via `http://localhost:4000/projects/nop-entropy/docs/`.

When executing `npm start`, the system will automatically process the files under `/docs`. Upon successful completion, a file named `/.knosys/sites/default/source/_data/knosys/project-nop-entropy/docs.yml` will be generated. This file contains metadata about the document, including the total number of documents and their hierarchical structure.

It's important to note that after running `npm start`, the system does not monitor changes in the `/docs` directory. If any source files under `/docs` are modified, you'll need to re-run `npm start`.

# Document Directory Structure

To display the document in the website, its metadata should be added to the `/docs/.meta/toc.yml` file. The structure of this file is as follows:

```yml
- text: Node One Name
  uri: Node One Path
- text: Node Two Name
  children:
    - text: Node Two's First Subnode Name
      uri: Node Two's First Subnode Path
    - Node Two's Second Subnode Path
```

Each node can be either a string or an object. If it's a string, it represents the node's path; if it's an object, it indicates that this node has children.

| Key       | Type   | Required | Description
|-----------|--------|----------|-------------|
| `text`    | String | Yes      | Node name, used to generate the navigation tree.
| `uri`     | String | Yes      | Path relative to `/docs` or parent node's path.
| `children`| Array  | No       | Child nodes (if `text` is an object). |

[EndOfData]

# Document Writing Guide

Since the document will be automatically processed by some tools, when writing a Markdown document, it's better to follow some guidelines:

- The title of the document should be set with `#` and must be unique. Each document should have only one top-level heading.
- Code blocks should be enclosed within three backticks (`<code>`). Do not exceed three levels of indentation.

# Document Generation Preview

To start, in the project root directory, execute `npm install` to install dependencies, ensuring Node.js version is 14 or higher. Then, run `npm start` to launch the local service and access it via `http://localhost:4000/projects/nop-entropy/docs/`.

When executing `npm start`, the system will automatically process the files under `/docs`. Upon successful completion, a file named `/.knosys/sites/default/source/_data/knosys/project-nop-entropy/docs.yml` will be generated. This file contains metadata about the document, including the total number of documents and their hierarchical structure.

It's important to note that after running `npm start`, the system does not monitor changes in the `/docs` directory. If any source files under `/docs` are modified, you'll need to re-run `npm start`.

# Document Directory Structure

To display the document in the website, its metadata should be added to the `/docs/.meta/toc.yml` file. The structure of this file is as follows:

```yml
- text: Node One Name
  uri: Node One Path
- text: Node Two Name
  children:
    - text: Node Two's First Subnode Name
      uri: Node Two's First Subnode Path
    - Node Two's Second Subnode Path
```

Each node can be either a string or an object. If it's a string, it represents the node's path; if it's an object, it indicates that this node has children.

| Key       | Type   | Required | Description
|-----------|--------|----------|-------------|
| `text`    | String | Yes      | Node name, used to generate the navigation tree.
| `uri`     | String | Yes      | Path relative to `/docs` or parent node's path.
| `children`| Array  | No       | Child nodes (if `text` is an object). |
