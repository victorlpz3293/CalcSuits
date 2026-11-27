#!/usr/bin/env python3
"""
CalcSuits Phase 4.1 — Isolated OCR Evaluation Dataset Generator (POC)
Generates controlled printed, 2D-typeset, and synthetic handwritten-style PNG
images for the 7 mandatory mathematical expressions using ImageMagick `convert`.

NOTE: Synthetic handwritten images use perturbed SVG strokes to test OCR
robustness under irregular glyphs. They do NOT substitute real human camera
handwriting evaluation on physical Android hardware.
"""

import hashlib
import json
import math
import os
import random
import subprocess
import sys
from pathlib import Path

DATASET_SEED = 20261127
OUT_DIR = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("/tmp/poc_ocr_dataset")
OUT_DIR.mkdir(parents=True, exist_ok=True)


def deterministic_seed(*parts) -> int:
    key = ":".join([str(DATASET_SEED), *(str(p) for p in parts)])
    return int(hashlib.sha256(key.encode("utf-8")).hexdigest()[:8], 16)

JETBRAINS_FONT = Path("app/src/main/res/font/jetbrains_mono.ttf").resolve()
SPACE_GROTESK_FONT = Path("app/src/main/res/font/space_grotesk.ttf").resolve()

EXPRESSIONS = [
    {
        "id": "expr_01_arith_mul",
        "display": "125 + 35 × 2",
        "expected_canonical": "125 + 35 * 2",
        "expected_latex": r"125 + 35 \times 2",
        "expected_solver_behavior": "EVALUATE",
        "expected_result": "195",
        "has_2d_structure": False,
    },
    {
        "id": "expr_02_div_dec",
        "display": "12.75 / 0.5",
        "expected_canonical": "12.75 / 0.5",
        "expected_latex": r"\frac{12.75}{0.5}",
        "expected_solver_behavior": "EVALUATE",
        "expected_result": "25.5",
        "has_2d_structure": True,
    },
    {
        "id": "expr_03_poly_sq",
        "display": "x² + 5x + 6",
        "expected_canonical": "x^2 + 5x + 6",
        "expected_latex": r"x^{2} + 5x + 6",
        "expected_solver_behavior": "UNBOUND_VARIABLE_GRAPHABLE",
        "expected_result": None,
        "has_2d_structure": True,
    },
    {
        "id": "expr_04_sqrt",
        "display": "√144 + 5",
        "expected_canonical": "sqrt(144) + 5",
        "expected_latex": r"\sqrt{144} + 5",
        "expected_solver_behavior": "EVALUATE",
        "expected_result": "17",
        "has_2d_structure": True,
    },
    {
        "id": "expr_05_grouped_frac",
        "display": "(3x + 2) / 5",
        "expected_canonical": "(3x + 2) / 5",
        "expected_latex": r"\frac{3x + 2}{5}",
        "expected_solver_behavior": "UNBOUND_VARIABLE_GRAPHABLE",
        "expected_result": None,
        "has_2d_structure": True,
    },
    {
        "id": "expr_06_linear_eq",
        "display": "2x + 5 = 15",
        "expected_canonical": "2x + 5 = 15",
        "expected_latex": r"2x + 5 = 15",
        "expected_solver_behavior": "SOLVE_LINEAR",
        "expected_result": "x = 5",
        "has_2d_structure": False,
    },
    {
        "id": "expr_07_percent_es",
        "display": "15% de 250",
        "expected_canonical": "15% de 250",
        "expected_latex": r"15\% \text{ de } 250",
        "expected_solver_behavior": "SOLVE_PERCENT",
        "expected_result": "37.5",
        "has_2d_structure": False,
    },
]


def run_convert(args):
    cmd = [
        "convert",
        *args[:-1],
        "-alpha", "remove",
        "-alpha", "off",
        "-type", "TrueColor",
        "-strip",
        "-define", "png:exclude-chunk=date,time",
        args[-1],
    ]
    subprocess.run(cmd, check=True)


