#!/usr/bin/env bash
# Fetches PaddleOCR's PP-OCRv6 "small" text detector and recognizer, as RapidOCR publishes them in
# ONNX form (Apache 2.0), checks them, and stores them in the Android app's assets: the recognizer cut
# down to Latin script, the weights at half precision, and the characters the recognizer writes.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
out="$here/../androidApp/src/main/assets/ocr"
base="https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/v3.10.0/onnx/PP-OCRv6"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

fetch() { # name, url, sha256
    curl -fsSL -o "$work/$1" "$2"
    echo "$3  $work/$1" | sha256sum -c --quiet
}
fetch det.onnx "$base/det/PP-OCRv6_det_small.onnx" 090f04abcd9d9a7498bc4ebf677e4cb9bdce1fe4197ddb7e529f1ef44e1ff94f
fetch rec.onnx "$base/rec/PP-OCRv6_rec_small.onnx" 6f327246b50388f3c176ae304bd95767ea6dc0c9ae92153ef8cbe210b3c14884

python3 -m venv "$work/venv"
"$work/venv/bin/pip" install -q onnx==1.23.2
mkdir -p "$out"
"$work/venv/bin/python" -I - "$work" "$out" <<'PY'
import sys
import numpy as np
import onnx
from onnx import TensorProto, helper, numpy_helper

work, out = sys.argv[1], sys.argv[2]

def half_weights(model):
    """Stores the weights at half precision, each turned back to single precision as the model loads
    (ONNX Runtime folds the casts away): half the size, while all arithmetic stays single precision.
    Networks converted to half precision throughout read nothing on ARM, which computes in it."""
    graph = model.graph
    weights, casts = [], []
    for weight in graph.initializer:
        if weight.data_type == TensorProto.FLOAT and int(np.prod(weight.dims)) >= 16:
            half = numpy_helper.from_array(numpy_helper.to_array(weight).astype(np.float16), weight.name + "_half")
            weights.append(half)
            casts.append(helper.make_node("Cast", [half.name], [weight.name], to=TensorProto.FLOAT, name=weight.name + "_cast"))
        else:
            weights.append(weight)
    del graph.initializer[:]
    graph.initializer.extend(weights)
    nodes = list(graph.node)
    del graph.node[:]
    graph.node.extend(casts + nodes)
    onnx.checker.check_model(model)
    return model

def latin(c):
    """Whether receipts in Latin script use [c]: letters of the European languages written in it, digits,
    punctuation, currency signs."""
    o = ord(c)
    return (0x20 <= o <= 0x7E or 0xA0 <= o <= 0x17F or 0x218 <= o <= 0x21B or 0x2010 <= o <= 0x2044
            or 0x20A0 <= o <= 0x20C0 or c in "℃℉№™")

def latin_only(model, characters):
    """The recognizer writing only Latin script. It knows 18 708 characters, most of them Chinese and
    Japanese, and scores every one at every step of every line; keeping the few hundred of Latin script
    makes it a fifth of the size and much faster on phones. Its outputs are the gap between letters,
    the characters, then a space."""
    keep = [i for i, c in enumerate(characters) if latin(c)]
    columns = [0] + [i + 1 for i in keep] + [len(characters) + 1]
    for weight in model.graph.initializer:
        if weight.dims and weight.dims[-1] == len(characters) + 2:
            values = numpy_helper.to_array(weight)[..., columns]
            weight.CopyFrom(numpy_helper.from_array(np.ascontiguousarray(values), weight.name))
    for value in list(model.graph.value_info) + list(model.graph.output):
        for dim in value.type.tensor_type.shape.dim:
            if dim.dim_value == len(characters) + 2:
                dim.dim_value = len(columns)
    return model, [characters[i] for i in keep]

for name in ("det.onnx", "rec.onnx"):
    model = onnx.load(f"{work}/{name}")
    if name == "rec.onnx":
        # Kept apart from the model: ONNX Runtime's Java API garbles characters beyond the 16-bit range
        # (emoji) in a model's metadata, where they came.
        characters = next(p.value for p in model.metadata_props if p.key == "character").split("\n")
        model, characters = latin_only(model, characters)
        del model.metadata_props[:]
        with open(f"{out}/characters.txt", "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(characters))
    onnx.save(half_weights(model), f"{out}/{name}")
PY
ls -l "$out"
