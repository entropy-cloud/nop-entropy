const { generateProjectDocs } = require('@nop-community/hexo-theme-site');

module.exports = {
  execute: (site = 'default', sourceKey) => generateProjectDocs(site, { sourceKey }),
};