def render_printed(expr, variant, out_path):
    text = expr["display"]
    if variant == "printed_clean_sans":
        run_convert([
            "-size", "640x160", "xc:white",
            "-font", "DejaVu-Sans", "-pointsize", "54",
            "-fill", "#111111", "-gravity", "center",
            "-annotate", "+0+0", text,
            str(out_path),
        ])
    elif variant == "printed_serif":
        run_convert([
            "-size", "640x160", "xc:white",
            "-font", "DejaVu-Serif", "-pointsize", "50",
            "-fill", "#111111", "-gravity", "center",
            "-annotate", "+0+0", text,
            str(out_path),
        ])
    elif variant == "printed_large":
        run_convert([
            "-size", "880x220", "xc:white",
            "-font", "DejaVu-Sans", "-pointsize", "78",
            "-fill", "#111111", "-gravity", "center",
            "-annotate", "+0+0", text,
            str(out_path),
        ])
    elif variant == "printed_mono_small":
        font = str(JETBRAINS_FONT) if JETBRAINS_FONT.exists() else "DejaVu-Sans-Mono"
        run_convert([
            "-size", "520x120", "xc:white",
            "-font", font, "-pointsize", "28",
            "-fill", "#181818", "-gravity", "center",
            "-annotate", "+0+0", text,
            str(out_path),
        ])
    elif variant == "printed_low_light":
        run_convert([
            "-size", "640x160", "radial-gradient:#8E8E8E-#585858",
            "-font", "DejaVu-Sans", "-pointsize", "50",
            "-fill", "#262626", "-gravity", "center",
            "-annotate", "+0+0", text,
            str(out_path),
        ])
    elif variant == "printed_tilted":
        run_convert([
            "-size", "640x180", "xc:white",
            "-font", "DejaVu-Sans", "-pointsize", "50",
            "-fill", "#111111", "-gravity", "center",
            "-annotate", "+0+0", text,
            "-background", "white", "-virtual-pixel", "background",
            "-distort", "Perspective", "0,0 18,16  640,0 618,4  0,180 6,166  640,180 632,174",
            str(out_path),
        ])
    elif variant == "printed_blur_noise":
        noise_seed = deterministic_seed(expr["id"], variant)
        run_convert([
            "-seed", str(noise_seed),
            "-size", "640x160", "xc:#F7F7F7",
            "-font", "DejaVu-Sans", "-pointsize", "50",
            "-fill", "#1E1E1E", "-gravity", "center",
            "-annotate", "+0+0", text,
            "-blur", "0x1.2",
            "-attenuate", "0.35", "+noise", "Gaussian",
            str(out_path),
        ])
    else:
        raise ValueError(f"Unknown printed variant: {variant}")


def render_2d_typeset(expr, out_path):
    """Renders textbook 2D mathematical layout (stacked fractions, vinculum radical, raised exponent) using native ImageMagick draw."""
    eid = expr["id"]
    if eid == "expr_02_div_dec":
        run_convert([
            "-size", "440x220", "xc:white",
            "-fill", "#111111", "-stroke", "none",
            "-font", "DejaVu-Serif", "-pointsize", "52",
            "-gravity", "North", "-annotate", "+0+32", "12.75",
            "-gravity", "South", "-annotate", "+0+32", "0.5",
            "-gravity", "None", "-stroke", "#111111", "-strokewidth", "4",
            "-draw", "line 115,108 325,108",
            str(out_path),
        ])
    elif eid == "expr_03_poly_sq":
        run_convert([
            "-size", "540x170", "xc:white",
            "-fill", "#111111", "-stroke", "none",
            "-font", "DejaVu-Serif", "-pointsize", "56",
            "-annotate", "+90+112", "x",
            "-pointsize", "34",
            "-annotate", "+126+80", "2",
            "-pointsize", "54",
            "-annotate", "+160+112", "+ 5x + 6",
            str(out_path),
        ])
    elif eid == "expr_04_sqrt":
        run_convert([
            "-size", "520x170", "xc:white",
            "-fill", "none", "-stroke", "#111111", "-strokewidth", "4.5",
            "-draw", "polyline 82,92 98,84 116,124 146,46 276,46",
            "-fill", "#111111", "-stroke", "none",
            "-font", "DejaVu-Serif", "-pointsize", "52",
            "-annotate", "+158+112", "144",
            "-annotate", "+294+112", "+ 5",
            str(out_path),
        ])
    elif eid == "expr_05_grouped_frac":
        run_convert([
            "-size", "480x220", "xc:white",
            "-fill", "#111111", "-stroke", "none",
            "-font", "DejaVu-Serif", "-pointsize", "50",
            "-gravity", "North", "-annotate", "+0+32", "3x + 2",
            "-gravity", "South", "-annotate", "+0+32", "5",
            "-gravity", "None", "-stroke", "#111111", "-strokewidth", "4",
            "-draw", "line 115,108 365,108",
            str(out_path),
        ])
    else:
        return False
    return True


