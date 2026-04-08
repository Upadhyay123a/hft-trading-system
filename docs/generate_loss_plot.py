#!/usr/bin/env python3
"""
Generate an AdvancedML loss CSV and PNG from the backtest log.

Usage:
  python docs/generate_loss_plot.py

Produces:
  - docs/advml_loss.csv
  - docs/advml_loss.png

Requires: matplotlib (pip install matplotlib) and pandas (optional)
"""
import re
import os
import sys
from pathlib import Path

LOG = Path('logs/all_backtests_run_with_fetch.log')
OUT_CSV = Path('docs/advml_loss.csv')
OUT_PNG = Path('docs/advml_loss.png')

if not LOG.exists():
    print(f"Log file not found: {LOG.resolve()}")
    sys.exit(1)

pattern = re.compile(r"Avg Loss:\s*([0-9]*\.?[0-9]+)")
losses = []
with LOG.open('r', encoding='utf-8', errors='ignore') as fh:
    for line in fh:
        m = pattern.search(line)
        if m:
            losses.append(float(m.group(1)))

if not losses:
    print('No "Avg Loss" entries found in log.')
    sys.exit(1)

# Ensure docs dir exists
OUT_CSV.parent.mkdir(parents=True, exist_ok=True)

with OUT_CSV.open('w', encoding='utf-8') as fh:
    fh.write('index,loss\n')
    for i, v in enumerate(losses, start=1):
        fh.write(f"{i},{v:.6f}\n")

try:
    import matplotlib.pyplot as plt
except Exception as e:
    print('matplotlib is required to plot. Install with: pip install matplotlib')
    print(f'CSV written to: {OUT_CSV.resolve()}')
    sys.exit(0)

plt.figure(figsize=(8,3))
plt.plot(range(1, len(losses)+1), losses, marker='o', linewidth=1)
plt.title('AdvancedML reported batch losses')
plt.xlabel('batch index')
plt.ylabel('avg loss')
plt.grid(alpha=0.3)
plt.tight_layout()
plt.savefig(OUT_PNG, dpi=150)
print(f'Wrote CSV: {OUT_CSV.resolve()}')
print(f'Wrote PNG: {OUT_PNG.resolve()}')
