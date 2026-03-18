#!/usr/bin/env python3
import json
import sys

with open('assets/pixelCoordinates.json', 'r') as f:
    coords = json.load(f)

print(f"Total LEDs: {len(coords)}")

# Find min/max
xs = [c['x'] for c in coords]
ys = [c['y'] for c in coords]
print(f"X range: {min(xs)} - {max(xs)}")
print(f"Y range: {min(ys)} - {max(ys)}")

# Compute center
center_x = (min(xs) + max(xs)) / 2
center_y = (min(ys) + max(ys)) / 2
print(f"Center: ({center_x:.1f}, {center_y:.1f})")

# Group by approximate grid? Let's see distribution
from collections import defaultdict
# Round to nearest 50 maybe
grid = defaultdict(list)
for c in coords:
    gx = round(c['x'] / 50) * 50
    gy = round(c['y'] / 50) * 50
    grid[(gx, gy)].append(c['index'])

print(f"Number of grid cells (50px): {len(grid)}")
# Print top few cells
for (gx, gy), indices in sorted(grid.items(), key=lambda kv: len(kv[1]), reverse=True)[:10]:
    print(f"  Cell ({gx},{gy}): {len(indices)} LEDs")

# Check symmetry: for each point, see if there's a point mirrored across center
mirror_tolerance = 10
mirrored_count = 0
for c in coords:
    mx = 2 * center_x - c['x']
    my = 2 * center_y - c['y']
    # Find closest point
    found = False
    for c2 in coords:
        if abs(c2['x'] - mx) < mirror_tolerance and abs(c2['y'] - my) < mirror_tolerance:
            found = True
            break
    if found:
        mirrored_count += 1

print(f"Points with mirror counterpart within {mirror_tolerance}px: {mirrored_count}/{len(coords)}")

# Perhaps there are "boxes" - clusters with similar x or y? Let's try clustering by x values
sorted_x = sorted(xs)
# Look for gaps
prev = sorted_x[0]
gaps = []
for x in sorted_x[1:]:
    if x - prev > 20:  # gap larger than 20 pixels
        gaps.append((prev, x))
    prev = x
print(f"Gaps in X larger than 20px: {len(gaps)}")
for start, end in gaps[:5]:
    print(f"  Gap from {start} to {end} (width {end-start})")

# Same for Y
sorted_y = sorted(ys)
prev = sorted_y[0]
gaps_y = []
for y in sorted_y[1:]:
    if y - prev > 20:
        gaps_y.append((prev, y))
    prev = y
print(f"Gaps in Y larger than 20px: {len(gaps_y)}")
for start, end in gaps_y[:5]:
    print(f"  Gap from {start} to {end} (width {end-start})")

# Print first few coordinates
print("\nFirst 5 coordinates:")
for i, c in enumerate(coords[:5]):
    print(f"  {i}: index {c['index']} at ({c['x']}, {c['y']})")