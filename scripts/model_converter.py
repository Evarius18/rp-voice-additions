"""Geometry-preserving Minecraft 1.21.11 -> 1.21.8 model conversion.

The module deliberately has no dependency on the Fabric runtime.  It operates on
ordinary JSON dictionaries and verifies reconstructed elements by comparing the
eight transformed cuboid corners as unordered point sets.
"""

from __future__ import annotations

from copy import deepcopy
from dataclasses import asdict, dataclass, field
from itertools import permutations, product
import json
import math
from pathlib import Path
from typing import Any, Iterable, Sequence


EPSILON = 1.0e-10
MIN_1218_ANGLE = -45.0
MAX_1218_ANGLE = 45.0
MIN_MODEL_COORDINATE = -16.0
MAX_MODEL_COORDINATE = 32.0
AXES = ("x", "y", "z")

Vec3 = tuple[float, float, float]
Mat3 = tuple[Vec3, Vec3, Vec3]


@dataclass
class GeometryError:
    maximum: float = 0.0
    rms: float = 0.0
    dimensions: float = 0.0


@dataclass
class ElementResult:
    index: int
    name: str | None
    category: str
    status: str
    strategy: str
    source_from: list[float]
    source_to: list[float]
    original_rotation: dict[str, Any] | None
    output_rotation: dict[str, Any] | None
    error: GeometryError = field(default_factory=GeometryError)
    warnings: list[str] = field(default_factory=list)
    reason: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class ConversionReport:
    model: str
    elements: int = 0
    compatible_unchanged: int = 0
    converted_single_axis: int = 0
    reconstructed: int = 0
    warnings: int = 0
    failures: int = 0
    maximum_geometry_error: float = 0.0
    results: list[ElementResult] = field(default_factory=list)

    @property
    def successful(self) -> bool:
        return self.failures == 0

    def add(self, result: ElementResult) -> None:
        self.results.append(result)
        self.maximum_geometry_error = max(
            self.maximum_geometry_error, result.error.maximum
        )
        if result.strategy == "compatible-unchanged":
            self.compatible_unchanged += 1
        elif result.strategy == "single-axis":
            self.converted_single_axis += 1
        elif result.strategy.startswith("reconstructed"):
            self.reconstructed += 1
        if result.status == "WARN":
            self.warnings += 1
        elif result.status == "FAIL":
            self.failures += 1

    def to_dict(self) -> dict[str, Any]:
        return {
            "model": self.model,
            "elements": self.elements,
            "compatible_unchanged": self.compatible_unchanged,
            "converted_single_axis": self.converted_single_axis,
            "reconstructed": self.reconstructed,
            "warnings": self.warnings,
            "failures": self.failures,
            "maximum_geometry_error": self.maximum_geometry_error,
            "results": [result.to_dict() for result in self.results],
        }


@dataclass(frozen=True)
class Reconstruction:
    from_: Vec3
    to: Vec3
    rotation: dict[str, Any]
    error: GeometryError
    face_mapping_changed: bool


def load_model(path: str | Path) -> dict[str, Any]:
    with Path(path).open("r", encoding="utf-8-sig") as stream:
        document = json.load(stream)
    if not isinstance(document, dict):
        raise ValueError("The model root must be a JSON object")
    if "elements" in document and not isinstance(document["elements"], list):
        raise ValueError("Top-level 'elements' must be an array")
    return document


