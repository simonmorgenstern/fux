#!/usr/bin/env python3
import json
import math

with open('assets/pixelCoordinates.json', 'r') as f:
    coords = json.load(f)

points = [(c['x'], c['y'], c['index']) for c in coords]

def distance(p1, p2):
    return math.hypot(p1[0]-p2[0], p1[1]-p2[1])

# Simple greedy clustering
threshold = 30  # pixels
clusters = []
visited = set()

for i, p in enumerate(points):
    if i in visited:
        continue
    # start new cluster
    cluster = [p]
    visited.add(i)
    # expand
    queue = [i]
    while queue:
        idx = queue.pop()
        px, py, _ = points[idx]
        for j, q in enumerate(points):
            if j in visited:
                continue
            qx, qy, _ = q
            if distance((px, py), (qx, qy)) < threshold:
                cluster.append(q)
                visited.add(j)
                queue.append(j)
    clusters.append(cluster)

print(f"Found {len(clusters)} clusters with threshold {threshold}px")
for i, cluster in enumerate(clusters[:10]):  # limit output
    xs = [p[0] for p in cluster]
    ys = [p[1] for p in cluster]
    indices = [p[2] for p in cluster]
    print(f"Cluster {i}: {len(cluster)} LEDs, x={min(xs)}-{max(xs)}, y={min(ys)}-{max(ys)}")
    # Print first few indices
    if len(indices) > 8:
        print(f"  Indices: {indices[:4]} ... {indices[-4:]}")
    else:
        print(f"  Indices: {indices}")
    # Compute bounding box center
    center_x = (min(xs) + max(xs)) / 2
    center_y = (min(ys) + max(ys)) / 2
    print(f"  Center: ({center_x:.1f}, {center_y:.1f})")

# Check if clusters are roughly symmetric
overall_center_x = 230.0
overall_center_y = 300.0
print("\nChecking symmetry across overall center...")
for i, cluster in enumerate(clusters[:5]):
    # compute cluster center
    xs = [p[0] for p in cluster]
    ys = [p[1] for p in cluster]
    cx = sum(xs) / len(xs)
    cy = sum(ys) / len(ys)
    # mirror
    mx = 2 * overall_center_x - cx
    my = 2 * overall_center_y - cy
    # find closest cluster
    best_dist = float('inf')
    best_j = -1
    for j, cluster2 in enumerate(clusters):
        if i == j:
            continue
        xs2 = [p[0] for p in cluster2]
        ys2 = [p[1] for p in cluster2]
        cx2 = sum(xs2) / len(xs2)
        cy2 = sum(ys2) / len(ys2)
        d = math.hypot(cx2 - mx, cy2 - my)
        if d < best_dist:
            best_dist = d
            best_j = j
    if best_dist < 50:
        print(f"Cluster {i} mirrored matches cluster {best_j} (dist {best_dist:.1f})")
    else:
        print(f"Cluster {i} has no mirror match (closest dist {best_dist:.1f})")

# If we have many clusters, maybe we need larger threshold
if len(clusters) > 20:
    print(f"\nToo many clusters ({len(clusters)}). Trying larger threshold (60px)...")
    # Re-run with larger threshold
    threshold = 60
    clusters = []
    visited = set()
    for i, p in enumerate(points):
        if i in visited:
            continue
        cluster = [p]
        visited.add(i)
        queue = [i]
        while queue:
            idx = queue.pop()
            px, py, _ = points[idx]
            for j, q in enumerate(points):
                if j in visited:
                    continue
                qx, qy, _ = q
                if distance((px, py), (qx, qy)) < threshold:
                    cluster.append(q)
                    visited.add(j)
                    queue.append(j)
        clusters.append(cluster)
    print(f"Found {len(clusters)} clusters with threshold {threshold}px")
    for i, cluster in enumerate(clusters[:10]):
        xs = [p[0] for p in cluster]
        ys = [p[1] for p in cluster]
        print(f"Cluster {i}: {len(cluster)} LEDs, x={min(xs)}-{max(xs)}, y={min(ys)}-{max(ys)}")