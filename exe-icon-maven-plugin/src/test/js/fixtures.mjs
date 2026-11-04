import { NtExecutable, NtExecutableResource } from "resedit";
import { Buffer } from "buffer";

globalThis.Buffer = Buffer;

export function variant(buffer, kind) {
  const executable = NtExecutable.from(buffer);
  const resources = NtExecutableResource.from(executable);
  const group = resources.entries.find(entry => entry.type === 14);
  if (kind === "shared") {
    resources.entries.push({ ...group, id: 200, bin: group.bin.slice(0) });
  } else if (kind === "languages") {
    const language = group.lang === 1033 ? 1036 : 1033;
    for (const entry of [...resources.entries]) {
      if (entry.type === 3 || entry.type === 14) {
        resources.entries.push({ ...entry, lang: language, bin: entry.bin.slice(0) });
      }
    }
  } else if (kind === "none") {
    resources.entries = resources.entries.filter(entry => entry.type !== 14 && entry.type !== 3);
  } else {
    throw new Error(`Unknown fixture variant: ${kind}`);
  }
  resources.outputResource(executable);
  return executable.generate();
}
