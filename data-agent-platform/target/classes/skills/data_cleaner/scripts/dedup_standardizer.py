#!/usr/bin/env python3
"""
Dedup and Standardizer Script
Removes duplicates and standardizes data formats.

Usage:
    cat data.csv | python3 dedup_standardizer.py [--dedup-columns col1,col2] [--dedup-strategy first|last] [--standardize date|string|numeric|all|none]

Output: Cleaned CSV to stdout, JSON report to stderr.
"""

import argparse
import csv
import json
import sys
import re


def read_csv_stdin():
    reader = csv.DictReader(sys.stdin)
    rows = list(reader)
    headers = reader.fieldnames or []
    return headers, rows


def deduplicate(rows, dedup_columns, strategy):
    """Remove duplicate rows based on specified columns."""
    seen = {}
    result = []
    dupes = 0

    for i, r in enumerate(rows):
        key = tuple(r.get(c, '') for c in dedup_columns)
        if key in seen:
            dupes += 1
            if strategy == 'last':
                result[seen[key]] = r
        else:
            seen[key] = len(result)
            result.append(r)

    return result, dupes


def standardize_dates(rows, headers):
    """Standardize date formats to YYYY-MM-DD."""
    date_pattern = re.compile(r'^\d{4}[-/]\d{1,2}[-/]\d{1,2}')
    changes = 0

    for h in headers:
        values = [r.get(h, '').strip() for r in rows]
        date_count = sum(1 for v in values if v and date_pattern.match(v))
        if date_count < len(values) * 0.5:
            continue

        for r in rows:
            v = r.get(h, '').strip()
            if not v:
                continue
            # Normalize: replace / with -
            new_v = re.sub(r'(\d{4})/(\d{1,2})/(\d{1,2})', r'\1-\2-\3', v)
            # Pad month/day to 2 digits
            new_v = re.sub(r'-(\d)-', r'-0\1-', new_v)
            new_v = re.sub(r'-(\d)$', r'-0\1', new_v)
            new_v = re.sub(r'^(\d{4})-(\d)-', r'\1-0\2-', new_v)
            if new_v != v:
                r[h] = new_v
                changes += 1

    return changes


def standardize_strings(rows, headers):
    """Standardize string values: trim, normalize whitespace, fullwidth to halfwidth."""
    changes = 0
    fullwidth_map = str.maketrans(
        '０１２３４５６７８９ＡＢＣＤＥＦＧＨＩＪＫＬＭＮＯＰＱＲＳＴＵＶＷＸＹＺａｂｃｄｅｆｇｈｉｊｋｌｍｎｏｐｑｒｓｔｕｖｗｘｙｚ',
        '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'
    )

    for r in rows:
        for h in headers:
            v = r.get(h, '').strip()
            if not v:
                continue
            new_v = v.translate(fullwidth_map)
            new_v = re.sub(r'\s+', ' ', new_v).strip()
            if new_v != v:
                r[h] = new_v
                changes += 1

    return changes


def standardize_numeric(rows, headers):
    """Standardize numeric values: remove commas, pad decimals."""
    changes = 0
    for h in headers:
        values = [r.get(h, '').strip() for r in rows if r.get(h, '').strip()]
        numeric_count = 0
        for v in values:
            try:
                float(v.replace(',', ''))
                numeric_count += 1
            except ValueError:
                pass
        if numeric_count < len(values) * 0.5:
            continue

        for r in rows:
            v = r.get(h, '').strip()
            if not v:
                continue
            cleaned = v.replace(',', '')
            try:
                float(cleaned)
                if cleaned != v:
                    r[h] = cleaned
                    changes += 1
            except ValueError:
                pass

    return changes


def main():
    parser = argparse.ArgumentParser(description='Dedup and standardize')
    parser.add_argument('--dedup-columns', default='',
                        help='Comma-separated columns for dedup check')
    parser.add_argument('--dedup-strategy', default='first',
                        choices=['first', 'last'])
    parser.add_argument('--standardize', default='none',
                        choices=['date', 'string', 'numeric', 'all', 'none'])
    args = parser.parse_args()

    try:
        headers, rows = read_csv_stdin()
    except Exception as e:
        print(json.dumps({'error': f'Failed to read CSV: {str(e)}'}))
        sys.exit(1)

    if not rows:
        print(json.dumps({'error': 'No data rows'}))
        sys.exit(1)

    report = {
        'originalRows': len(rows),
    }

    # Deduplication
    if args.dedup_columns:
        dedup_cols = [c.strip() for c in args.dedup_columns.split(',') if c.strip()]
        valid_cols = [c for c in dedup_cols if c in headers]
        if valid_cols:
            rows, dupes = deduplicate(rows, valid_cols, args.dedup_strategy)
            report['deduplication'] = {
                'columns': valid_cols,
                'strategy': args.dedup_strategy,
                'duplicatesRemoved': dupes,
            }
    else:
        # Full row dedup
        seen = []
        unique = []
        for r in rows:
            key = tuple(r.get(h, '') for h in headers)
            if key not in seen:
                seen.append(key)
                unique.append(r)
        dupes = len(rows) - len(unique)
        rows = unique
        report['deduplication'] = {
            'columns': '(all)',
            'strategy': args.dedup_strategy,
            'duplicatesRemoved': dupes,
        }

    # Standardization
    std_changes = {}
    if args.standardize in ('date', 'all'):
        count = standardize_dates(rows, headers)
        std_changes['date'] = count
    if args.standardize in ('string', 'all'):
        count = standardize_strings(rows, headers)
        std_changes['string'] = count
    if args.standardize in ('numeric', 'all'):
        count = standardize_numeric(rows, headers)
        std_changes['numeric'] = count

    if std_changes:
        report['standardization'] = std_changes

    report['rowsAfterProcessing'] = len(rows)

    # Output cleaned CSV
    writer = csv.DictWriter(sys.stdout, fieldnames=headers)
    writer.writeheader()
    writer.writerows(rows)

    # Report to stderr
    print(json.dumps(report, ensure_ascii=False), file=sys.stderr)


if __name__ == '__main__':
    main()
