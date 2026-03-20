#!/usr/bin/env node

import fs from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRootDefault = path.resolve(scriptDir, "..");
const configPath = path.join(scriptDir, "openapi-docs-site.config.json");
const config = JSON.parse(fs.readFileSync(configPath, "utf8"));

function parseArgs(argv) {
  const args = new Map();
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (!token.startsWith("--")) {
      continue;
    }
    const [key, inlineValue] = token.split("=", 2);
    if (inlineValue !== undefined) {
      args.set(key, inlineValue);
      continue;
    }
    const next = argv[i + 1];
    if (next && !next.startsWith("--")) {
      args.set(key, next);
      i += 1;
    } else {
      args.set(key, "true");
    }
  }
  return args;
}

function resolveSourceFile(repoRoot, spec, useContracts) {
  return path.join(repoRoot, useContracts ? spec.contractSource : spec.buildOutput);
}

function assertOpenApiContract(filePath, publishedFile) {
  const parsed = JSON.parse(fs.readFileSync(filePath, "utf8"));
  if (!String(parsed.openapi ?? "").startsWith("3.0")) {
    throw new Error(`${publishedFile}: openapi version missing or invalid`);
  }
  if (!parsed.paths || typeof parsed.paths !== "object") {
    throw new Error(`${publishedFile}: paths missing`);
  }
}

function buildIndexHtml(siteConfig) {
  const urls = siteConfig.specs
    .map((spec) => `            { url: "${spec.publishedFile}", name: "${spec.displayName}" }`)
    .join(",\n");

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>FIX API Docs</title>
  <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5.17.14/swagger-ui.css">
</head>
<body>
<div id="swagger-ui"></div>
<script src="https://unpkg.com/swagger-ui-dist@5.17.14/swagger-ui-bundle.js"></script>
<script src="https://unpkg.com/swagger-ui-dist@5.17.14/swagger-ui-standalone-preset.js"></script>
<script>
  SwaggerUIBundle({
    urls: [
${urls}
    ],
    "urls.primaryName": "${siteConfig.primaryName}",
    dom_id: "#swagger-ui",
    deepLinking: true,
    presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
    layout: "StandaloneLayout"
  });
</script>
</body>
</html>
`;
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(args.get("--repo-root") ?? repoRootDefault);
  const outputDir = path.resolve(args.get("--output-dir") ?? path.join(repoRoot, "docs-site"));
  const useContracts = args.get("--use-contracts") === "true";

  fs.mkdirSync(outputDir, { recursive: true });

  for (const spec of config.specs) {
    const sourceFile = resolveSourceFile(repoRoot, spec, useContracts);
    if (!fs.existsSync(sourceFile)) {
      throw new Error(`Missing OpenAPI source: ${sourceFile}`);
    }
    const destinationFile = path.join(outputDir, spec.publishedFile);
    fs.copyFileSync(sourceFile, destinationFile);
    assertOpenApiContract(destinationFile, spec.publishedFile);
  }

  fs.writeFileSync(path.join(outputDir, "index.html"), buildIndexHtml(config), "utf8");
}

main();
