#!/usr/bin/env python3
"""Expose a custom IntelliJ SDK to Gradle without modifying the installed IDE.

The Gradle plugin only recognizes JetBrains product codes. Libraries, launch
configuration and bundled plugins stay symlinked to the original SDK; only its
product code in this build-local metadata copy is mapped to IC.
"""
import argparse
import json
from pathlib import Path
from zipfile import ZipFile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("ide", type=Path)
parser.add_argument("output", type=Path)
args = parser.parse_args()
source = args.ide.resolve()
target = args.output.resolve()
if source == target or source in target.parents:
    parser.error("Output must be outside the installed IDE")
metadata = json.loads((source / "product-info.json").read_text())
original_code = metadata["productCode"]
metadata["productCode"] = "IC"
target.mkdir(parents=True, exist_ok=True)
for child in source.iterdir():
    if child.name == "product-info.json":
        continue
    link = target / child.name
    if child.name == "lib":
        if link.is_symlink():
            link.unlink()
        link.mkdir(exist_ok=True)
        for library in child.iterdir():
            destination = link / library.name
            if not destination.exists():
                destination.symlink_to(library, target_is_directory=library.is_dir())
        # Gradle's structural reader expects the legacy main descriptor name.
        core_jar = child / "intellij.raketa.jar" if original_code == "RK" else child / "intellij.platform.ide.impl.jar"
        descriptor_name = "META-INF/RaketaPlugin.xml" if original_code == "RK" else "META-INF/PlatformLangPlugin.xml"
        with ZipFile(core_jar) as core, ZipFile(link / "baseline-sdk-descriptor.jar", "w") as descriptor:
            descriptor.writestr("META-INF/plugin.xml", core.read(descriptor_name))
        continue
    if not link.exists() and not link.is_symlink():
        link.symlink_to(child, target_is_directory=child.is_dir())
(target / "product-info.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n")
print(f"SDK metadata: {original_code} -> IC; original IDE unchanged: {target}")
