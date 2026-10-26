#!/usr/bin/env bash
set -euo pipefail
platform="${1:?platform required}"
out="${2:?output directory required}"
commit="de69d52048cf52c72ebfeef61617c04c9bc2005c"
source_url="https://github.com/fpc/FPCSource.git"
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
mkdir -p "$out/units"
"$fpc_bin" -B -O2 -Xs "-Fu$work/packages/chm/src" "-FU$out/units" "-FE$out" "$work/packages/chm/src/chmcmd.lpr" 2>&1 | tee "$out/build.log"
test -s "$out/chmcmd"
file "$out/chmcmd" | tee "$out/binary-dependencies.txt"
if [[ "$platform" == linux-* ]]; then
  if ! ldd "$out/chmcmd" >> "$out/binary-dependencies.txt" 2>&1; then echo 'ldd not applicable; inspect ELF headers below' >> "$out/binary-dependencies.txt"; fi
  readelf -l -d -V "$out/chmcmd" >> "$out/binary-dependencies.txt"
else
  otool -L "$out/chmcmd" >> "$out/binary-dependencies.txt"
  otool -l "$out/chmcmd" >> "$out/binary-dependencies.txt"
  sw_vers >> "$out/binary-dependencies.txt"
fi
for license in COPYING COPYING.FPC COPYING.LIB; do
  if [[ -f "$work/$license" ]]; then cp "$work/$license" "$out/$license"; fi
done
tar -czf "$out/chm-source.tar.gz" -C "$work" packages/chm
cat > "$out/SOURCE.txt" <<EOF
CHM utility source: $source_url
Commit: $commit
Bootstrap FPC: $version
Bootstrap executable: $fpc_bin
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
