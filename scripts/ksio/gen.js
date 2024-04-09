const { resolve: resolvePath } = require('path');
const { existsSync } = require('fs');
const { isArray, isPlainObject, capitalize } = require('@ntks/toolbox');

const { resolveRootPath, getConfig, readData, saveData, execute } = require('./helper');

function resolveSlug(uri) {
  return `${uri.replace(/(?:\/)?(index)?\.md$/, '') || 'index'}`;
}

function resolveDefaultDocToc(docs, docData) {
  return docs.map(({ title, uri, children }) => {
    const resolved = {};

    if (title) {
      resolved.text = title;
    }

    const slug = resolveSlug(uri);

    resolved.slug = slug;
    docData[slug] = { title: title || '', slug };

    if (children) {
      resolved.items = resolveDefaultDocToc(children, docData);
    }

    return resolved;
  });
}

function resolveCustomizedDocToc(srcPath, items, parentUri, docData) {
  const resolved = [];

  items.forEach(({ text, uri, children }) => {
    const item = {};

    if (text) {
      item.text = text;
    }

    if (isArray(children)) {
      item.items = resolveCustomizedDocToc(srcPath, children, uri, docData);
    } else {
      if (uri) {
        let docFile;

        if (uri.startsWith('./')) {
          docFile = uri;
        } else {
          docFile = `./${parentUri ? [parentUri.replace(/^\\\./, ''), uri].join('/') : uri}`;
        }

        const docPath = resolvePath(srcPath, docFile);

        if (!existsSync(docPath)) {
          return;
        }

        if (!item.text) {
          const content = readData(docPath);

          if (content) {
            const normalized = normalizeFrontMatter(content);

            if (normalized.data && normalized.data.title) {
              item.text = normalized.data.title;
            }
          }
        }

        item.slug = resolveSlug(docFile.slice(2));
        docData[item.slug] = { title: item.text || '', slug: item.slug };
      }
    }

    resolved.push(item);
  });

  return resolved;
}

function resolveDocToc(srcPath, docs, docData) {
  const customizedTocPath = `${srcPath}/.meta/toc.yml`;

  if (existsSync(customizedTocPath)) {
    return resolveCustomizedDocToc(srcPath, readData(customizedTocPath), '', docData);
  }

  return resolveDefaultDocToc(docs.structure, docData);
}

function resolveRepoData(site, config) {
  const rootPath = resolveRootPath();
  const siteDataDir = resolvePath(rootPath, `./.knosys/sites/${site}/source/_data`);

  const projectRepos = {};

  Object.entries(config.data).forEach(([srcKey, srcDir]) => {
    if (!srcKey.startsWith('project-')) {
      return;
    }

    const docData = {};
    const toc = resolveDocToc(resolvePath(rootPath, srcDir), readData(`${siteDataDir}/knosys/${srcKey}/docs.yml`), docData);

    const projectSlug = srcKey.replace(/^project\-/, '');

    projectRepos[projectSlug] = {
      name: `${projectSlug.split('-').map(w => capitalize(w)).join(' ')} 项目文档`,
      base: `/projects/${projectSlug}`,
      collection: 'docs',
      toc,
    };

    saveData(`${siteDataDir}/knosys/${projectSlug}.yml`, { items: docData });
  });

  saveData(`${siteDataDir}/local/repos.yml`, projectRepos);
}

module.exports = {
  execute: (site = 'default', sourceKey) => {
    const siteConfig = getConfig(`site.${site}`);
    const { data } = siteConfig;

    let keys = [];

    if (data) {
      if (isPlainObject(data)) {
        if (sourceKey) {
          if (data[sourceKey]) {
            keys.push(sourceKey);
          }
        } else {
          keys = Object.keys(data);
        }
      }
    }

    keys.forEach(key => execute('generate', site, key));

    if (siteConfig.generator === 'hexo') {
      setTimeout(() => resolveRepoData(site, siteConfig));
    }
  },
};
