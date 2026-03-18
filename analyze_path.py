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
print("\nFirst 30 points (index, x, y, direction angle):")
angles = []
for i in range(len(xs)-1):
    dx = xs[i+1] - xs[i]
    dy = ys[i+1] - ys[i]
    angle = math.atan2(dy, dx) if abs(dx) > 0.1 or abs(dy) > 0.1 else 0
    angles.append(angle)
    if i < 30:
        deg = math.degrees(angle)
        print(f"  {i:3d}: ({xs[i]:6.1f}, {ys[i]:6.1f}) -> angle {deg:6.1f}°")

# Look for significant angle changes (> 45 degrees)
print("\nSignificant direction changes (>45°):")
for i in range(len(angles)-1):
    delta = abs(angles[i+1] - angles[i])
    if delta > math.pi:
        delta = 2*math.pi - delta
    if delta > math.pi/4:
        deg_delta = math.degrees(delta)
        print(f"  At index {i+1}: angle change {deg_delta:.1f}° (from {math.degrees(angles[i]):.1f}° to {math.degrees(angles[i+1]):.1f}°)")

# Group points into segments of similar direction
segments = []
current_seg = [0]
current_angle = angles[0]
angle_tol = math.radians(20)  # 20 degrees tolerance
for i in range(1, len(angles)):
    delta = abs(angles[i] - current_angle)
    if delta > math.pi:
        delta = 2*math.pi - delta
    if delta < angle_tol:
        current_seg.append(i)
    else:
        segments.append(current_seg)
        current_seg = [i]
        current_angle = angles[i]
segments.append(current_seg)

print(f"\nDetected {len(segments)} straight segments:")
for seg_idx, seg in enumerate(segments[:10]):  # limit output
    start = seg[0]
    end = seg[-1]
    length = len(seg)
    avg_angle = math.degrees(sum(angles[i] for i in seg) / length)
    print(f"  Segment {seg_idx}: indices {start}-{end} ({length} LEDs), avg angle {avg_angle:.1f}°")

# Try to find rectangles: look for 4 segments forming approximate rectangle
# For now, just note if there are segments with ~90° difference
print("\nChecking for orthogonal segments:")
ortho_pairs = []
for i in range(len(segments)):
    for j in range(i+1, len(segments)):
        # compute average angle of each segment
        avg_i = sum(angles[idx] for idx in segments[i]) / len(segments[i])
        avg_j = sum(angles[idx] for idx in segments[j]) / len(segments[j])
        delta = abs(avg_i - avg_j)
        if delta > math.pi:
            delta = 2*math.pi - delta
        # check if close to 90 degrees
        if abs(delta - math.pi/2) < math.radians(30):
            ortho_pairs.append((i, j, delta))
            if len(ortho_pairs) <= 5:
                print(f"  Segments {i} and {j} are orthogonal ({math.degrees(delta):.1f}°)")
if len(ortho_pairs) > 5:
    print(f"  ... and {len(ortho_pairs)-5} more orthogonal pairs")

print("\nDone.")