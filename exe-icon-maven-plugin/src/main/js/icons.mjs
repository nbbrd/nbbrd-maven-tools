import { NtExecutable, NtExecutableResource, Data, Resource } from "resedit";
import { Buffer } from "buffer";

// pe-library's non-Buffer setFixedString fallback repeats the first character.
globalThis.Buffer = Buffer;

function validateIco(buffer) {
  const view = new DataView(buffer);
  if (view.byteLength < 6 || view.getUint16(0, true) !== 0 ||
      view.getUint16(2, true) !== 1) {
    throw new Error("Invalid ICO header");
  }
  const count = view.getUint16(4, true);
  const directoryEnd = 6 + count * 16;
  if (!count || directoryEnd > view.byteLength) {
    throw new Error("ICO must contain a complete, nonempty image directory");
  }
  for (let index = 0; index < count; index++) {
    const position = 6 + index * 16;
    const size = view.getUint32(position + 8, true);
    const offset = view.getUint32(position + 12, true);
    if (!size || offset < directoryEnd || offset + size > view.byteLength) {
      throw new Error("ICO image is outside the file");
    }
    if (size < 40) {
      throw new Error("ICO image is too short");
    }
    const signature = view.getUint32(offset, true);
    if (signature !== 40 && !(signature === 0x474e5089 &&
        view.getUint32(offset + 4, true) === 0x0a1a0a0d &&
        view.getUint32(offset + 8, false) === 13 &&
        view.getUint32(offset + 12, false) === 0x49484452)) {
      throw new Error("ICO image must contain a supported bitmap or PNG header");
    }
  }
}

export function replaceIcon(executableBuffer, iconBuffer, groupId) {
  validateIco(iconBuffer);
  // Signed executables are rejected by resedit's default parser.
  const executable = NtExecutable.from(executableBuffer);
  const resources = NtExecutableResource.from(executable);
  const groups = Resource.IconGroupEntry.fromEntries(resources.entries);
  const ids = [...new Set(groups.map(group => group.id))];
  if (groupId === null) {
    if (ids.length !== 1) {
      throw new Error(`Specify iconGroupId: found ${ids.length} icon groups (${ids.join(", ")})`);
    }
    groupId = ids[0];
  }
  const selected = groups.filter(group => group.id === groupId);
  if (!selected.length) {
    throw new Error(`Icon group ${groupId} does not exist`);
  }
  const icons = Data.IconFile.from(iconBuffer).icons.map(item => item.data);
  for (const group of selected) {
    Resource.IconGroupEntry.replaceIconsForResource(resources.entries, group.id, group.lang, icons);
  }
  resources.outputResource(executable);
  const result = executable.generate();
  const written = NtExecutableResource.from(NtExecutable.from(result));
  const writtenGroups = Resource.IconGroupEntry.fromEntries(written.entries);
  for (const group of selected) {
    const actual = writtenGroups.find(candidate => candidate.id === group.id && candidate.lang === group.lang);
    if (!actual || actual.icons.length !== icons.length ||
        actual.getIconItemsFromEntries(written.entries).length !== icons.length) {
      throw new Error("Generated executable failed icon resource validation");
    }
  }
  return result;
}
