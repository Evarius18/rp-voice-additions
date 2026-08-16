"""Convert the supplied Blockbench mast exports to vanilla 1.21.8 block models.

Blockbench's generic export may contain free Euler rotations. Vanilla block models only
accept a single axis and the angles -45, -22.5, 0, 22.5 or 45 degrees. Right-angle parts
are baked into their bounds; remaining rotations are quantized to the nearest supported
angle. Multi-axis rotations are conservatively baked into an axis-aligned bounding box.
"""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

MODEL_NAMES = (
    "mast_basis",
    "mast",
    "mast_sirene_zwei",
    "mast_sirene_drei",
    "mast_mobilfunk",
    "mast_digitalfunk",
)
TEXTURES = {
    "light_gray_concrete_powder": "rp-vca:block/mast/light_gray_concrete_powder",
    "gray_concrete_powder": "rp-vca:block/mast/gray_concrete_powder",
    "rgb_sheet": "rp-vca:block/mast/rgb_sheet",
}


def rotate(point: list[float], origin: list[float], axis: str, degrees: float) -> list[float]:
    radians = math.radians(degrees)
    cosine, sine = math.cos(radians), math.sin(radians)
    x, y, z = (point[index] - origin[index] for index in range(3))
    if axis == "x":
        y, z = y * cosine - z * sine, y * sine + z * cosine
    elif axis == "y":
        x, z = x * cosine + z * sine, -x * sine + z * cosine
    else:
        x, y = x * cosine - y * sine, x * sine + y * cosine
    return [x + origin[0], y + origin[1], z + origin[2]]


def corners(element: dict) -> list[list[float]]:
    return [
        [x, y, z]
        for x in (element["from"][0], element["to"][0])
        for y in (element["from"][1], element["to"][1])
        for z in (element["from"][2], element["to"][2])
    ]


def set_bounds(element: dict, points: list[list[float]]) -> None:
    element["from"] = [round(min(point[index] for point in points), 5) for index in range(3)]
    element["to"] = [round(max(point[index] for point in points), 5) for index in range(3)]


def convert_rotation(element: dict) -> None:
    rotation = element.get("rotation")
    if not rotation:
        return
    if "axis" in rotation:
        angle = round(float(rotation.get("angle", 0.0)) / 22.5) * 22.5
        if abs(angle) < 0.00001:
            element.pop("rotation", None)
        else:
            rotation["angle"] = max(-45.0, min(45.0, angle))
        return

    origin = [float(value) for value in rotation.get("origin", (8, 8, 8))]
    active_axes = [axis for axis in "xyz" if abs(float(rotation.get(axis, 0.0))) > 0.00001]
    points = corners(element)
    if len(active_axes) == 1:
        axis = active_axes[0]
        angle = float(rotation[axis])
        quarter_turn = round(angle / 90.0)
        baked_angle = quarter_turn * 90.0
        if baked_angle:
            points = [rotate(point, origin, axis, baked_angle) for point in points]
            set_bounds(element, points)
        residual = angle - baked_angle
        quantized = round(residual / 22.5) * 22.5
        if abs(quantized) < 0.00001:
            element.pop("rotation", None)
        else:
            element["rotation"] = {"angle": quantized, "axis": axis, "origin": origin}
        return

    # Vanilla cannot express a free multi-axis cuboid. Baking its transformed outer
    # bounds keeps its position and visible volume without requiring a custom renderer.
    for axis in "xyz":
        angle = float(rotation.get(axis, 0.0))
        if angle:
            points = [rotate(point, origin, axis, angle) for point in points]
    set_bounds(element, points)
    element.pop("rotation", None)


def convert(source: Path, target: Path) -> None:
    model = json.loads(source.read_text(encoding="utf-8-sig"))
    model.pop("format_version", None)
    model.pop("groups", None)
    model["textures"] = {
        key: TEXTURES.get(value, value) for key, value in model.get("textures", {}).items()
    }
    for element in model.get("elements", []):
        convert_rotation(element)
        for face in element.get("faces", {}).values():
            if face.get("texture") == "#missing":
                face["texture"] = "#0"
    target.write_text(json.dumps(model, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("target", type=Path)
    args = parser.parse_args()
    args.target.mkdir(parents=True, exist_ok=True)
    for name in MODEL_NAMES:
        convert(args.source / f"{name}.json", args.target / f"{name}.json")


if __name__ == "__main__":
    main()
