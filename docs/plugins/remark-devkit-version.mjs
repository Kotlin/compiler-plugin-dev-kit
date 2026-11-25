import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {visit} from 'unist-util-visit';

const here = path.dirname(fileURLToPath(import.meta.url));
const versionCatalogFile = path.resolve(here, '../../gradle/libs.versions.toml');

export const devKitVersionTemplate = '{{devkitVersion}}';
export const devKitVersion = readDevKitVersion();

// Replaces the version template in code blocks and in inline code.
export function remarkDevKitVersion({version = devKitVersion} = {}) {
  return (tree) => {
    visit(tree, ['code', 'inlineCode'], (node) => {
      node.value = node.value.replaceAll(devKitVersionTemplate, version);
    });
  };
}

// The Gradle builds read the same variable, so the website and the API reference show one version.
export function readDevKitVersion(environment = process.env) {
  const version = environment.DEVKIT_PUBLISH_VERSION || readCatalogVersion();
  if (!/^\d+\.\d+\.\d+(?:-[0-9A-Za-z]+)*$/.test(version)) {
    throw new Error(`Invalid DevKit version '${version}'. Use MAJOR.MINOR.PATCH with an optional suffix.`);
  }
  return version;
}

function readCatalogVersion() {
  const catalog = fs.readFileSync(versionCatalogFile, 'utf8');
  const versions = [...catalog.matchAll(/^devkit-version\s*=\s*"([^"]+)"\s*$/gm)].map((match) => match[1]);
  if (versions.length !== 1) {
    throw new Error(`Expected exactly one devkit-version in ${versionCatalogFile}.`);
  }
  return versions[0];
}
