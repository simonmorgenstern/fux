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

# Compute direction angles
angles = []
for i in range(len(xs)-1):
    dx = xs[i+1] - xs[i]
    dy = ys[i+1] - ys[i]
    angle = math.atan2(dy, dx) if abs(dx) > 0.1 or abs(dy) > 0.1 else 0
    angles.append(angle)

# Find corners: where direction changes significantly (> 60 degrees)
corners = []
corner_tolerance = math.radians(60)
for i in range(1, len(angles)-1):
    # Change from i-1 to i
    delta1 = abs(angles[i] - angles[i-1])
    if delta1 > math.pi:
        delta1 = 2*math.pi - delta1
    delta2 = abs(angles[i+1] - angles[i])
    if delta2 > math.pi:
        delta2 = 2*math.pi - delta2
    if delta1 > corner_tolerance or delta2 > corner_tolerance:
        corners.append(i)

print(f"Found {len(corners)} corner indices")
print("First 10 corners:", corners[:10])

# Try to group corners into rectangles
# Look for sequences of 4 corners that form roughly rectangular shape
boxes = []
used = set()
for i in range(len(corners)-3):
    c0 = corners[i]
    c1 = corners[i+1]
    c2 = corners[i+2]
    c3 = corners[i+3]
    
    # Check if these corners could be a rectangle
    # 1. They should be in order along the strip
    if not (c0 < c1 < c2 < c3):
        continue
        
    # 2. Compute segment lengths
    def segment_length(start_idx, end_idx):
        total = 0
        for j in range(start_idx, end_idx):
            dx = xs[j+1] - xs[j]
            dy = ys[j+1] - ys[j]
            total += math.hypot(dx, dy)
        return total
    
    len1 = segment_length(c0, c1)
    len2 = segment_length(c1, c2)
    len3 = segment_length(c2, c3)
    len4 = segment_length(c3, c0)  # need to wrap around? might not be contiguous
    
    # 3. Check if opposite sides are roughly equal length (within 30%)
    ratio12 = max(len1, len2) / min(len1, len2) if min(len1, len2) > 0 else 999
    ratio34 = max(len3, len4) / min(len3, len4) if min(len3, len4) > 0 else 999
    
    # Also check if the four points form a convex shape
    # For now, just accept if all corners are unused and segment lengths are reasonable
    if len1 > 10 and len2 > 10 and len3 > 10 and len4 > 10:  # at least 10px per side
        # Check if any corner already used
        if any(c in used for c in [c0, c1, c2, c3]):
            continue
        boxes.append((c0, c1, c2, c3))
        used.update([c0, c1, c2, c3])
        # Skip ahead to avoid overlapping boxes
        i += 3

print(f"\nDetected {len(boxes)} potential boxes")
for i, (c0, c1, c2, c3) in enumerate(boxes[:5]):  # limit output
    print(f"Box {i}: corners at indices {c0}, {c1}, {c2}, {c3}")
    print(f"  Coordinates:")
    print(f"    {c0}: ({xs[c0]}, {ys[c0]})")
    print(f"    {c1}: ({xs[c1]}, {ys[c1]})")
    print(f"    {c2}: ({xs[c2]}, {ys[c2]})")
    print(f"    {c3}: ({xs[c3]}, {ys[c3]})")

# Alternative approach: cluster by spatial position
print("\n--- Spatial clustering approach ---")
# Let's try to find groups of LEDs that are close together and form a convex hull
from scipy.spatial import ConvexHull
import numpy as np

# Since we don't have scipy installed in workspace, do simpler approach
# Just find bounding boxes by scanning for gaps in x and y
# From earlier analysis, there are gaps in X at ~230 and Y at ~300
x_gaps = [(0, 230), (230, 458)]  # approximate from earlier
y_gaps = [(0, 300), (300, 660)]

# Group LEDs into quadrants
quadrants = []
for xmin, xmax in x_gaps:
    for ymin, ymax in y_gaps:
        indices = []
        for idx, (x, y) in enumerate(zip(xs, ys)):
            if xmin <= x <= xmax and ymin <= y <= ymax:
                indices.append(idx)
        if indices:
            quadrants.append({
                'xrange': (xmin, xmax),
                'yrange': (ymin, ymax),
                'indices': indices
            })

print(f"Found {len(quadrants)} spatial quadrants")
for i, q in enumerate(quadrants):
    xmin, xmax = q['xrange']
    ymin, ymax = q['yrange']
    indices = q['indices']
    print(f"Quadrant {i}: x={xmin}-{xmax}, y={ymin}-{ymax}, {len(indices)} LEDs")
    # Get first and last few indices
    if len(indices) > 10:
        print(f"  Indices: {indices[:5]} ... {indices[-5:]}")
    else:
        print(f"  Indices: {indices}")

# Now find vertical mirror pairs
mirror_x = 229.0  # approximate center from earlier
print(f"\n--- Vertical mirror pairs (mirror axis x={mirror_x}) ---")
mirror_pairs = []
for i, q in enumerate(quadrants):
    xmin, xmax = q['xrange']
    ymin, ymax = q['yrange']
    # Compute mirrored quadrant
    mirrored_xmin = 2 * mirror_x - xmax
    mirrored_xmax = 2 * mirror_x - xmin
    # Find matching quadrant
    for j, q2 in enumerate(quadrants):
        if i == j:
            continue
        xmin2, xmax2 = q2['xrange']
        ymin2, ymax2 = q2['yrange']
        # Check if mirrored bounds match (within tolerance)
        if (abs(xmin2 - mirrored_xmin) < 20 and abs(xmax2 - mirrored_xmax) < 20 and
            abs(ymin2 - ymin) < 20 and abs(ymax2 - ymax) < 20):
            mirror_pairs.append((i, j, q['indices'], q2['indices']))
            print(f"Quadrant {i} mirrors to quadrant {j}")
            break

# Generate Java code for boxes
print("\n--- Java code generation ---")
print("// Precomputed boxes (quadrants)")
print("private static final List<int[]> BOXES = Arrays.asList(")
for i, q in enumerate(quadrants):
    indices = q['indices']
    # Convert to array literal
    if i < len(quadrants) - 1:
        print(f"    new int[] {{{', '.join(map(str, indices))}}}, // Box {i}")
    else:
        print(f"    new int[] {{{', '.join(map(str, indices))}}}  // Box {i}")
print(");")

print("\n// Mirror mapping: box index -> its mirror box index")
print("private static final Map<Integer, Integer> MIRROR_MAP = new HashMap<>();")
print("static {")
for i, j, _, _ in mirror_pairs:
    print(f"    MIRROR_MAP.put({i}, {j});")
    print(f"    MIRROR_MAP.put({j}, {i});")
print("}")