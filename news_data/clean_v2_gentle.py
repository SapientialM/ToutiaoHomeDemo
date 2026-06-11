#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
温和清洗v2数据：只移除明确失效的数据（空URL、404、连接失败）
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


def check_cover_url(url):
    """验证封面URL，只对明确失效的返回False"""
    if not url:
        return False
    try:
        # 对Bilibili图片用带referer的HEAD
        req_headers = HEADERS.copy()
        if 'hdslb.com' in url or 'bilibili' in url:
            req_headers['Referer'] = 'https://www.bilibili.com/'
        resp = requests.head(url, headers=req_headers, verify=False, timeout=15, allow_redirects=True)
        # 2xx/3xx 明确有效
        if resp.status_code < 400:
            return True
        # 404/410 明确失效
        if resp.status_code in (404, 410):
            return False
        # 其他4xx/5xx可能是暂时的，试GET
        resp2 = requests.get(url, headers=req_headers, verify=False, timeout=15, allow_redirects=True, stream=True)
        resp2.close()
        if resp2.status_code in (404, 410):
            return False
        # 其他状态码保守保留（可能是权限问题而非资源不存在）
        return True
    except (requests.exceptions.ConnectionError, requests.exceptions.Timeout):
        return False
    except Exception:
        return False


def clean_file(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        data = json.load(f)

    original = len(data)
    print(f'\n[{filename}] Original: {original}')

    # 1. 移除空cover_url
    data = [x for x in data if x.get('cover_url')]
    after_empty = len(data)
    print(f'  Remove empty cover: {after_empty} (-{original - after_empty})')

    # 2. 收集需要验证的cover_url（去重）
    unique_covers = list({x['cover_url'] for x in data})
    print(f'  Unique covers to verify: {len(unique_covers)}')

    # 3. 低并发验证（10线程，避免触发限流）
    cover_valid = {}
    start = time.time()
    completed = 0
    with ThreadPoolExecutor(max_workers=10) as executor:
        futures = {executor.submit(check_cover_url, url): url for url in unique_covers}
        for future in as_completed(futures):
            url = futures[future]
            try:
                cover_valid[url] = future.result()
            except Exception:
                cover_valid[url] = False
            completed += 1
            if completed % 100 == 0:
                print(f'  Verified {completed}/{len(unique_covers)}...')

    invalid_covers = sum(1 for v in cover_valid.values() if not v)
    print(f'  Verification done in {time.time()-start:.1f}s, invalid: {invalid_covers}')

    # 4. 过滤
    cleaned = [x for x in data if cover_valid.get(x['cover_url'], True)]
    removed = original - len(cleaned)
    print(f'  Final: {len(cleaned)} (-{removed}, {removed/original*100:.1f}%)')

    # 5. 保存（覆盖原文件）
    with open(filename, 'w', encoding='utf-8') as f:
        json.dump(cleaned, f, ensure_ascii=False, indent=2)

    return original, len(cleaned)


def main():
    cats = ['推荐', '深圳', '热榜', '视频', '发现', '财经']
    total_orig = 0
    total_final = 0
    for cat in cats:
        orig, final = clean_file(f'data/{cat}_v2.json')
        total_orig += orig
        total_final += final

    print(f'\n{"="*50}')
    print(f'TOTAL: {total_orig} -> {total_final} (-{total_orig-total_final}, {(total_orig-total_final)/total_orig*100:.1f}%)')
    print(f'{"="*50}')


if __name__ == '__main__':
    main()
