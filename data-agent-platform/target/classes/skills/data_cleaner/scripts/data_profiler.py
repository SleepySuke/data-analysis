#!/usr/bin/env python3
"""
Data Profiler Script
Generates data quality reports for CSV data.

Usage:
    cat data.csv | python3 data_profiler.py [--scope full|missing|duplicate|consistency]

Output: JSON quality report to stdout.
"""

import argparse
import csv
import json
import sys
from collections import Counter


def read_csv_from_stdin():
    """Read CSV data from stdin."""
    reader = csv.DictReader(sys.stdin)
    rows = list(reader)
    headers = reader.fieldnames or []
    return headers, rows


def profile_full(headers, rows):
    """Full profiling: row/col counts, types, basic stats."""
    columns = []
    for h in headers:
        values = [r.get(h, '') for r in rows]
        non_empty = [v for v in values if v.strip()]
        missing_count = len(values) - len(non_empty)
        missing_rate = f"{(missing_count / len(values) * 100):.2f}%" if values else "0.00%"

        col_type = infer_type(non_empty)
        unique = len(set(non_empty))

        col_info = {
            'name': h,
            'inferredType': col_type,
            'totalCount': len(values),
            'missingCount': missing_count,
            'missingRate': missing_rate,
            'uniqueValues': unique,
        }

        if col_type == 'numeric':
            nums = [float(v) for v in non_empty]
            if nums:
                col_info['min'] = min(nums)
                col_info['max'] = max(nums)
                col_info['mean'] = round(sum(nums) / len(nums), 4)

        col_info['sampleValues'] = non_empty[:5]
        columns.append(col_info)

    return {
        'totalRows': len(rows),
        'totalColumns': len(headers),
        'columns': columns,
    }


def profile_missing(headers, rows):
    """Missing value analysis."""
    missing_report = []
    for h in headers:
        values = [r.get(h, '') for r in rows]
        missing_indices = [i for i, v in enumerate(values) if not v.strip()]
        missing_count = len(missing_indices)
        missing_rate = (missing_count / len(values) * 100) if values else 0

        # Detect pattern: random, consecutive, or conditional
        pattern = 'none'
        if missing_count > 0:
            if missing_count == len(values):
                pattern = 'all_missing'
            elif all(missing_indices[i] + 1 == missing_indices[i + 1]
                     for i in range(len(missing_indices) - 1)):
                pattern = 'consecutive'
            else:
                pattern = 'scattered'

        missing_report.append({
            'column': h,
            'missingCount': missing_count,
            'missingRate': f"{missing_rate:.2f}%",
            'pattern': pattern,
            'missingIndices': missing_indices[:20],
        })

    complete_rows = sum(1 for r in rows if all(r.get(h, '').strip() for h in headers))
    return {
        'totalRows': len(rows),
        'completeRows': complete_rows,
        'completeRate': f"{(complete_rows / len(rows) * 100):.2f}%" if rows else "0.00%",
        'columns': missing_report,
    }


def profile_duplicate(headers, rows):
    """Duplicate analysis."""
    # Full row duplicates
    row_tuples = [tuple(r.get(h, '') for h in headers) for r in rows]
    counter = Counter(row_tuples)
    full_dupes = sum(count - 1 for count in counter.values() if count > 1)

    # Per-column duplicate info
    col_dupes = []
    for h in headers:
        values = [r.get(h, '') for r in rows]
        val_counter = Counter(values)
        dupe_count = sum(c - 1 for c in val_counter.values() if c > 1)
        col_dupes.append({
            'column': h,
            'uniqueValues': len(val_counter),
            'duplicateValues': dupe_count,
        })

    return {
        'totalRows': len(rows),
        'fullDuplicateRows': full_dupes,
        'duplicateRate': f"{(full_dupes / len(rows) * 100):.2f}%" if rows else "0.00%",
        'columnDuplicates': col_dupes,
    }


def profile_consistency(headers, rows):
    """Data consistency checks."""
    issues = []
    for h in headers:
        values = [r.get(h, '').strip() for r in rows if r.get(h, '').strip()]
        if not values:
            continue

        col_type = infer_type(values)

        # Mixed types
        numeric_count = sum(1 for v in values if is_numeric(v))
        type_ratio = numeric_count / len(values) if values else 0
        if 0.1 < type_ratio < 0.9:
            issues.append({
                'column': h,
                'issue': 'mixed_types',
                'description': f'Column contains {numeric_count}/{len(values)} numeric values',
                'severity': 'warning',
            })

        # Date format inconsistency
        date_formats = set()
        for v in values[:50]:
            if '-' in v and len(v) >= 8:
                parts = v.split('-')
                if len(parts) == 3:
                    date_formats.add(f'{len(parts[0])}-{len(parts[1])}-{len(parts[2])}')
        if len(date_formats) > 1:
            issues.append({
                'column': h,
                'issue': 'inconsistent_date_format',
                'description': f'Multiple date formats detected: {date_formats}',
                'severity': 'warning',
            })

        # Whitespace issues
        space_issues = sum(1 for v in values if v != v.strip() or '  ' in v)
        if space_issues > 0:
            issues.append({
                'column': h,
                'issue': 'whitespace',
                'description': f'{space_issues} values with whitespace issues',
                'severity': 'info',
            })

    return {
        'totalRows': len(rows),
        'totalColumns': len(headers),
        'issues': issues,
        'issueCount': len(issues),
    }


def infer_type(values):
    """Infer column type from sample values."""
    non_empty = [v for v in values if v.strip()]
    if not non_empty:
        return 'empty'
    if all(is_numeric(v) for v in non_empty):
        return 'numeric'
    if sum(1 for v in non_empty if is_date(v)) > len(non_empty) * 0.8:
        return 'date'
    return 'string'


def is_numeric(s):
    """Check if string is numeric."""
    try:
        float(s)
        return True
    except (ValueError, TypeError):
        return False


def is_date(s):
    """Basic date format check."""
    import re
    return bool(re.match(r'\d{4}[-/]\d{1,2}[-/]\d{1,2}', s))


def main():
    parser = argparse.ArgumentParser(description='Data profiler')
    parser.add_argument('--scope', default='full',
                        choices=['full', 'missing', 'duplicate', 'consistency'])
    args = parser.parse_args()

    try:
        headers, rows = read_csv_from_stdin()
    except Exception as e:
        print(json.dumps({'error': f'Failed to read CSV: {str(e)}'}))
        sys.exit(1)

    if not rows:
        print(json.dumps({'error': 'No data rows found'}))
        sys.exit(1)

    scopes = {
        'full': profile_full,
        'missing': profile_missing,
        'duplicate': profile_duplicate,
        'consistency': profile_consistency,
    }

    result = scopes[args.scope](headers, rows)
    result['scope'] = args.scope
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
