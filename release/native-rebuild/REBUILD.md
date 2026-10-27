# chmcmd source and rebuild instructions

This archive contains the complete CHM utility package at FPCSource
de69d52048cf52c72ebfeef61617c04c9bc2005c with the included db2chm patches
already applied. Do not apply those patches again. Header-only notices dated
2026-10-27 identify db2chm modifications; source-manifest.json maps each file
to the source bytes used by the accepted compiler build.

The standalone chmcmd tool is distributed under its LGPL terms, including the
LGPL 2.1 LZX code. FastHTMLParser retains its separate permissive header.
The application's Java/Node UNLICENSED metadata does not restrict these third-party
rights. You may modify the tool or library, rebuild/relink it, and debug those
modifications under the applicable terms. Use --compiler chmcmd --compiler-path
to select your rebuilt tool. Preserve the db2chm encoding interface when using it
with this version of the Java application.

## Windows x64

Obtain the official FPC 3.3.1 x64 bootstrap ZIP:
https://downloads.freepascal.org/fpc/snapshot/trunk/x86_64-win64/fpc-3.3.1.x86_64-win64.built.on.i386-win32.zip

ZIP SHA-256: 2206d8d866360844e02a2920c4827bd139ec4a8713b14be2afacc04ee3e8d66e
Compiler SHA-256: 3c230fa579aaa595382b7e7e9daddd00adfd196e9743112bd551141de260b0bc
Compiler revision: 3.3.1-21298-g814ece0205.

Extract the ZIP and run rebuild.ps1 -BootstrapRoot PATH from PowerShell.
The script checks the compiler and uses the documented x86_64-win64 RTL/FCL units.
No system DLL registration or administrator permission is needed. The complete
compiler source baseline is https://github.com/fpc/FPCSource/tree/814ece0205a6b9c2c6dad74c8069ebb2ae50d1c7.

## Linux x64 and macOS Intel/Apple Silicon

Install FPC 3.2.2 with its RTL, rtl-objpas, rtl-generics, fcl-base and fcl-xml
units for the native CPU, then run bash rebuild.sh. Set FPC to the compiler
path if needed. The script uses -B -O2 -Xs -viu, the CHM source unit path and
an isolated build directory, matching the application build recipe.

Linux acceptance used Ubuntu 3.2.2+dfsg-32. Corresponding distribution sources:
https://archive.ubuntu.com/ubuntu/pool/universe/f/fpc/fpc_3.2.2+dfsg-32.dsc
macOS acceptance used Homebrew FPC 3.2.2_1; the platform-specific formula and
source/bottle identities are retained in the accompanying bootstrap-packages.json
and runtime-source-links.json. Upstream source:
https://downloads.sourceforge.net/project/freepascal/Source/3.2.2/fpc-3.2.2.source.tar.gz
SHA-256: d542e349de246843d4f164829953d1f5b864126c5b62fd17c9b45b33e23d2f44

## Runtime library scope

The unmodified Free Pascal RTL/FCL libraries use the included modified LGPL
with the independent-module linking exception. See COPYING.FPC and
https://www.freepascal.org/faq.html#general-license. Their source can be obtained
from https://www.freepascal.org/develop.html and the exact release/distribution
links above. The utility sources and all its includes are supplied here for
modifying and relinking the utility and its LZX library. Precise reconstruction
of every precompiled runtime unit, full toolchain pinning, and byte identity on
every system are separate reproducibility goals; they are not claimed complete.

The system linker, standard operating-system libraries and SDK, and the
documented FPC distribution are rebuild prerequisites. build-provenance.json
records the compiler and actual loaded unit identities for the supplied binary.
No build toolchain is downloaded when installing the npm package.
