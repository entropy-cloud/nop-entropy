# Documentation Writing Guide

Since some tools will process them automatically, please be as standardized as possible when writing documents in Markdown:

- Use `#` for headings; every document must have exactly one level-1 heading.
- Use three consecutive backticks <code>`</code> for code blocks; do not use more than three.

## Preview of Documentation Build Output

On first run, install dependencies at the project root with `npm i`; Node.js version must be 14 or higher.

Then start the local service with `npm start` and visit `http://localhost:4000/projects/nop-entropy/docs/` in the browser to view the result.

When running `npm start`, files under `/docs` are automatically processed first. Upon success, a file `/.knosys/sites/default/source/_data/knosys/project-nop-entropy/docs.yml` will be generated, containing the total number of documents and metadata arranged according to the directory structure.

Note well — **after `npm start`, changes under `/docs` are not watched; if source documents are modified, you need to rerun the command.**

## Documentation Site Directory Layout

To display documents on the website, add their information to `/docs/.meta/toc.yml`. The data structure is as follows:

```yml
- text: Node 1 name
  uri: Path to Node 1
- text: Node 2 name
  children:
    - text: Name of the first child of Node 2
      uri: Path to the first child of Node 2
    - Path to the second child of Node 2
```

Each node can be either a string or an object. When it's a string, it represents the path of that node. When it's an object, the fields mean:

| Key | Type | Required | Description |
| --- | --- | --- | --- |
| `text` | string | When `children` is present | Node name, used to build the navigation tree; defaults to the document’s title |
| `uri` | string | When `children` is absent | Path relative to `/docs` or to the parent node |
| `children` | array | No | Child nodes |

<!-- SOURCE_MD5:324cf01714d64a5f8ca67d0a227c0c4b-->
