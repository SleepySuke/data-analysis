#!/usr/bin/env python3
"""
Web Scraper Script
Fetches web content and extracts structured data (tables, articles).

Usage:
    python3 web_scraper.py --url <URL> [--extract-type table|article|all] [--output json|csv]
"""

import argparse
import json
import sys
import urllib.request
import urllib.error
from html.parser import HTMLParser


class TableExtractor(HTMLParser):
    """Extract tables from HTML without external dependencies."""

    def __init__(self):
        super().__init__()
        self.tables = []
        self._current_table = None
        self._current_row = None
        self._current_cell = None
        self._in_thead = False
        self._in_tbody = False
        self._in_th = False
        self._in_td = False

    def handle_starttag(self, tag, attrs):
        if tag == 'table':
            self._current_table = {'headers': [], 'rows': []}
        elif tag == 'thead':
            self._in_thead = True
        elif tag == 'tbody':
            self._in_tbody = True
        elif tag == 'tr':
            self._current_row = []
        elif tag == 'th':
            self._in_th = True
            self._current_cell = ''
        elif tag == 'td':
            self._in_td = True
            self._current_cell = ''

    def handle_endtag(self, tag):
        if tag == 'table':
            if self._current_table:
                self.tables.append(self._current_table)
            self._current_table = None
        elif tag == 'thead':
            self._in_thead = False
        elif tag == 'tbody':
            self._in_tbody = False
        elif tag == 'tr':
            if self._current_row is not None:
                if self._in_thead and self._current_table is not None:
                    self._current_table['headers'].extend(self._current_row)
                elif self._current_table is not None:
                    self._current_table['rows'].append(self._current_row)
            self._current_row = None
        elif tag == 'th':
            self._in_th = False
            if self._current_cell is not None and self._current_row is not None:
                self._current_row.append(self._current_cell.strip())
            self._current_cell = None
        elif tag == 'td':
            self._in_td = False
            if self._current_cell is not None and self._current_row is not None:
                self._current_row.append(self._current_cell.strip())
            self._current_cell = None

    def handle_data(self, data):
        if self._current_cell is not None:
            self._current_cell += data


class ArticleExtractor(HTMLParser):
    """Extract main article content from HTML."""

    def __init__(self):
        super().__init__()
        self.articles = []
        self._current_article = None
        self._in_article = False
        self._in_main = False
        self._in_p = False
        self._in_h = False
        self._current_text = ''
        self._depth = 0

    def handle_starttag(self, tag, attrs):
        if tag == 'article':
            self._in_article = True
            self._current_article = {'title': '', 'paragraphs': []}
            self._depth = 0
        elif tag == 'main':
            self._in_main = True
        elif tag in ('h1', 'h2', 'h3', 'h4', 'h5', 'h6'):
            self._in_h = True
            self._current_text = ''
        elif tag == 'p':
            self._in_p = True
            self._current_text = ''

    def handle_endtag(self, tag):
        if tag == 'article':
            self._in_article = False
            if self._current_article and self._current_article['paragraphs']:
                self.articles.append(self._current_article)
            self._current_article = None
        elif tag == 'main':
            self._in_main = False
        elif tag in ('h1', 'h2', 'h3', 'h4', 'h5', 'h6'):
            self._in_h = False
            if self._current_text.strip():
                if self._current_article:
                    self._current_article['title'] = self._current_text.strip()
        elif tag == 'p':
            self._in_p = False
            text = self._current_text.strip()
            if text and len(text) > 20:
                if self._current_article:
                    self._current_article['paragraphs'].append(text)
                elif self._in_main:
                    if not self.articles:
                        self.articles.append({'title': '', 'paragraphs': []})
                    self.articles[0]['paragraphs'].append(text)
            self._current_text = ''

    def handle_data(self, data):
        if self._in_h or self._in_p:
            self._current_text += data


def fetch_url(url, timeout=30):
    """Fetch URL content with basic error handling."""
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


def extract_tables(html):
    """Extract all HTML tables."""
    parser = TableExtractor()
    parser.feed(html)
    return parser.tables


def extract_articles(html):
    """Extract article content."""
    parser = ArticleExtractor()
    parser.feed(html)
    return parser.articles


def main():
    parser = argparse.ArgumentParser(description='Web scraper')
    parser.add_argument('--url', required=True, help='Target URL')
    parser.add_argument('--extract-type', default='all',
                        choices=['table', 'article', 'all'], help='What to extract')
    parser.add_argument('--output', default='json', choices=['json', 'csv'])
    args = parser.parse_args()

    html = fetch_url(args.url)

    result = {
        'url': args.url,
        'extract_type': args.extract_type,
    }

    if args.extract_type in ('table', 'all'):
        tables = extract_tables(html)
        result['tables'] = tables
        result['table_count'] = len(tables)

    if args.extract_type in ('article', 'all'):
        articles = extract_articles(html)
        result['articles'] = articles
        result['article_count'] = len(articles)

    if args.output == 'json':
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        if result.get('tables'):
            for i, table in enumerate(result['tables']):
                if i > 0:
                    print()
                headers = table.get('headers', [])
                if headers:
                    print(','.join(f'"{h}"' for h in headers))
                for row in table.get('rows', []):
                    print(','.join(f'"{c}"' for c in row))


if __name__ == '__main__':
    main()
