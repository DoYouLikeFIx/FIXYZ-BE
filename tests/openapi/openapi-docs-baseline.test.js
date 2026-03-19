"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const childProcess = require("node:child_process");

const repoRoot = path.resolve(__dirname, "..", "..", "..");
const contractsDir = path.join(repoRoot, "BE", "contracts", "openapi");
const workflowPath = path.join(repoRoot, ".github", "workflows", "docs-publish.yml");
const docsSiteConfigPath = path.join(repoRoot, "BE", "scripts", "openapi-docs-site.config.json");
const docsSiteAssemblerPath = path.join(repoRoot, "BE", "scripts", "assemble-openapi-docs-site.mjs");

const docsSiteConfig = JSON.parse(fs.readFileSync(docsSiteConfigPath, "utf8"));
const requiredDocsSpecs = docsSiteConfig.specs.map((spec) => [
  path.basename(spec.contractSource),
  spec.publishedFile,
  spec.displayName,
]);

function readText(filePath) {
  return fs.readFileSync(filePath, "utf8");
}

function mustInclude(text, needle) {
  assert.match(text, new RegExp(needle.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
}

function loadContract(fileName) {
  return JSON.parse(readText(path.join(contractsDir, fileName)));
}

function operations(contract) {
  const items = [];
  for (const [route, methods] of Object.entries(contract.paths ?? {})) {
    for (const [method, operation] of Object.entries(methods ?? {})) {
      if (!operation || typeof operation !== "object" || method.startsWith("x-")) {
        continue;
      }
      items.push({ route, method: method.toUpperCase(), operation });
    }
  }
  return items;
}

function hasSchemaBearingResponse(operation) {
  return Object.values(operation.responses ?? {}).some((response) => {
    const content = response?.content ?? {};
    return Object.values(content).some((mediaType) => {
      const schema = mediaType?.schema;
      return Boolean(schema?.$ref || schema?.type || schema?.properties);
    });
  });
}

function hasSchemaBearingSuccessResponse(operation) {
  return Object.entries(operation.responses ?? {}).some(([status, response]) => {
    if (!/^2\d\d$/.test(String(status)) || String(status) === "204") {
      return false;
    }
    const content = response?.content ?? {};
    return Object.values(content).some((mediaType) => {
      const schema = mediaType?.schema;
      return Boolean(
        schema?.$ref
          || schema?.type
          || schema?.properties
          || schema?.items
          || schema?.additionalProperties,
      );
    });
  });
}

function isIntentionalNoContentOperation(operation) {
  return Object.entries(operation.responses ?? {}).some(([status, response]) => {
    if (String(status) !== "204") {
      return false;
    }
    return String(response?.description ?? "").trim().toLowerCase() === "no content";
  });
}

function hasApiErrorResponse(operation) {
  return Object.entries(operation.responses ?? {}).some(([status, response]) => {
    if (!/^[45]\d\d$/.test(String(status))) {
      return false;
    }
    const content = response?.content ?? {};
    return Object.values(content).some((mediaType) => mediaType?.schema?.$ref === "#/components/schemas/ApiErrorResponse");
  });
}

function hasWildcardErrorContent(operation) {
  return Object.entries(operation.responses ?? {}).some(([status, response]) => {
    if (!/^[45]\d\d$/.test(String(status))) {
      return false;
    }
    return Boolean(response?.content?.["*/*"]);
  });
}

function hasStatusCode(operation, statusCode) {
  return Boolean(operation.responses?.[String(statusCode)]);
}

function hasRequiredHeader(operation, headerName) {
  return (operation.parameters ?? []).some((parameter) =>
    parameter?.in === "header" && parameter?.name === headerName && parameter?.required === true);
}

function responseSchemaRef(operation, statusCode) {
  const content = operation.responses?.[String(statusCode)]?.content ?? {};
  if (content["application/json"]?.schema?.$ref) {
    return content["application/json"].schema.$ref;
  }
  const first = Object.values(content)[0];
  return first?.schema?.$ref ?? "";
}

function hasResponseHeader(operation, statusCode, headerName) {
  return Boolean(operation.responses?.[String(statusCode)]?.headers?.[headerName]);
}

test("docs-publish workflow delegates selector bundle assembly to the checked-in site assembler", () => {
  const workflow = readText(workflowPath);

  mustInclude(workflow, "Publish API Docs to GitHub Pages");
  mustInclude(workflow, "./gradlew :channel-service:refreshOpenApiDocs");
  mustInclude(workflow, ":corebank-service:refreshOpenApiDocs");
  mustInclude(workflow, ":fep-gateway:refreshOpenApiDocs");
  mustInclude(workflow, ":fep-simulator:refreshOpenApiDocs");
  mustInclude(workflow, "node BE/scripts/assemble-openapi-docs-site.mjs");
});

test("checked-in docs site assembler produces the canonical four-service selector bundle", () => {
  const outputDir = fs.mkdtempSync(path.join(os.tmpdir(), "fixyz-docs-site-"));

  childProcess.execFileSync(
    process.execPath,
    [
      docsSiteAssemblerPath,
      "--repo-root",
      repoRoot,
      "--output-dir",
      outputDir,
      "--use-contracts",
      "true",
    ],
    { cwd: repoRoot, stdio: "pipe" },
  );

  for (const [sourceSpec, publishedSpec, displayName] of requiredDocsSpecs) {
    assert.ok(fs.existsSync(path.join(outputDir, publishedSpec)), `${publishedSpec} was not assembled`);
    const assembled = JSON.parse(readText(path.join(outputDir, publishedSpec)));
    assert.match(String(assembled.openapi ?? ""), /^3\.0/);
    assert.equal(typeof assembled.paths, "object");
  }

  const indexHtml = readText(path.join(outputDir, "index.html"));
  mustInclude(indexHtml, docsSiteConfig.primaryName);
  for (const [, publishedSpec, displayName] of requiredDocsSpecs) {
    mustInclude(indexHtml, publishedSpec);
    mustInclude(indexHtml, displayName);
  }
});

test("required OpenAPI contracts keep summaries and schema-bearing response contracts for every published operation", () => {
  for (const [contractFile] of requiredDocsSpecs) {
    const contract = loadContract(contractFile);
    const missingSummary = operations(contract).filter(({ operation }) => !operation.summary || !operation.summary.trim());
    const missingResponseSchema = operations(contract).filter(
      ({ operation }) => !hasSchemaBearingResponse(operation) && !isIntentionalNoContentOperation(operation),
    );
    const missingSuccessResponseSchema = operations(contract).filter(({ operation }) => {
      const hasNonNoContentSuccess = Object.keys(operation.responses ?? {}).some(
        (status) => /^2\d\d$/.test(String(status)) && String(status) !== "204",
      );
      return hasNonNoContentSuccess && !hasSchemaBearingSuccessResponse(operation);
    });

    assert.deepEqual(
      missingSummary,
      [],
      `${contractFile} contains operations without summary: ${JSON.stringify(missingSummary, null, 2)}`,
    );
    assert.deepEqual(
      missingResponseSchema,
      [],
      `${contractFile} contains operations without schema-bearing response contract: ${JSON.stringify(missingResponseSchema, null, 2)}`,
    );
    assert.deepEqual(
      missingSuccessResponseSchema,
      [],
      `${contractFile} contains operations without schema-bearing success response contract: ${JSON.stringify(missingSuccessResponseSchema, null, 2)}`,
    );
  }
});

test("required OpenAPI contracts document ApiErrorResponse-backed common failure responses for every published operation", () => {
  for (const [contractFile] of requiredDocsSpecs) {
    const contract = loadContract(contractFile);
    const missingErrorDocs = operations(contract).filter(({ operation }) => !hasApiErrorResponse(operation));
    const wildcardErrorDocs = operations(contract).filter(({ operation }) => hasWildcardErrorContent(operation));

    assert.deepEqual(
      missingErrorDocs,
      [],
      `${contractFile} contains operations without documented ApiErrorResponse failures: ${JSON.stringify(missingErrorDocs, null, 2)}`,
    );
    assert.deepEqual(
      wildcardErrorDocs,
      [],
      `${contractFile} contains error responses still published as */*: ${JSON.stringify(wildcardErrorDocs, null, 2)}`,
    );
  }
});

test("protected OpenAPI contracts document auth failures and required internal-secret headers", () => {
  const channelContract = loadContract("channel-service.json");
  const memberProfile = channelContract.paths["/api/v1/members/me"].get;
  const adminAuditLogs = channelContract.paths["/api/v1/admin/audit-logs"].get;
  const orderSessionCreate = channelContract.paths["/api/v1/orders/sessions"].post;

  assert.ok(hasStatusCode(memberProfile, "401"));
  assert.ok(!hasStatusCode(memberProfile, "403"));
  assert.ok(hasStatusCode(orderSessionCreate, "401"));
  assert.ok(!hasStatusCode(orderSessionCreate, "403"));
  assert.ok(hasStatusCode(adminAuditLogs, "401"));
  assert.ok(hasStatusCode(adminAuditLogs, "403"));
  assert.equal(responseSchemaRef(memberProfile, "401"), "#/components/schemas/ApiErrorResponse");
  assert.ok(hasResponseHeader(memberProfile, "401", "X-Correlation-Id"));
  assert.ok(hasResponseHeader(memberProfile, "401", "traceparent"));
  assert.ok(hasResponseHeader(adminAuditLogs, "403", "X-Correlation-Id"));
  assert.ok(hasResponseHeader(adminAuditLogs, "403", "traceparent"));

  const corebankContract = loadContract("corebank-service.json");
  const missingCorebankAuth = operations(corebankContract).filter(({ route, operation }) =>
    route.startsWith("/internal/")
      && (!hasStatusCode(operation, "401") || !hasRequiredHeader(operation, "X-Internal-Secret")));

  assert.deepEqual(
    missingCorebankAuth,
    [],
    `corebank-service.json contains internal operations without documented internal-secret auth contract: ${JSON.stringify(missingCorebankAuth, null, 2)}`,
  );
  const corebankPortfolio = corebankContract.paths["/internal/v1/portfolio"].get;
  assert.ok(hasResponseHeader(corebankPortfolio, "401", "X-Correlation-Id"));
  assert.ok(hasResponseHeader(corebankPortfolio, "401", "traceparent"));
  assert.equal(responseSchemaRef(corebankPortfolio, "401"), "#/components/schemas/ApiErrorResponse");

  const gatewayContract = loadContract("fep-gateway.json");
  const missingGatewayAuth = operations(gatewayContract).filter(({ route, operation }) =>
    (route.startsWith("/fep/") || route.startsWith("/fep-internal/"))
      && (!hasStatusCode(operation, "401") || !hasRequiredHeader(operation, "X-Internal-Secret")));

  assert.deepEqual(
    missingGatewayAuth,
    [],
    `fep-gateway.json contains protected operations without documented internal-secret auth contract: ${JSON.stringify(missingGatewayAuth, null, 2)}`,
  );
  const gatewaySubmit = gatewayContract.paths["/fep/v1/orders"].post;
  assert.match(gatewaySubmit.responses["401"].description, /X-Internal-Secret/);
  assert.ok(hasResponseHeader(gatewaySubmit, "401", "X-Correlation-Id"));
  assert.ok(hasResponseHeader(gatewaySubmit, "401", "traceparent"));
  assert.equal(responseSchemaRef(gatewaySubmit, "401"), "#/components/schemas/ApiErrorResponse");

  const simulatorContract = loadContract("fep-simulator.json");
  const missingSimulatorAuth = operations(simulatorContract).filter(({ route, operation }) =>
    route.startsWith("/fep-internal/")
      && (!hasStatusCode(operation, "401") || !hasRequiredHeader(operation, "X-Internal-Secret")));

  assert.deepEqual(
    missingSimulatorAuth,
    [],
    `fep-simulator.json contains protected operations without documented internal-secret auth contract: ${JSON.stringify(missingSimulatorAuth, null, 2)}`,
  );
  const simulatorRules = simulatorContract.paths["/fep-internal/rules"].get;
  assert.ok(hasResponseHeader(simulatorRules, "401", "X-Correlation-Id"));
  assert.ok(hasResponseHeader(simulatorRules, "401", "traceparent"));
  assert.equal(responseSchemaRef(simulatorRules, "401"), "#/components/schemas/ApiErrorResponse");
});
