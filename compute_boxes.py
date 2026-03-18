#!/usr/bin/env python3
import json
import math

with open('assets/pixelCoordinates.json', 'r') as f:
    coords = json.load(f)

# Sort by index
sorted_coords = sorted(coords, key=lambda c: c['index'])
xs = [c['x'] for c in sorted_coords]
ys = [c['y'] for c in sorted_coords]

print(f"Total LEDs: {len(xs)}")

# Determine vertical mirror axis (center x)
min_x = min(xs)
max_x = max(xs)
mirror_x = (min_x + max_x) / 2
print(f"X range: {min_x} - {max_x}, mirror axis: {mirror_x}")

# Find natural clustering in X direction
# Look for gaps in sorted x values
sorted_unique_x = sorted(set(xs))
x_gaps = []
prev = sorted_unique_x[0]
for x in sorted_unique_x[1:]:
    if x - prev > 30:  # gap larger than 30 pixels
        x_gaps.append((prev, x))
    prev = x

print(f"\nGaps in X (threshold 30px): {len(x_gaps)}")
for start, end in x_gaps:
    print(f"  Gap from {start} to {end} (width {end-start})")

# Use the largest gap to split left/right
if x_gaps:
    # Find the largest gap
    largest_gap = max(x_gaps, key=lambda g: g[1] - g[0])
    split_threshold = (largest_gap[0] + largest_gap[1]) / 2
    print(f"\nLargest gap at {largest_gap}, split threshold: {split_threshold}")
else:
    # Fallback: use mirror axis
    split_threshold = mirror_x

# Similarly for Y
sorted_unique_y = sorted(set(ys))
y_gaps = []
prev = sorted_unique_y[0]
for y in sorted_unique_y[1:]:
    if y - prev > 30:
        y_gaps.append((prev, y))
    prev = y

print(f"\nGaps in Y (threshold 30px): {len(y_gaps)}")
for start, end in y_gaps:
    print(f"  Gap from {start} to {end} (width {end-start})")

if y_gaps:
    largest_y_gap = max(y_gaps, key=lambda g: g[1] - g[0])
    y_split = (largest_y_gap[0] + largest_y_gap[1]) / 2
    print(f"Largest Y gap at {largest_y_gap}, split threshold: {y_split}")
else:
    y_split = (min(ys) + max(ys)) / 2

# Create 4 quadrants based on splits
quadrants = [
    {'name': 'top-left',     'x_range': (min_x, split_threshold), 'y_range': (min(ys), y_split)},
    {'name': 'top-right',    'x_range': (split_threshold, max_x), 'y_range': (min(ys), y_split)},
    {'name': 'bottom-left',  'x_range': (min_x, split_threshold), 'y_range': (y_split, max(ys))},
    {'name': 'bottom-right', 'x_range': (split_threshold, max_x), 'y_range': (y_split, max(ys))},
]

# Assign LEDs to quadrants
for q in quadrants:
    xmin, xmax = q['x_range']
    ymin, ymax = q['y_range']
    indices = []
    for idx, (x, y) in enumerate(zip(xs, ys)):
        if xmin <= x <= xmax and ymin <= y <= ymax:
            indices.append(idx)
    q['indices'] = indices

print("\nQuadrants:")
for i, q in enumerate(quadrants):
    name = q['name']
    indices = q['indices']
    xmin, xmax = q['x_range']
    ymin, ymax = q['y_range']
    print(f"{i}: {name} (x={xmin:.0f}-{xmax:.0f}, y={ymin:.0f}-{ymax:.0f}): {len(indices)} LEDs")
    # Show sample
    if len(indices) > 0:
        sample = indices[:3] if len(indices) > 3 else indices
        print(f"   Sample indices: {sample}")

# Verify mirror pairs
print("\nMirror pairs (vertical across x={mirror_x}):")
mirror_map = {}
for i, q in enumerate(quadrants):
    name = q['name']
    xmin, xmax = q['x_range']
    ymin, ymax = q['y_range']
    # Compute mirrored x range
    mirrored_xmin = 2 * mirror_x - xmax
    mirrored_xmax = 2 * mirror_x - xmin
    
    # Find matching quadrant
    for j, q2 in enumerate(quadrants):
        if i == j:
            continue
        xmin2, xmax2 = q2['x_range']
        ymin2, ymax2 = q2['y_range']
        if (abs(xmin2 - mirrored_xmin) < 20 and abs(xmax2 - mirrored_xmax) < 20 and
            abs(ymin2 - ymin) < 20 and abs(ymax2 - ymax) < 20):
            mirror_map[i] = j
            print(f"  {name} (quadrant {i}) <-> {q2['name']} (quadrant {j})")
            break

# Generate Java code
print("\n=== Java Code ===")
print("// Precomputed boxes (quadrants)")
print("private static final List<int[]> BOXES = Arrays.asList(")
for i, q in enumerate(quadrants):
    indices = q['indices']
    if i < len(quadrants) - 1:
        print(f"    new int[] {{{', '.join(map(str, indices))}}}, // {q['name']}")
    else:
        print(f"    new int[] {{{', '.join(map(str, indices))}}}  // {q['name']}")
print(");")

print("\n// Mirror mapping")
print("private static final Map<Integer, Integer> MIRROR_MAP = new HashMap<>();")
print("static {")
for i, j in mirror_map.items():
    if i < j:  # avoid duplicate entries
        print(f"    MIRROR_MAP.put({i}, {j});")
        print(f"    MIRROR_MAP.put({j}, {i});")
print("}")

print("\n// To use in render():")
print("// Random box = random.nextInt(BOXES.size());")
print("// int[] boxIndices = BOXES.get(box);")
print("// int mirrorBox = MIRROR_MAP.get(box);")
print("// int[] mirrorIndices = BOXES.get(mirrorBox);")

# Also output as JSON for debugging
import json as json_module
output = {
    'mirror_x': mirror_x,
    'y_split': y_split,
    'quadrants': [
        {
            'name': q['name'],
            'indices': q['indices'],
            'x_range': q['x_range'],
            'y_range': q['y_range']
        }
        for q in quadrants
    ],
    'mirror_map': mirror_map
}
with open('boxes_debug.json', 'w') as f:
    json_module.dump(output, f, indent=2)
print("\nDebug info written to boxes_debug.json")