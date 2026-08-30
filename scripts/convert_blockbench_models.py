"""Install supplied Minecraft 1.21.11 mast models as validated 1.21.8 models.

The generic geometry conversion lives in :mod:`model_converter`. It compares the
eight transformed corners of every cuboid and only emits a classic 1.21.8 element
rotation. Five decorative antenna elements in ``mast_mobielfunk.json`` cannot be
represented exactly by the older format; generating them therefore requires the
explicit ``--allow-lossy`` option and records their measured error in a report.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from model_converter import convert_document, load_model


SOURCE_MODELS = {
    "mast_basis.json": "mast_basis.json",
    "mast.json": "mast.json",
    "mast_siene_zwei.json": "mast_sirene_zwei.json",
    "mast_siene_drei.json": "mast_sirene_drei.json",
    "mast_mobielfunk.json": "mast_mobilfunk.json",
    "mast_digitalfunk.json": "mast_digitalfunk.json",
}

TEXTURES = {
    "light_gray_concrete_powder": "rp-vca:block/mast/light_gray_concrete_powder",
    "gray_concrete_powder": "rp-vca:block/mast/gray_concrete_powder",
    "rgb_sheet": "rp-vca:block/mast/rgb_sheet",
}


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def install_textures(document: dict) -> None:
    textures = document.setdefault("textures", {})
    for key, value in list(textures.items()):
        if isinstance(value, str) and not value.startswith("#"):
            textures[key] = TEXTURES.get(value, value)
    if "particle" not in textures:
        first = next((value for value in textures.values() if isinstance(value, str)), None)
        if first is not None:
            textures["particle"] = first


def normalize_1218_document(document: dict) -> None:
    # Blockbench metadata stays in the editable source and does not belong in the
    # vanilla 1.21.8 runtime model. Groups also reference source element indices.
    document.pop("format_version", None)
    document.pop("groups", None)
    install_textures(document)
    for element in document.get("elements", []):
        for face in element.get("faces", {}).values():
            if face.get("texture") == "#missing":
                face["texture"] = "#0"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("target", type=Path)
    parser.add_argument("--tolerance", type=float, default=0.01)
    parser.add_argument("--allow-lossy", action="store_true")
    parser.add_argument("--report-json", type=Path)
    args = parser.parse_args()

    reports: list[dict] = []
    failed = False
    for source_name, target_name in SOURCE_MODELS.items():
        source_path = args.source / source_name
        if not source_path.is_file():
            raise FileNotFoundError(f"Missing supplied mast model: {source_path}")
        converted, report = convert_document(
            load_model(source_path),
            model_name=source_name,
            tolerance=args.tolerance,
            allow_lossy=args.allow_lossy,
        )
        reports.append({"target": target_name, **report.to_dict()})
        print(
            f"{source_name} -> {target_name}: elements={report.elements}, "
            f"warnings={report.warnings}, failures={report.failures}, "
            f"max_error={report.maximum_geometry_error:.10g}"
        )
        if not report.successful:
            failed = True
            continue
        normalize_1218_document(converted)
        write_json(args.target / target_name, converted)

    combined = {
        "source_format": "1.21.11",
        "target_format": "1.21.8",
        "tolerance": args.tolerance,
        "allow_lossy": args.allow_lossy,
        "models": reports,
        "failures": sum(report["failures"] for report in reports),
        "warnings": sum(report["warnings"] for report in reports),
        "maximum_geometry_error": max(
            (report["maximum_geometry_error"] for report in reports), default=0.0
        ),
    }
    if args.report_json:
        write_json(args.report_json, combined)
    return 2 if failed else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"mast model conversion failed: {error}")
        raise SystemExit(1)
