#!/usr/bin/env python3
"""
Outlier Detector Script
Detects and handles outliers using IQR or Z-score methods.

Usage:
    cat data.csv | python3 outlier_detector.py [--method iqr|zscore] [--threshold 1.5] [--action detect|remove|cap]

Output: JSON outlier report or cleaned CSV to stdout.
"""

import argparse
import csv
import json
import sys
import math


def read_csv_stdin():
    reader = csv.DictReader(sys.stdin)
    rows = list(reader)
    headers = reader.fieldnames or []
    return headers, rows


def get_numeric_columns(headers, rows):
    """Identify numeric columns."""
    numeric_cols = []
    for h in headers:
        values = [r.get(h, '').strip() for r in rows if r.get(h, '').strip()]
        if not values:
            continue
        numeric_count = 0
        for v in values:
            try:
                float(v)
                numeric_count += 1
            except ValueError:
                pass
        if numeric_count / len(values) > 0.8:
            numeric_cols.append(h)
    return numeric_cols


def get_values(rows, col):
    """Get numeric values from column."""
    values = []
    for r in rows:
        v = r.get(col, '').strip()
        if v:
            try:
                values.append(float(v))
            except ValueError:
                pass
    return values


def calc_stats(values):
    """Calculate basic statistics."""
    n = len(values)
    if n == 0:
        return {}
    sorted_vals = sorted(values)
    mean = sum(values) / n
    variance = sum((v - mean) ** 2 for v in values) / n
    stddev = math.sqrt(variance) if variance > 0 else 0
    q1 = sorted_vals[n // 4]
    q3 = sorted_vals[3 * n // 4]
    median = (sorted_vals[n // 2 - 1] + sorted_vals[n // 2]) / 2 if n % 2 == 0 else sorted_vals[n // 2]
    return {'mean': mean, 'stddev': stddev, 'median': median, 'q1': q1, 'q3': q3, 'iqr': q3 - q1}


def detect_iqr(values, threshold=1.5):
    """Detect outliers using IQR method."""
    stats = calc_stats(values)
    if not stats or stats['iqr'] == 0:
        return [], stats
    lower = stats['q1'] - threshold * stats['iqr']
    upper = stats['q3'] + threshold * stats['iqr']
    outliers = [(i, v) for i, v in enumerate(values) if v < lower or v > upper]
    return outliers, stats


def detect_zscore(values, threshold=3.0):
    """Detect outliers using Z-score method."""
    stats = calc_stats(values)
    if not stats or stats['stddev'] == 0:
        return [], stats
    outliers = [(i, v) for i, v in enumerate(values)
                if abs((v - stats['mean']) / stats['stddev']) > threshold]
    return outliers, stats


def main():
    parser = argparse.ArgumentParser(description='Outlier detector')
    parser.add_argument('--method', default='iqr', choices=['iqr', 'zscore'])
    parser.add_argument('--threshold', type=float, default=1.5,
                        help='IQR multiplier or Z-score threshold')
    parser.add_argument('--action', default='detect',
                        choices=['detect', 'remove', 'cap'])
    args = parser.parse_args()

    try:
        headers, rows = read_csv_stdin()
    except Exception as e:
        print(json.dumps({'error': f'Failed to read CSV: {str(e)}'}))
        sys.exit(1)

    if not rows:
        print(json.dumps({'error': 'No data rows'}))
        sys.exit(1)

    numeric_cols = get_numeric_columns(headers, rows)
    if not numeric_cols:
        print(json.dumps({'error': 'No numeric columns found'}))
        sys.exit(1)

    detect_fn = detect_iqr if args.method == 'iqr' else detect_zscore
    threshold = args.threshold

    results = []
    outlier_row_indices = set()

    for col in numeric_cols:
        values = get_values(rows, col)
        outliers, stats = detect_fn(values, threshold)
        col_result = {
            'column': col,
            'method': args.method,
            'threshold': threshold,
            'stats': {k: round(v, 4) for k, v in stats.items()} if stats else {},
            'outlierCount': len(outliers),
            'outlierRate': f"{(len(outliers) / len(values) * 100):.2f}%" if values else "0.00%",
            'outlierValues': [round(v, 4) for _, v in outliers[:20]],
        }
        results.append(col_result)

        if args.action in ('remove', 'cap'):
            outlier_set = set(i for i, _ in outliers)
            if args.action == 'remove':
                outlier_row_indices.update(outlier_set)
            elif args.action == 'cap' and stats:
                lower = stats['q1'] - threshold * stats['iqr'] if args.method == 'iqr' \
                    else stats['mean'] - threshold * stats['stddev']
                upper = stats['q3'] + threshold * stats['iqr'] if args.method == 'iqr' \
                    else stats['mean'] + threshold * stats['stddev']
                for i in outlier_set:
                    val = values[i]
                    if val < lower:
                        rows[i][col] = str(round(lower, 4))
                    elif val > upper:
                        rows[i][col] = str(round(upper, 4))

    if args.action == 'remove':
        rows = [r for i, r in enumerate(rows) if i not in outlier_row_indices]

    if args.action == 'detect':
        output = {
            'method': args.method,
            'threshold': threshold,
            'columns': results,
            'totalOutliers': sum(r['outlierCount'] for r in results),
        }
        print(json.dumps(output, ensure_ascii=False, indent=2))
    else:
        writer = csv.DictWriter(sys.stdout, fieldnames=headers)
        writer.writeheader()
        writer.writerows(rows)
        report = {
            'action': args.action,
            'method': args.method,
            'columns': results,
            'rowsAfterProcessing': len(rows),
        }
        print(json.dumps(report, ensure_ascii=False), file=sys.stderr)


if __name__ == '__main__':
    main()
