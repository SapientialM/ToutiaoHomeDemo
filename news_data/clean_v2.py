#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
清洗v2数据：验证封面图和链接可访问性，移除失效数据
"""

import json
import time
import requests
import urllib3
from concurrent.futures import ThreadPoolExecutor, as_completed

urllib3.disable_warnings()

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
}


def check_url(url, timeout=8):
    """验证URL是否可访问，返回 (url, is_valid, status_code)"""
    if not url:
        return url, False, 0
    try:
        resp = requests.head(url, headers=HEADERS, verify=False, timeout=timeout, allow_redirects=True)
        # 2xx 和 3xx 都认为有效
        if resp.status_code < 400:
            return url, True, resp.status_code
        # HEAD可能不被支持，试试GET（只读header）
        resp2 = requests.get(url, headers=HEADERS, verify=False, timeout=timeout, allow_redirects=True, stream=True)
        resp2.close()
        if resp2.status_code < 400:
            return url, True, resp2.status_code
        return url, False, resp.status_code
    except requests.exceptions.Timeout:
        return url, False, 'timeout'
    except Exception as e:
        return url, False, str(e)[:30]


def clean_category(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        data = json.load(f)

    original_count = len(data)
    print(f'\\n[{filename}] Original: {original_count} items')

    # 1. 先移除 cover_url 为空的数据
    data = [x for x in data if x.get('cover_url')]
    after_cover = len(data)
    print(f'  After removing empty cover: {after_cover} (-{original_count - after_cover})')

    # 2. 收集所有需要验证的 URL
    cover_urls = list({x['cover_url'] for x in data if x.get('cover_url')})
    page_urls = list({x['url'] for x in data if x.get('url')})
    print(f'  Unique cover URLs to check: {len(cover_urls)}')
    print(f'  Unique page URLs to check: {len(page_urls)}')

    # 3. 并发验证 cover_url
    print('  Checking cover URLs...')
    cover_valid = {}
    start = time.time()
    with ThreadPoolExecutor(max_workers=50) as executor:
        futures = {executor.submit(check_url, url): url for url in cover_urls}
        for future in as_completed(futures):
            url, valid, code = future.result()
            cover_valid[url] = valid
    print(f'  Cover check done in {time.time()-start:.1f}s, invalid: {sum(1 for v in cover_valid.values() if not v)}')

    # 4. 并发验证 page_url（只检查Bilibili视频，因为文章页面通常不会失效）
    # 为了加快速度，只抽样检查部分page_url
    page_urls_to_check = [url for url in page_urls if 'bilibili.com' in url]
    print(f'  Checking Bilibili page URLs: {len(page_urls_to_check)}')
    page_valid = {}
    start = time.time()
    with ThreadPoolExecutor(max_workers=50) as executor:
        futures = {executor.submit(check_url, url): url for url in page_urls_to_check}
        for future in as_completed(futures):
            url, valid, code = future.result()
            page_valid[url] = valid
    print(f'  Page check done in {time.time()-start:.1f}s, invalid: {sum(1 for v in page_valid.values() if not v)}')

    # 5. 过滤数据
    cleaned = []
    for item in data:
        cover_ok = cover_valid.get(item.get('cover_url'), True)
        page_ok = page_valid.get(item.get('url'), True)
        if cover_ok and page_ok:
            cleaned.append(item)

    removed = original_count - len(cleaned)
    print(f'  Final: {len(cleaned)} items (-{removed}, {removed/original_count*100:.1f}%)')

    # 6. 保存
    with open(filename, 'w', encoding='utf-8') as f:
        json.dump(cleaned, f, ensure_ascii=False, indent=2)

    return original_count, len(cleaned), removed


def main():
    categories = ['推荐', '深圳', '热榜', '视频', '发现', '财经']
    total_original = 0
    total_final = 0
    total_removed = 0

    for cat in categories:
        orig, final, removed = clean_category(f'data/{cat}_v2.json')
        total_original += orig
        total_final += final
        total_removed += removed

    print(f'\\n{"="*50}')
    print(f'TOTAL: {total_original} -> {total_final} (-{total_removed}, {total_removed/total_original*100:.1f}%)')
    print(f'{"="*50}')


if __name__ == '__main__':
    main()