def _jitter_polyline(points, rng, jitter=1.6):
    pts = [(x + rng.uniform(-jitter, jitter), y + rng.uniform(-jitter, jitter)) for x, y in points]
    if len(pts) < 2:
        return ""
    coords = " ".join(f"{x:.1f},{y:.1f}" for x, y in pts)
    return f"polyline {coords}"


# Simple single-stroke glyph templates normalized to x in [0, 30], y in [0, 50]
GLYPH_STROKES = {
    "0": [[(15, 4), (5, 14), (4, 34), (15, 46), (26, 34), (25, 14), (15, 4)]],
    "1": [[(9, 14), (16, 4), (16, 46)], [(9, 46), (23, 46)]],
    "2": [[(5, 14), (14, 4), (24, 12), (22, 24), (5, 45), (26, 45)]],
    "3": [[(6, 10), (16, 4), (24, 13), (15, 23), (25, 33), (17, 46), (5, 42)]],
    "4": [[(21, 4), (5, 30), (27, 30)], [(21, 4), (21, 46)]],
    "5": [[(24, 5), (7, 5), (6, 23), (19, 21), (25, 32), (17, 46), (5, 42)]],
    "6": [[(22, 6), (11, 14), (5, 32), (14, 46), (25, 37), (22, 25), (7, 27)]],
    "7": [[(5, 6), (26, 6), (12, 46)]],
    "8": [[(15, 24), (7, 15), (15, 4), (24, 14), (15, 24), (6, 35), (15, 46), (25, 36), (15, 24)]],
    "9": [[(24, 18), (16, 5), (6, 14), (10, 26), (23, 22), (21, 46)]],
    "+": [[(15, 12), (15, 38)], [(4, 25), (26, 25)]],
    "-": [[(5, 25), (25, 25)]],
    "×": [[(6, 14), (24, 36)], [(24, 14), (6, 36)]],
    "/": [[(25, 4), (5, 46)]],
    "=": [[(5, 19), (25, 19)], [(5, 31), (25, 31)]],
    "(": [[(20, 4), (11, 16), (11, 34), (20, 46)]],
    ")": [[(10, 4), (19, 16), (19, 34), (10, 46)]],
    ".": [[(13, 42), (16, 44), (14, 46), (12, 44), (13, 42)]],
    "%": [
        [(25, 6), (5, 44)],
        [(9, 9), (5, 14), (9, 18), (13, 13), (9, 9)],
        [(21, 32), (17, 37), (21, 41), (25, 36), (21, 32)],
    ],
    "x": [[(6, 18), (24, 44)], [(24, 18), (6, 44)]],
    "d": [[(24, 22), (12, 20), (6, 32), (13, 45), (24, 38)], [(24, 4), (24, 46)]],
    "e": [[(7, 31), (24, 30), (22, 20), (11, 20), (6, 32), (14, 45), (24, 42)]],
    "√": [[(2, 28), (8, 25), (14, 45), (24, 4), (30, 4)]],
}


