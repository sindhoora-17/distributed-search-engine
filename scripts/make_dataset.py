#!/usr/bin/env python3
"""
Generate a reproducible benchmark corpus of .txt files.

Usage: python3 make_dataset.py <output_dir> <num_files> <words_per_file>
Example: python3 make_dataset.py datasets/bench 2000 1500
Produces deterministic pseudo-English so benchmark runs are comparable.
"""
import os, sys, random

VOCAB = ("distributed system network protocol server client index inverted "
         "concurrent thread latency throughput benchmark document retrieval "
         "vortex adaptation distortion moon child cluster shard partition "
         "cache memory buffer stream socket request response query term").split()

def main():
    out, n, wpf = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
    os.makedirs(out, exist_ok=True)
    rng = random.Random(42)  # fixed seed = reproducible corpus
    for i in range(n):
        words = [rng.choice(VOCAB) for _ in range(wpf)]
        with open(os.path.join(out, f"doc_{i:05d}.txt"), "w") as f:
            f.write(" ".join(words))
    print(f"Wrote {n} files x {wpf} words to {out}/")

if __name__ == "__main__":
    if len(sys.argv) != 4:
        print(__doc__); sys.exit(1)
    main()
