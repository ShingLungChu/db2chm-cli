#!/usr/bin/env bash
set -euo pipefail
platform="${1:?platform required}"
out="${2:?output directory required}"
commit="de69d52048cf52c72ebfeef61617c04c9bc2005c"
source_url="https://github.com/fpc/FPCSource.git"
script_root="$(cd "$(dirname "$0")/.." && pwd)"
fpc_bin="$(command -v fpc)"
version="$("$fpc_bin" -iV)"
if [[ "$version" != "3.2.2" ]]; then echo "Unapproved bootstrap FPC version: $version" >&2; exit 1; fi
case "$platform" in
  linux-x64|darwin-x64) expected_cpu="x86_64";;
  darwin-arm64) expected_cpu="aarch64";;
  *) echo "Unsupported native target" >&2; exit 1;;
esac
if [[ "$("$fpc_bin" -iTP)" != "$expected_cpu" ]]; then echo "Bootstrap CPU does not match package target" >&2; exit 1; fi
mkdir -p "$out"
out="$(cd "$out" && pwd)"
work="$(mktemp -d)"
git -C "$work" init
git -C "$work" remote add origin "$source_url"
git -C "$work" fetch --depth=1 origin "$commit"
git -C "$work" checkout --detach FETCH_HEAD
test "$(git -C "$work" rev-parse HEAD)" = "$commit"
for patch in chmcmd-fpc322.patch chmcmd-encoding.patch; do
  git -C "$work" apply "$script_root/release/patches/$patch"
  cp "$script_root/release/patches/$patch" "$out/$patch"
done
mkdir -p "$out/units"
"$fpc_bin" -B -O2 -Xs -viu "-Fu$work/packages/chm/src" "-FU$out/units" "-FE$out" "$work/packages/chm/src/chmcmd.lpr" 2>&1 | tee "$out/build.log"
test -s "$out/chmcmd"
for patch in chmcmd-fpc322.patch chmcmd-encoding.patch; do test -s "$out/$patch"; done
node "$script_root/scripts/record-native-inputs.cjs" "$out" "$fpc_bin" "$work" "$platform"
mkdir -p "$script_root/target/navigation-tools"
"$fpc_bin" -B -O2 -Xs "-Fu$work/packages/chm/src" "-FU$out/units" "-FE$script_root/target/navigation-tools" "$script_root/scripts/chm-nav-dump.pas" > "$script_root/target/navigation-tools/build.log"
file "$out/chmcmd" | tee "$out/binary-dependencies.txt"
if [[ "$platform" == linux-* ]]; then
  if ! ldd "$out/chmcmd" >> "$out/binary-dependencies.txt" 2>&1; then echo 'ldd not applicable; inspect ELF headers below' >> "$out/binary-dependencies.txt"; fi
  readelf -l -d -V "$out/chmcmd" >> "$out/binary-dependencies.txt"
else
  otool -L "$out/chmcmd" >> "$out/binary-dependencies.txt"
  otool -l "$out/chmcmd" >> "$out/binary-dependencies.txt"
  sw_vers >> "$out/binary-dependencies.txt"
fi
cp "$work/LICENSE" "$out/FPC-LICENSE.txt"
cp "$work/rtl/COPYING.FPC" "$out/RTL-COPYING.FPC"
cp "$work/rtl/COPYING.txt" "$out/RTL-COPYING.txt"
tar -czf "$out/chm-source.tar.gz" -C "$work" packages/chm LICENSE rtl/COPYING.FPC rtl/COPYING.txt
cat > "$out/SOURCE.txt" <<EOF
CHM utility source: $source_url
Commit: $commit
Bootstrap FPC: $version
Bootstrap executable: $fpc_bin
Local modifications: bootstrap rtlString alias; explicit db2chm UTF-8/CP936 navigation, keyword and search profile. Included patches record all changes.
Bootstrap library source baseline: https://github.com/fpc/FPCSource/tree/release_3_2_2
Exact distro package/revision details: bootstrap-packages.*
Build candidate: distribution review and oldest-system validation pending.
EOF
node - "$out" "$platform" "$commit" "$fpc_bin" <<'NODE'
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),cp=require('node:child_process');
const [out,platform,sourceCommit,fpc]=process.argv.slice(2);
const hash=f=>crypto.createHash('sha256').update(fs.readFileSync(f)).digest('hex');
const files=fs.readdirSync(out).filter(n=>fs.statSync(path.join(out,n)).isFile()).map(n=>({path:n,sha256:hash(path.join(out,n)),bytes:fs.statSync(path.join(out,n)).size}));
const manifest={platform,sourceCommit,sourceUrl:'https://github.com/fpc/FPCSource/tree/'+sourceCommit,bootstrapVersion:cp.execFileSync(fpc,['-iV'],{encoding:'utf8'}).trim(),bootstrapSha256:hash(fpc),redistributionApproved:false,licenseNotice:'FPC CHM utility: included license files, exact utility source archive and pinned full source URL. Distribution review pending.',files};
fs.writeFileSync(path.join(out,'manifest.json'),JSON.stringify(manifest,null,2)+'\n');
console.log(JSON.stringify(manifest,null,2));
NODE
