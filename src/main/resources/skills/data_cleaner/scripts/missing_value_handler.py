#!/usr/bin/env python3
"""
Missing Value Handler Script
Handles missing values in CSV data using various strategies.

Usage:
    cat data.csv | python3 missing_value_handler.py --strategy mean [--columns col1,col2]

Output: Cleaned CSV to stdout, JSON report to stderr.
"""

import argparse
import csv
import json
import sys
from collections import Counter


def read_csv_stdin():
    reader = csv.DictReader(sys.stdin)
    rows = list(reader)
    headers = reader.fieldnames or []
    return headers, rows


def get_numeric_values(rows, col):
    """Extract numeric values from a column."""
    values = []
    for r in rows:
        v = r.get(col, '').strip()
        if v:
            try:
                values.append(float(v))
            except ValueError:
                pass
    return values


def fill_mean(rows, col):
    """Fill missing with column mean."""
    nums = get_numeric_values(rows, col)
    if not nums:
        return rows, {'strategy': 'mean', 'column': col, 'fillValue': None, 'filled': 0}
    mean_val = sum(nums) / len(nums)
    filled = 0
    for r in rows:
        if not r.get(col, '').strip():
            r[col] = str(round(mean_val, 4))
            filled += 1
    return rows, {'strategy': 'mean', 'column': col, 'fillValue': round(mean_val, 4), 'filled': filled}


def fill_median(rows, col):
    """Fill missing with column median."""
    nums = sorted(get_numeric_values(rows, col))
    if not nums:
        return rows, {'strategy': 'median', 'column': col, 'fillValue': None, 'filled': 0}
    n = len(nums)
    median = (nums[n // 2 - 1] + nums[n // 2]) / 2 if n % 2 == 0 else nums[n // 2]
    filled = 0
    for r in rows:
        if not r.get(col, '').strip():
            r[col] = str(round(median, 4))
            filled += 1
    return rows, {'strategy': 'median', 'column': col, 'fillValue': round(median, 4), 'filled': filled}


def fill_mode(rows, col):
    """Fill missing with column mode (most frequent value)."""
    values = [r.get(col, '').strip() for r in rows if r.get(col, '').strip()]
    if not values:
        return rows, {'strategy': 'mode', 'column': col, 'fillValue': None, 'filled': 0}
    counter = Counter(values)
    mode_val = counter.most_common(1)[0][0]
    filled = 0
    for r in rows:
        if not r.get(col, '').strip():
            r[col] = mode_val
            filled += 1
    return rows, {'strategy': 'mode', 'column': col, 'fillValue': mode_val, 'filled': filled}


def fill_forward(rows, col):
    """Forward fill: use previous non-empty value."""
    filled = 0
    last_val = ''
    for r in rows:
        if r.get(col, '').strip():
            last_val = r[col]
        else:
            if last_val:
                r[col] = last_val
                filled += 1
    return rows, {'strategy': 'forward_fill', 'column': col, 'filled': filled}


def fill_backward(rows, col):
    """Backward fill: use next non-empty value."""
    filled = 0
    next_val = ''
    for r in reversed(rows):
        if r.get(col, '').strip():
            next_val = r[col]
        else:
            if next_val:
                r[col] = next_val
                filled += 1
    return rows, {'strategy': 'backward_fill', 'column': col, 'filled': filled}


def drop_missing(rows, col):
    """Drop rows where column is missing."""
    original = len(rows)
    rows = [r for r in rows if r.get(col, '').strip()]
    dropped = original - len(rows)
    return rows, {'strategy': 'drop', 'column': col, 'dropped': dropped}


STRATEGIES = {
    'mean': fill_mean,
    'median': fill_median,
    'mode': fill_mode,
    'forward_fill': fill_forward,
    'backward_fill': fill_backward,
    'drop': drop_missing,
}


def main():
    parser = argparse.ArgumentParser(description='Missing value handler')
    parser.add_argument('--strategy', required=True,
                        choices=list(STRATEGIES.keys()),
                        help='Fill strategy')
    parser.add_argument('--columns', default='',
                        help='Comma-separated column names (default: all with missing)')
    args = parser.parse_args()

    try:
        headers, rows = read_csv_stdin()
    except Exception as e:
        print(json.dumps({'error': f'Failed to read CSV: {str(e)}'}))
        sys.exit(1)

    if not rows:
        print(json.dumps({'error': 'No data rows'}))
        sys.exit(1)

    # Determine target columns
    if args.columns:
        target_cols = [c.strip() for c in args.columns.split(',') if c.strip()]
    else:
        target_cols = [h for h in headers
                       if any(not r.get(h, '').strip() for r in rows)]

    strategy_fn = STRATEGIES[args.strategy]
    reports = []

    for col in target_cols:
        if col not in headers:
            reports.append({'column': col, 'error': 'Column not found'})
            continue
        rows, report = strategy_fn(rows, col)
        reports.append(report)

    # Output cleaned CSV to stdout
    writer = csv.DictWriter(sys.stdout, fieldnames=headers)
    writer.writeheader()
    writer.writerows(rows)

    # Output report to stderr
    report = {
        'strategy': args.strategy,
        'columnsProcessed': len(target_cols),
        'reports': reports,
        'rowsAfterProcessing': len(rows),
    }
    print(json.dumps(report, ensure_ascii=False), file=sys.stderr)


if __name__ == '__main__':
    main()
