# Documentation Writing Guidelines

Since it will be processed by some tools automatically, please follow these guidelines when writing documents in Markdown:

- Use `#` to set the title of each document. Each document must have one level of title and can only have one.
- Use three consecutive `<code>` tags to represent code blocks, do not exceed three.

## Documentation Generation Preview

Firstly, install dependencies by running `npm i` in the project root directory, limiting Node.js version to 14 or above.

Then, start the local server using `npm start`, and access `http://localhost:4000/projects/nop-entropy/docs/` in your browser to view the preview.

When executing `npm start`, it will automatically process files under `/docs` before generating `/.knosys/sites/default/source/_data/knosys/project-nop-entropy/docs.yml` file, which contains metadata for all documents. The content is structured as follows:

```yml
- text: Document Title
  uri: /docs/DocumentPath
```

Note that `npm start` does not monitor changes to files under `/docs`, so if you modify the source files of your documentation, you need to re-run `npm start`.

## Directory Organization for Documentation Site

To display documents on the website, add their metadata to `/docs/.meta/toc.yml`. The data structure is as follows:

```yml
- text: Node One Title
  uri: /docs/NodeOnePath
- text: Node Two Title
  children:
    - text: Node Two's First Child Name
      uri: /docs/NodeTwoPath/Child1
    - Node Two's Second Child Path
```

Each node can have a data type of either string or object. When it is an object, its keys are:

| Key Name | Type | Required | Description |
| --- | --- | --- | --- |
| `text` | String | Yes if `children` exists | Node name for generating the directory tree, defaults to document title |
| `uri` | String | No if `children` exists | Path relative to `/docs` or parent node |
| `children` | Array | No | Child nodes |

 </TRANSLATE_RESULT>
