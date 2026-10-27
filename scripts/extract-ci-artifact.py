"""Extract a verified CI ZIP inside a fresh target directory; reject links/traversal."""
import pathlib,sys,zipfile
archive,destination=pathlib.Path(sys.argv[1]),pathlib.Path(sys.argv[2]).resolve()
if destination.exists():raise RuntimeError('Use a fresh artifact extraction directory')
flat='--flat' in sys.argv[3:]
with zipfile.ZipFile(archive) as source:
    entries=[]
    for info in source.infolist():
        name=pathlib.PurePosixPath(info.filename)
        if name.is_absolute() or '..' in name.parts or '\\' in info.filename or ':' in info.filename:
            raise RuntimeError('Unsafe artifact member')
        if (info.external_attr>>16)&0o170000==0o120000:raise RuntimeError('Artifact symlink rejected')
        target=(destination/pathlib.Path(*name.parts)).resolve()
        if not target.is_relative_to(destination):raise RuntimeError('Artifact member escapes destination')
        if info.is_dir() or flat and len(name.parts)>1:continue
        entries.append((info,target))
    destination.mkdir(parents=True)
    for info,target in entries:
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(source.read(info))