def render_synthetic_handwritten(expr, difficult, out_path):
    """Generates perturbed pen-stroke handwriting-style PNG via native ImageMagick draw."""
    seed_val = deterministic_seed(expr["id"], "handwritten", int(difficult))
    rng = random.Random(seed_val)
    text = expr["display"]
    jitter = 2.6 if difficult else 1.1
    slant = -11.0 if difficult else -5.0
    stroke_w = "3.4" if difficult else "4.0"
    ink = "#2B3A55" if difficult else "#162447"
    bg = "#EFECE1" if difficult else "#FAF8F2"

    draw_ops = []
    cursor_x = 45.0
    base_y = 65.0
    i = 0
    while i < len(text):
        ch = text[i]
        if ch == " ":
            cursor_x += 22.0
            i += 1
            continue
        if ch == "²":
            for stroke in GLYPH_STROKES["2"]:
                pts = [(cursor_x + px * 0.62, base_y - 20.0 + py * 0.62) for px, py in stroke]
                draw_ops.append(_jitter_polyline(pts, rng, jitter * 0.7))
            cursor_x += 24.0
            i += 1
            continue
        if ch == "√":
            j = i + 1
            while j < len(text) and text[j].isdigit():
                j += 1
            rad_digits = max(1, j - (i + 1))
            rad_pts = [
                (cursor_x + 2, base_y + 28),
                (cursor_x + 9, base_y + 24),
                (cursor_x + 16, base_y + 47),
                (cursor_x + 28, base_y + 3),
                (cursor_x + 32 + rad_digits * 34, base_y + 3),
            ]
            draw_ops.append(_jitter_polyline(rad_pts, rng, jitter))
            cursor_x += 34.0
            i += 1
            continue

        strokes = GLYPH_STROKES.get(ch, [])
        dy = rng.uniform(-4.0, 4.0) if difficult else rng.uniform(-1.5, 1.5)
        scale = rng.uniform(0.90, 1.10) if difficult else rng.uniform(0.96, 1.04)
        for stroke in strokes:
            pts = []
            for px, py in stroke:
                sx = cursor_x + px * scale + (50 - py) * math.tan(math.radians(-slant)) * 0.35
                sy = base_y + dy + py * scale
                pts.append((sx, sy))
            draw_ops.append(_jitter_polyline(pts, rng, jitter))
        cursor_x += 34.0 if ch not in (".", "1") else 24.0
        i += 1

    width = max(560, int(cursor_x + 60))
    height = 175
    cmd = ["-size", f"{width}x{height}", f"xc:{bg}"]
    if difficult:
        cmd += [
            "-fill", "none", "-stroke", "#C5D1E0", "-strokewidth", "1.2",
            "-draw", f"line 0,45 {width},45 line 0,115 {width},115 line 0,150 {width},150",
        ]
    cmd += [
        "-fill", "none", "-stroke", ink, "-strokewidth", stroke_w,
        "-draw", "stroke-linecap round stroke-linejoin round " + " ".join(op for op in draw_ops if op),
    ]
    if difficult:
        cmd = ["-seed", str(seed_val)] + cmd + [
            "-background", bg, "-rotate", "3.5",
            "-blur", "0x0.9", "-attenuate", "0.25", "+noise", "Gaussian",
        ]
    cmd.append(str(out_path))
    run_convert(cmd)


def main():
    manifest = []
    printed_variants = [
        "printed_clean_sans",
        "printed_serif",
        "printed_large",
        "printed_mono_small",
        "printed_low_light",
        "printed_tilted",
        "printed_blur_noise",
    ]
    for expr in EXPRESSIONS:
        for variant in printed_variants:
            fname = f"{expr['id']}__{variant}.png"
            fpath = OUT_DIR / fname
            render_printed(expr, variant, fpath)
            manifest.append({
                "file": str(fpath),
                "filename": fname,
                "expr_id": expr["id"],
                "category": "printed",
                "variant": variant,
                "ground_truth_display": expr["display"],
                "expected_canonical": expr["expected_canonical"],
                "expected_latex": expr["expected_latex"],
                "expected_solver_behavior": expr["expected_solver_behavior"],
                "expected_result": expr["expected_result"],
            })

        if expr["has_2d_structure"]:
            fname = f"{expr['id']}__printed_2d_typeset.png"
            fpath = OUT_DIR / fname
            if render_2d_typeset(expr, fpath):
                manifest.append({
                    "file": str(fpath),
                    "filename": fname,
                    "expr_id": expr["id"],
                    "category": "printed_2d",
                    "variant": "printed_2d_typeset",
                    "ground_truth_display": expr["display"],
                    "expected_canonical": expr["expected_canonical"],
                    "expected_latex": expr["expected_latex"],
                    "expected_solver_behavior": expr["expected_solver_behavior"],
                    "expected_result": expr["expected_result"],
                })

        for variant, diff in [("handwritten_synth_clear", False), ("handwritten_synth_difficult", True)]:
            fname = f"{expr['id']}__{variant}.png"
            fpath = OUT_DIR / fname
            render_synthetic_handwritten(expr, diff, fpath)
            manifest.append({
                "file": str(fpath),
                "filename": fname,
                "expr_id": expr["id"],
                "category": "handwritten_synthetic",
                "variant": variant,
                "ground_truth_display": expr["display"],
                "expected_canonical": expr["expected_canonical"],
                "expected_latex": expr["expected_latex"],
                "expected_solver_behavior": expr["expected_solver_behavior"],
                "expected_result": expr["expected_result"],
            })

    manifest_path = OUT_DIR / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Generated {len(manifest)} POC images in {OUT_DIR}")


if __name__ == "__main__":
    main()