def _number(value: Any, label: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{label} must be numeric")
    value = float(value)
    if not math.isfinite(value):
        raise ValueError(f"{label} must be finite")
    return value


def _vec3(value: Any, label: str) -> Vec3:
    if not isinstance(value, list) or len(value) != 3:
        raise ValueError(f"{label} must contain exactly three numbers")
    return tuple(_number(component, f"{label}[{index}]") for index, component in enumerate(value))  # type: ignore[return-value]


def _clean_number(value: float) -> int | float:
    if abs(value) < EPSILON:
        return 0
    rounded = round(value)
    if abs(value - rounded) < EPSILON:
        return int(rounded)
    return round(value, 10)


def _json_vec(vector: Vec3) -> list[int | float]:
    return [_clean_number(component) for component in vector]


def _identity() -> Mat3:
    return ((1.0, 0.0, 0.0), (0.0, 1.0, 0.0), (0.0, 0.0, 1.0))


def _matmul(left: Mat3, right: Mat3) -> Mat3:
    return tuple(
        tuple(
            sum(left[row][inner] * right[inner][column] for inner in range(3))
            for column in range(3)
        )
        for row in range(3)
    )  # type: ignore[return-value]


def _matvec(matrix: Mat3, vector: Vec3) -> Vec3:
    return tuple(
        sum(matrix[row][column] * vector[column] for column in range(3))
        for row in range(3)
    )  # type: ignore[return-value]


def _rotation_matrix(axis: str, angle_degrees: float) -> Mat3:
    angle = math.radians(angle_degrees)
    cosine = math.cos(angle)
    sine = math.sin(angle)
    if axis == "x":
        return ((1.0, 0.0, 0.0), (0.0, cosine, -sine), (0.0, sine, cosine))
    if axis == "y":
        return ((cosine, 0.0, sine), (0.0, 1.0, 0.0), (-sine, 0.0, cosine))
    if axis == "z":
        return ((cosine, -sine, 0.0), (sine, cosine, 0.0), (0.0, 0.0, 1.0))
    raise ValueError(f"Unsupported rotation axis: {axis!r}")


def euler_xyz_matrix(x: float, y: float, z: float) -> Mat3:
    """Return the matrix for Minecraft 1.21.11's X, then Y, then Z order."""

    # Column vectors are transformed by Rx first, then Ry, then Rz.
    return _matmul(
        _rotation_matrix("z", z),
        _matmul(_rotation_matrix("y", y), _rotation_matrix("x", x)),
    )


def _rotation_description(rotation: dict[str, Any] | None) -> tuple[Mat3, Vec3]:
    if rotation is None:
        return _identity(), (8.0, 8.0, 8.0)
    origin = _vec3(rotation.get("origin", [8.0, 8.0, 8.0]), "rotation.origin")
    if "angle" in rotation or "axis" in rotation:
        if "angle" not in rotation or "axis" not in rotation:
            raise ValueError("Classic rotation needs both 'angle' and 'axis'")
        axis = rotation["axis"]
        if axis not in AXES:
            raise ValueError(f"Invalid classic rotation axis: {axis!r}")
        return _rotation_matrix(axis, _number(rotation["angle"], "rotation.angle")), origin
    if any(axis in rotation for axis in AXES):
        angles = tuple(_number(rotation.get(axis, 0.0), f"rotation.{axis}") for axis in AXES)
        return euler_xyz_matrix(*angles), origin
    raise ValueError("Unknown rotation object; expected angle/axis or x/y/z")


def _corners(from_: Vec3, to: Vec3) -> list[Vec3]:
    low = tuple(min(from_[index], to[index]) for index in range(3))
    high = tuple(max(from_[index], to[index]) for index in range(3))
    return [
        (x, y, z)
        for x, y, z in product((low[0], high[0]), (low[1], high[1]), (low[2], high[2]))
    ]


def _transform_points(points: Iterable[Vec3], matrix: Mat3, origin: Vec3) -> list[Vec3]:
    transformed: list[Vec3] = []
    for point in points:
        relative = tuple(point[index] - origin[index] for index in range(3))
        rotated = _matvec(matrix, relative)  # type: ignore[arg-type]
        transformed.append(tuple(rotated[index] + origin[index] for index in range(3)))  # type: ignore[arg-type]
    return transformed


def transformed_corners(element: dict[str, Any]) -> list[Vec3]:
    from_ = _vec3(element.get("from"), "element.from")
    to = _vec3(element.get("to"), "element.to")
    rotation = element.get("rotation")
    if rotation is not None and not isinstance(rotation, dict):
        raise ValueError("element.rotation must be an object")
    matrix, origin = _rotation_description(rotation)
    return _transform_points(_corners(from_, to), matrix, origin)


def _best_point_assignment(left: Sequence[Vec3], right: Sequence[Vec3]) -> tuple[float, float]:
    if len(left) != len(right):
        raise ValueError("Point sets must have equal cardinality")
    count = len(left)
    distances = [
        [sum((left[row][axis] - right[column][axis]) ** 2 for axis in range(3)) for column in range(count)]
        for row in range(count)
    ]
    # Dynamic-programming assignment is deterministic and O(n * 2^n), avoiding
    # fragile vertex-order assumptions without a third-party Hungarian solver.
    states: dict[int, tuple[float, tuple[int, ...]]] = {0: (0.0, ())}
    for row in range(count):
        next_states: dict[int, tuple[float, tuple[int, ...]]] = {}
        for mask, (cost, assignment) in states.items():
            for column in range(count):
                bit = 1 << column
                if mask & bit:
                    continue
                new_mask = mask | bit
                candidate = (cost + distances[row][column], assignment + (column,))
                previous = next_states.get(new_mask)
                if previous is None or candidate[0] < previous[0]:
                    next_states[new_mask] = candidate
        states = next_states
    squared_sum, assignment = states[(1 << count) - 1]
    paired = [math.sqrt(distances[row][column]) for row, column in enumerate(assignment)]
    return max(paired, default=0.0), math.sqrt(squared_sum / max(count, 1))


def _dimensions(from_: Vec3, to: Vec3) -> Vec3:
    return tuple(abs(to[index] - from_[index]) for index in range(3))  # type: ignore[return-value]


def _bounds_are_valid(from_: Vec3, to: Vec3) -> bool:
    return all(
        MIN_MODEL_COORDINATE - EPSILON <= value <= MAX_MODEL_COORDINATE + EPSILON
        for value in (*from_, *to)
    )


def _dimension_error(left: Vec3, right: Vec3) -> float:
    return max(abs(a - b) for a, b in zip(sorted(left), sorted(right)))


def is_valid_1218_angle(angle: float) -> bool:
    """Match the 1.21.8 ModelElement deserializer: abs(angle) <= 45."""

    return math.isfinite(angle) and MIN_1218_ANGLE - EPSILON <= angle <= MAX_1218_ANGLE + EPSILON


def _classic_rotation(axis: str, angle: float, origin: Vec3) -> dict[str, Any]:
    return {"angle": _clean_number(angle), "axis": axis, "origin": _json_vec(origin)}


def _column(matrix: Mat3, index: int) -> Vec3:
    return matrix[0][index], matrix[1][index], matrix[2][index]


def _dot(left: Vec3, right: Vec3) -> float:
    return sum(left[index] * right[index] for index in range(3))


def _determinant(matrix: Mat3) -> float:
    return (
        matrix[0][0] * (matrix[1][1] * matrix[2][2] - matrix[1][2] * matrix[2][1])
        - matrix[0][1] * (matrix[1][0] * matrix[2][2] - matrix[1][2] * matrix[2][0])
        + matrix[0][2] * (matrix[1][0] * matrix[2][1] - matrix[1][1] * matrix[2][0])
    )


def _signed_permutation_basis(
    source_matrix: Mat3,
    dimension_permutation: tuple[int, int, int],
    signs: tuple[int, int, int],
) -> Mat3:
    columns = [
        tuple(
            source_matrix[row][dimension_permutation[column]] * signs[column]
            for row in range(3)
        )
        for column in range(3)
    ]
    return tuple(
        tuple(columns[column][row] for column in range(3)) for row in range(3)
    )  # type: ignore[return-value]


def _estimated_axis_angle(matrix: Mat3, axis: str) -> float:
    if axis == "x":
        return math.degrees(math.atan2(matrix[2][1], matrix[1][1]))
    if axis == "y":
        return math.degrees(math.atan2(matrix[0][2], matrix[0][0]))
    if axis == "z":
        return math.degrees(math.atan2(matrix[1][0], matrix[0][0]))
    raise ValueError(f"Unsupported rotation axis: {axis!r}")


def _candidate_angles(matrix: Mat3, axis: str) -> tuple[float, ...]:
    estimated = _estimated_axis_angle(matrix, axis)
    clamped = max(MIN_1218_ANGLE, min(MAX_1218_ANGLE, estimated))
    # The clamped analytic estimate finds exact single-axis representations.
    # Boundary/zero candidates make lossy selection stable for a general OBB.
    return tuple(dict.fromkeys((clamped, MIN_1218_ANGLE, 0.0, MAX_1218_ANGLE)))


def _face_mapping_changed(
    dimension_permutation: tuple[int, int, int], signs: tuple[int, int, int]
) -> bool:
    return dimension_permutation != (0, 1, 2) or signs != (1, 1, 1)


def _reconstruct(element: dict[str, Any]) -> Reconstruction:
    source_from = _vec3(element.get("from"), "element.from")
    source_to = _vec3(element.get("to"), "element.to")
    if not _bounds_are_valid(source_from, source_to):
        raise ValueError("element.from/to must stay within Minecraft 1.21.8's -16..32 model-coordinate range")
    source_rotation = element.get("rotation")
    source_matrix, source_origin = _rotation_description(source_rotation)
    target_points = _transform_points(_corners(source_from, source_to), source_matrix, source_origin)
    center = tuple(sum(point[axis] for point in target_points) / 8.0 for axis in range(3))
    source_dimensions = _dimensions(source_from, source_to)

    best: tuple[tuple[float, float, int, float, int], Reconstruction] | None = None
    seen_candidates: set[tuple[Vec3, str, float, bool]] = set()
    for dimension_permutation in permutations(range(3)):
        candidate_dimensions = tuple(source_dimensions[index] for index in dimension_permutation)
        half = tuple(value / 2.0 for value in candidate_dimensions)
        candidate_from = tuple(center[index] - half[index] for index in range(3))
        candidate_to = tuple(center[index] + half[index] for index in range(3))
        for signs in product((-1, 1), repeat=3):
            signed_basis = _signed_permutation_basis(source_matrix, dimension_permutation, signs)
            # Reflections describe the same corner set, but cannot themselves be
            # represented by a proper rotation matrix. The matching proper basis
            # is covered by another sign combination.
            if _determinant(signed_basis) < 0.0:
                continue
            mapping_changed = _face_mapping_changed(dimension_permutation, signs)
            for axis_index, axis in enumerate(AXES):
                for angle in _candidate_angles(signed_basis, axis):
                    key = (candidate_dimensions, axis, round(angle, 12), mapping_changed)
                    if key in seen_candidates:
                        continue
                    seen_candidates.add(key)
                    candidate_matrix = _rotation_matrix(axis, angle)
                    candidate_points = _transform_points(
                        _corners(candidate_from, candidate_to), candidate_matrix, center
                    )
                    maximum, rms = _best_point_assignment(target_points, candidate_points)
                    dimension_error = _dimension_error(source_dimensions, candidate_dimensions)
                    reconstruction = Reconstruction(
                        candidate_from,
                        candidate_to,
                        _classic_rotation(axis, angle, center),
                        GeometryError(maximum, rms, dimension_error),
                        mapping_changed,
                    )
                    # Geometry dominates. On exact ties prefer intact face
                    # semantics, then the smallest rotation and stable axis.
                    score = (maximum, rms, int(mapping_changed), abs(angle), axis_index)
                    if best is None or score < best[0]:
                        best = score, reconstruction
    assert best is not None
    return best[1]


def _rotation_category(rotation: dict[str, Any] | None) -> str:
    if rotation is None:
        return "none"
    if "angle" in rotation or "axis" in rotation:
        return "classic"
    nonzero = sum(abs(_number(rotation.get(axis, 0.0), f"rotation.{axis}")) > EPSILON for axis in AXES)
    return "new-single-axis" if nonzero <= 1 else "new-multi-axis"


def analyze_document(document: dict[str, Any]) -> dict[str, int]:
    counts = {"elements": 0, "classic": 0, "new_single_axis": 0, "new_multi_axis": 0, "without_rotation": 0}
    for element in document.get("elements", []):
        counts["elements"] += 1
        category = _rotation_category(element.get("rotation"))
        key = {
            "classic": "classic",
            "new-single-axis": "new_single_axis",
            "new-multi-axis": "new_multi_axis",
            "none": "without_rotation",
        }[category]
        counts[key] += 1
    return counts


def _result_for_error(
    index: int, element: dict[str, Any], category: str, reason: str
) -> ElementResult:
    return ElementResult(
        index=index,
        name=element.get("name") if isinstance(element.get("name"), str) else None,
        category=category,
        status="FAIL",
        strategy="invalid-input",
        source_from=deepcopy(element.get("from", [])),
        source_to=deepcopy(element.get("to", [])),
        original_rotation=deepcopy(element.get("rotation")),
        output_rotation=None,
        reason=reason,
    )


def _convert_element(
    element: dict[str, Any], index: int, tolerance: float, allow_lossy: bool
) -> tuple[dict[str, Any] | None, ElementResult]:
    category = _rotation_category(element.get("rotation"))
    source_from = _vec3(element.get("from"), "element.from")
    source_to = _vec3(element.get("to"), "element.to")
    if not _bounds_are_valid(source_from, source_to):
        raise ValueError("element.from/to must stay within Minecraft 1.21.8's -16..32 model-coordinate range")
    rotation = element.get("rotation")
    name = element.get("name") if isinstance(element.get("name"), str) else None

    if rotation is None:
        result = ElementResult(index, name, category, "OK", "compatible-unchanged", list(source_from), list(source_to), None, None)
        return deepcopy(element), result

    if category == "classic":
        axis = rotation.get("axis")
        angle = _number(rotation.get("angle"), "rotation.angle")
        if axis in AXES and is_valid_1218_angle(angle):
            output = deepcopy(element)
            # Normalize the rotation object and deliberately remove modern-only
            # or unknown rotation members from the 1.21.8 output schema.
            origin = _vec3(rotation.get("origin", [8.0, 8.0, 8.0]), "rotation.origin")
            output["rotation"] = _classic_rotation(axis, angle, origin)
            result = ElementResult(index, name, category, "OK", "compatible-unchanged", list(source_from), list(source_to), deepcopy(rotation), deepcopy(output["rotation"]))
            return output, result

    if category == "new-single-axis":
        nonzero = [axis for axis in AXES if abs(_number(rotation.get(axis, 0.0), f"rotation.{axis}")) > EPSILON]
        axis = nonzero[0] if nonzero else "y"
        angle = _number(rotation.get(axis, 0.0), f"rotation.{axis}")
        if is_valid_1218_angle(angle):
            origin = _vec3(rotation.get("origin", [8.0, 8.0, 8.0]), "rotation.origin")
            output = deepcopy(element)
            output["rotation"] = _classic_rotation(axis, angle, origin)
            original_points = transformed_corners(element)
            converted_points = transformed_corners(output)
            maximum, rms = _best_point_assignment(original_points, converted_points)
            error = GeometryError(maximum, rms, 0.0)
            result = ElementResult(index, name, category, "OK", "single-axis", list(source_from), list(source_to), deepcopy(rotation), deepcopy(output["rotation"]), error)
            return output, result

    reconstruction = _reconstruct(element)
    exact = reconstruction.error.maximum <= tolerance and reconstruction.error.dimensions <= tolerance
    warnings: list[str] = []
    if reconstruction.face_mapping_changed:
        warnings.append("Face directions may differ after cuboid reorientation; UV arrays were intentionally preserved unchanged.")
    if not exact:
        reason = (
            "No equivalent cuboid using one Minecraft 1.21.8 rotation was found "
            f"within tolerance {tolerance:g}."
        )
        if not allow_lossy:
            result = ElementResult(index, name, category, "FAIL", "reconstruction-failed", list(source_from), list(source_to), deepcopy(rotation), deepcopy(reconstruction.rotation), reconstruction.error, warnings, reason)
            return None, result
        warnings.append(reason + " Best approximation emitted because --allow-lossy is active.")

    output = deepcopy(element)
    output["from"] = _json_vec(reconstruction.from_)
    output["to"] = _json_vec(reconstruction.to)
    output["rotation"] = deepcopy(reconstruction.rotation)
    if not _bounds_are_valid(reconstruction.from_, reconstruction.to):
        result = ElementResult(
            index,
            name,
            category,
            "FAIL",
            "reconstruction-failed",
            list(source_from),
            list(source_to),
            deepcopy(rotation),
            deepcopy(output["rotation"]),
            reconstruction.error,
            warnings,
            "Geometrically matching reconstruction exceeds Minecraft 1.21.8's -16..32 from/to range.",
        )
        return None, result
    status = "WARN" if warnings else "OK"
    strategy = "reconstructed-lossy" if not exact else "reconstructed"
    result = ElementResult(index, name, category, status, strategy, list(source_from), list(source_to), deepcopy(rotation), deepcopy(output["rotation"]), reconstruction.error, warnings)
    return output, result


def convert_document(
    document: dict[str, Any],
    *,
    model_name: str = "<memory>",
    tolerance: float = 0.01,
    allow_lossy: bool = False,
) -> tuple[dict[str, Any], ConversionReport]:
    if tolerance < 0.0 or not math.isfinite(tolerance):
        raise ValueError("tolerance must be a finite non-negative number")
    converted = deepcopy(document)
    elements = document.get("elements", [])
    if not isinstance(elements, list):
        raise ValueError("Top-level 'elements' must be an array")
    report = ConversionReport(model=model_name, elements=len(elements))
    output_elements: list[dict[str, Any]] = []
    for index, element in enumerate(elements):
        if not isinstance(element, dict):
            result = _result_for_error(index, {}, "invalid", "Element must be a JSON object")
            report.add(result)
            continue
        try:
            output, result = _convert_element(element, index, tolerance, allow_lossy)
        except (KeyError, TypeError, ValueError) as error:
            result = _result_for_error(index, element, "invalid", str(error))
            output = None
        report.add(result)
        if output is not None:
            output_elements.append(output)
    converted["elements"] = output_elements
    return converted, report
