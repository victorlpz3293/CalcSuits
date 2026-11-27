#!/usr/bin/env python3
"""
CalcSuits Phase 4.1 — Isolated Local Image Preprocessing POC
Applies deterministic on-device-equivalent preprocessing (grayscale, contrast
normalization, adaptive local thresholding, margin trim & padding) to degraded
images (low light, blur/noise, difficult synthetic handwriting) so before/after
OCR accuracy can be measured empirically.
"""

import json
import subprocess
import sys
from pathlib import Path

DATASET_DIR = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("/tmp/poc_ocr_dataset")
TARGET_VARIANTS = {"printed_low_light", "printed_blur_noise", "handwritten_synth_difficult"}


def preprocess_image(src: Path, dst: Path):
    # Grayscale -> contrast stretch -> local adaptive threshold (negated so ink is black on white)
    # -> gentle despeckle -> trim margins -> uniform white border -> 3-channel sRGB
    cmd = [
        "convert",
        str(src),
        "-colorspace", "Gray",
        "-auto-level",
        "-contrast-stretch", "1%x1%",
        "-negate",
        "-lat", "25x25+10%",
        "-negate",
        "-despeckle",
        "-fuzz", "10%",
        "-trim", "+repage",
        "-bordercolor", "white",
        "-border", "28",
        "-alpha", "remove",
        "-alpha", "off",
        "-type", "TrueColor",
        str(dst),
    ]
    subprocess.run(cmd, check=True)


def main():
    manifest_path = DATASET_DIR / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    pre_manifest = []
    out_dir = DATASET_DIR / "preprocessed"
    out_dir.mkdir(parents=True, exist_ok=True)

    for item in manifest:
        if item["variant"] in TARGET_VARIANTS:
            src = Path(item["file"])
            dst = out_dir / item["filename"]
            preprocess_image(src, dst)
            pre_item = dict(item)
            pre_item["file"] = str(dst)
            pre_item["preprocessed"] = True
            pre_manifest.append(pre_item)

    out_manifest = out_dir / "manifest.json"
    out_manifest.write_text(json.dumps(pre_manifest, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Preprocessed {len(pre_manifest)} degraded images into {out_dir}")


if __name__ == "__main__":
    main()
