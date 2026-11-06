import { build } from "esbuild";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const output = new URL("../generated-resources/internal/exeicon/", import.meta.url);
await mkdir(output, { recursive: true });
await build({
  entryPoints: ["icons.mjs"],
  bundle: true,
  platform: "browser",
  format: "iife",
  globalName: "ExeIcons",
  target: "es2022",
  charset: "ascii",
  outfile: fileURLToPath(new URL("icons.js", output)),
  legalComments: "inline"
});

const notices = [];
for (const name of ["resedit", "pe-library", "buffer", "base64-js", "ieee754"]) {
  const directory = new URL(`node_modules/${name}/`, import.meta.url);
  const pkg = JSON.parse(await readFile(new URL("package.json", directory), "utf8"));
  const license = await readFile(new URL("LICENSE", directory), "utf8");
  notices.push(`${name} ${pkg.version}\n${license}`);
}
await writeFile(new URL("THIRD-PARTY-NOTICES.txt", output), notices.join("\n\n"));

await build({
  entryPoints: ["test/fixtures.mjs"],
  bundle: true,
  platform: "browser",
  format: "iife",
  globalName: "Fixtures",
  target: "es2022",
  charset: "ascii",
  outfile: fileURLToPath(new URL("../generated-test-resources/fixtures.js", import.meta.url))
});
