import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';
import {themes} from 'prism-react-renderer';
import {devKitVersion, remarkDevKitVersion} from './plugins/remark-devkit-version.mjs';
import {variables} from './variables.mjs';

// The docs workflow copies the Dokka output to this path. A local build doesn't have it.
const apiReference = `${variables.host}${variables['base-url']}api/`;

const config: Config = {
  title: variables.product,
  tagline: 'Build one Kotlin compiler plugin for many compiler and IDE versions',
  favicon: 'img/logo.svg',

  url: variables.host,
  baseUrl: variables['base-url'],
  organizationName: 'Kotlin',
  projectName: 'compiler-plugin-dev-kit',
  trailingSlash: false,

  onBrokenLinks: 'throw',
  onBrokenAnchors: 'throw',

  markdown: {
    // The guides are plain Markdown that GitHub also renders, so `.md` files aren't MDX.
    format: 'detect',
    hooks: {
      onBrokenMarkdownLinks: 'throw',
    },
  },

  presets: [
    [
      'classic',
      {
        docs: {
          path: 'pages',
          routeBasePath: '/',
          sidebarPath: './sidebars.ts',
          editUrl: `${variables['repo-tree-path'].replace('/tree/', '/edit/')}/docs/`,
          // The value in the options invalidates the cached pages when the version changes.
          beforeDefaultRemarkPlugins: [[remarkDevKitVersion, {version: devKitVersion}]],
        },
        blog: false,
        pages: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    image: 'img/logo.svg',
    colorMode: {
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: variables.product,
      logo: {
        alt: `${variables.product} logo`,
        src: 'img/logo.svg',
      },
      items: [
        {
          href: `${variables['repo-root-path']}/releases`,
          label: devKitVersion,
          position: 'right',
        },
        {
          href: apiReference,
          label: 'API Reference',
          position: 'right',
        },
        {
          href: variables['repo-root-path'],
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            {label: 'Get started', to: '/'},
            {label: 'Supported versions', to: '/versions'},
            {label: 'Migration guide', to: '/migration/migration-guide'},
          ],
        },
        {
          title: 'More',
          items: [
            {label: 'API Reference', href: apiReference},
            {label: 'GitHub', href: variables['repo-root-path']},
            {label: 'License', href: `${variables['repo-root-path']}/blob/main/LICENSE.txt`},
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} JetBrains s.r.o.`,
    },
    prism: {
      theme: themes.github,
      darkTheme: themes.dracula,
      additionalLanguages: ['properties', 'bash'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
