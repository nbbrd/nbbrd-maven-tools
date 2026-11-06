# NetBeans launcher fixtures

`app.exe`, `app64.exe`, `BUILDINFO.txt`, `LICENSE` and `NOTICE` are unmodified
files from Apache NetBeans' native launcher archive:

https://archive.apache.org/dist/netbeans/native/netbeans-launchers/4-8e2bc77e581e/launcher-external-binaries-4-8e2bc77e581e.zip

Archive SHA-1 (also recorded in the upstream harness `external/binaries-list`):
`4932AC2C37141B459EAB30B8F00EF69E73134D07`.

Launcher SHA-256:

- `app.exe`: `90E1928B964CB8419D8068430D7205D989A8BBAB2D582CF878374B465CE31373`
- `app64.exe`: `9B0BC7936E1B4E3C3E9945563C03EBAB3979208B7C77CA2CD861F799A37162DA`

`app.ico` comes from the same upstream source revision:

https://raw.githubusercontent.com/apache/netbeans/8e2bc77e581e/harness/apisupport.harness/windows-launcher-src/app.ico

These fixtures are distributed under the Apache License 2.0; see the accompanying
`LICENSE` and `NOTICE`. They exercise PE32 and PE32+ resource replacement without
requiring a NetBeans installation or executing native code on non-Windows hosts.
