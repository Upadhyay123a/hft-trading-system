#!/usr/bin/env python3
"""
Generate a PNG loss plot from the advml_loss.csv file.

Usage:
  python docs/plot_loss_from_csv.py

Produces:
  - docs/advml_loss.png
"""
import sys
from pathlib import Path

CSV_FILE = Path('docs/advml_loss.csv')
OUT_PNG = Path('docs/advml_loss.png')

if not CSV_FILE.exists():
    print(f"CSV file not found: {CSV_FILE.resolve()}")
    sys.exit(1)

try:
    import matplotlib.pyplot as plt
except Exception as e:
    print(f'matplotlib is required. Install with: pip install matplotlib')
    sys.exit(1)

# Read CSV
indices = []
losses = []
with CSV_FILE.open('r', encoding='utf-8') as fh:
    next(fh)  # Skip header
    for line in fh:
        parts = line.strip().split(',')
        if len(parts) == 2:
            try:
                idx = int(parts[0])
                loss = float(parts[1])
                indices.append(idx)
                losses.append(loss)
            except ValueError:
                continue

if not losses:
    print('No loss data found in CSV.')
    sys.exit(1)

# Generate plot
plt.figure(figsize=(12, 6))
plt.plot(indices, losses, marker='o', linestyle='-', linewidth=2, markersize=4, color='#1f77b4')
plt.xlabel('Epoch', fontsize=12, fontweight='bold')
plt.ylabel('Loss', fontsize=12, fontweight='bold')
plt.title('AdvancedML LSTM Training Loss Over Epochs', fontsize=14, fontweight='bold')
plt.grid(True, alpha=0.3)
plt.tight_layout()

OUT_PNG.parent.mkdir(parents=True, exist_ok=True)
plt.savefig(OUT_PNG, dpi=150, bbox_inches='tight')
print(f'PNG plot saved to: {OUT_PNG.resolve()}')
plt.close()
