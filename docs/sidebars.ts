import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';
import {variables} from './variables.mjs';

const sidebars: SidebarsConfig = {
  docs: [
    'overview',
    'build-structure',
    'versions',
    'multiversioning/multiversioning',
    'compiler-plugin',
    'testing',
    'gradle-plugin',
    'ide',
    'publishing',
    'ci',
    'properties',
    {
      type: 'category',
      label: 'Migration guide',
      link: {type: 'doc', id: 'migration/migration-guide'},
      items: [
        'migration/initial-setup',
        'migration/gradle-support-plugin',
        'migration/compiler-plugin',
        'migration/compiler-plugin-tests',
        'migration/initial-setup-done',
        {type: 'doc', id: 'migration/prompt', label: 'Prompt for an AI agent'},
      ],
    },
    {
      type: 'link',
      label: 'API Reference',
      href: `${variables.host}${variables['base-url']}api/`,
    },
  ],
};

export default sidebars;
