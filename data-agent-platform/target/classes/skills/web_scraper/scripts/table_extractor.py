#!/usr/bin/env python3
"""
Table Extractor Script
Extracts HTML tables from a URL using pandas.

Usage:
    python3 table_extractor.py --url <URL> [--table-index -1] [--output csv|json]
"""

import argparse
import json
import sys
import urllib.request
import urllib.error


def fetch_url(url, timeout=30):
    """Fetch URL content."""
    headers = {
        'User-Agent': 'Mozilla/5.0 (compatible; DataAnalysisBot/1.0)',
        'Accept': 'text/html,application/xhtml+xml',
    }
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            charset = resp.headers.get_content_charset() or 'utf-8'
            return resp.read().decode(charset, errors='replace')
    except urllib.error.HTTPError as e:
        print(json.dumps({'error': f'HTTP {e.code}: {e.reason}'}), file=sys.stderr)
        sys.exit(1)
    except urllib.error.URLError as e:
        print(json.dumps({'error': f'URL Error: {str(e.reason)}'}), file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(json.dumps({'error': str(e)}'), file=sys.stderr)
        sys.exit(1)


def extract_with_pandas(html, table_index):
    """Extract tables using pandas (if available)."""
    try:
        import pandas as pd
        tables = pd.read_html(html)
        if not tables:
            return []
        if table_index >= 0:
            if table_index < len(tables):
                return [tables[table_index].to_dict(orient='records')]
            return []
        return [t.to_dict(orient='records') for t in tables]
    except ImportError:
        return None


def extract_tables_simple(html):
    """Fallback table extraction without pandas."""
    from html.parser import HTMLParser

    class TableParser(HTMLParser):
        def __init__(self):
            super().__init__()
            self.tables = []
            self._table = None
            self._row = None
            self._cell = None
            self._in_cell = False

        def handle_starttag(self, tag, attrs):
            if tag == 'table':
                self._table = {'headers': [], 'rows': []}
            elif tag == 'tr':
                self._row = []
            elif tag in ('td', 'th'):
                self._in_cell = True
                self._cell = ''

        def handle_endtag(self, tag):
            if tag == 'table':
                if self._table:
                    self.tables.append(self._table)
                self._table = None
            elif tag == 'tr':
                if self._row is not None and self._table is not None:
                    self._table['rows'].append(self._row)
                self._row = None
            elif tag in ('td', 'th'):
                self._in_cell = False
                if self._cell is not None and self._row is not None:
                    self._row.append(self._cell.strip())
                self._cell = None

        def handle_data(self, data):
            if self._in_cell and self._cell is not None:
                self._cell += data

    parser = TableParser()
    parser.feed(html)
    return parser.tables


def main():
    parser = argparse.ArgumentParser(description='Table extractor')
    parser.add_argument('--url', required=True, help='Target URL')
    parser.add_argument('--table-index', type=int, default=-1,
                        help='Table index (0-based, -1 for all)')
    parser.add_argument('--output', default='csv', choices=['csv', 'json'])
    args = parser.parse_args()

    html = fetch_url(args.url)

    # Try pandas first, fall back to simple parser
    pandas_result = extract_with_pandas(html, args.table_index)
    if pandas_result is not None:
        if args.output == 'json':
            print(json.dumps({
                'url': args.url,
                'table_count': len(pandas_result),
                'tables': pandas_result
            }, ensure_ascii=False, indent=2))
        else:
            for i, table in enumerate(pandas_result):
                if not table:
                    continue
                if i > 0:
                    print()
                keys = list(table[0].keys())
                print(','.join(f'"{k}"' for k in keys))
                for row in table:
                    print(','.join(f'"{v}"' for v in row.values()))
        return

    # Fallback: simple HTML parser
    tables = extract_tables_simple(html)
    if args.table_index >= 0:
        if args.table_index < len(tables):
            tables = [tables[args.table_index]]
        else:
            tables = []

    if args.output == 'json':
        print(json.dumps({
            'url': args.url,
            'table_count': len(tables),
            'tables': tables
        }, ensure_ascii=False, indent=2))
    else:
        for i, table in enumerate(tables):
            if i > 0:
                print()
            headers = table.get('headers', [])
            if headers:
                print(','.join(f'"{h}"' for h in headers))
            for row in table.get('rows', []):
                print(','.join(f'"{c}"' for c in row))


if __name__ == '__main__':
    main()
