#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
v2版本：每个分类1000条，视频:文章 = 5:5
来源：今日头条、少数派、BiliBili
"""

import json
import time
import random
import requests
import urllib3

urllib3.disable_warnings()

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
}


def fetch_bilibili_all(target_count=4000):
    """获取Bilibili视频，合并popular和分区动态"""
    results = []
    seen_bvids = set()
    headers = {**HEADERS, 'Referer': 'https://www.bilibili.com/'}

    # 1. Popular
    print('[Bilibili] Fetching popular...')
    for pn in range(1, 15):
        try:
            resp = requests.get(
                f'https://api.bilibili.com/x/web-interface/popular?pn={pn}&ps=50',
                headers=headers, verify=False, timeout=15
            )
            data = resp.json()
            if data.get('code') == 0:
                items = data['data']['list']
                if not items:
                    break
                for item in items:
                    bvid = item.get('bvid')
                    if bvid in seen_bvids:
                        continue
                    seen_bvids.add(bvid)
                    owner = item.get('owner', {})
                    stat = item.get('stat', {})
                    desc = item.get('desc', '') or item.get('rcmd_reason', {}).get('content', '') or item.get('title', '')
                    desc = desc[:200] + '...' if len(desc) > 200 else desc
                    results.append({
                        'title': item.get('title', ''),
                        'url': f"https://www.bilibili.com/video/{bvid}",
                        'cover_url': item.get('pic', ''),
                        'avatar_url': owner.get('face', ''),
                        'summary': desc,
                        'source': 'bilibili',
                        'source_name': owner.get('name', 'Bilibili'),
                        'is_video': True,
                        'hot_score': stat.get('view', 0),
                    })
            else:
                break
        except Exception as e:
            print(f'[Bilibili] popular pn={pn} error: {e}')
        time.sleep(0.3)
    print(f'[Bilibili] popular: {len(results)} items')

    # 2. 分区动态
    print('[Bilibili] Fetching dynamic regions...', flush=True)
    for rid in range(1, 201):
        if len(results) >= target_count:
            break
        try:
            resp = requests.get(
                f'https://api.bilibili.com/x/web-interface/dynamic/region?ps=50&rid={rid}',
                headers=headers, verify=False, timeout=10
            )
            data = resp.json()
            if data.get('code') == 0:
                for item in data['data'].get('archives', []):
                    bvid = item.get('bvid')
                    if not bvid or bvid in seen_bvids:
                        continue
                    seen_bvids.add(bvid)
                    owner = item.get('owner', {})
                    stat = item.get('stat', {})
                    desc = item.get('desc', '') or item.get('title', '')
                    desc = desc[:200] + '...' if len(desc) > 200 else desc
                    results.append({
                        'title': item.get('title', ''),
                        'url': f"https://www.bilibili.com/video/{bvid}",
                        'cover_url': item.get('pic', ''),
                        'avatar_url': owner.get('face', ''),
                        'summary': desc,
                        'source': 'bilibili',
                        'source_name': owner.get('name', 'Bilibili'),
                        'is_video': True,
                        'hot_score': stat.get('view', 0),
                    })
        except Exception:
            pass
        if rid % 20 == 0:
            print(f'[Bilibili] rid={rid}, total={len(results)}')
        time.sleep(0.1)

    print(f'[Bilibili] final: {len(results)} items', flush=True)
    return results


def fetch_sspai(target_count=3000):
    """获取少数派文章"""
    results = []
    headers = {**HEADERS, 'Referer': 'https://sspai.com/'}
    offset = 0
    while len(results) < target_count:
        try:
            resp = requests.get(
                f'https://sspai.com/api/v1/article/tag/page/get?limit=20&tag=热门文章&offset={offset}',
                headers=headers, verify=False, timeout=15
            )
            data = resp.json()
            items = data.get('data', []) if isinstance(data, dict) else []
            if not items:
                break
            for item in items:
                author = item.get('author', {})
                banner = item.get('banner', '')
                avatar = author.get('avatar', '')
                summary = item.get('summary', '')
                summary = summary[:200] + '...' if len(summary) > 200 else summary
                results.append({
                    'title': item.get('title', ''),
                    'url': f"https://sspai.com/post/{item.get('id', '')}",
                    'cover_url': f"https://cdn.sspai.com/{banner}" if banner else '',
                    'avatar_url': f"https://cdn.sspai.com/{avatar}" if avatar else '',
                    'summary': summary,
                    'source': 'sspai',
                    'source_name': author.get('nickname', '少数派'),
                    'is_video': False,
                    'hot_score': item.get('view_count', 0),
                })
            if offset % 200 == 0:
                print(f'[SSPAI] offset={offset}, total={len(results)}')
        except Exception as e:
            print(f'[SSPAI] offset={offset} error: {e}')
        offset += 20
        time.sleep(0.25)
    print(f'[SSPAI] final: {len(results)} items', flush=True)
    return results


def fetch_toutiao_feed(category='news_hot', pages=15):
    results = []
    max_behot_time = 0
    for page in range(pages):
        url = f'https://www.toutiao.com/api/pc/feed/?category={category}&utm_source=toutiao&widen=1&max_behot_time={max_behot_time}'
        try:
            resp = requests.get(url, headers=HEADERS, verify=False, timeout=15)
            data = resp.json()
            items = data.get('data', [])
            if not items:
                break
            for item in items:
                if item.get('is_feed_ad'):
                    continue
                title = item.get('title', '')
                if not title:
                    continue
                image_url = ''
                if item.get('image_url'):
                    img = item['image_url']
                    image_url = 'https:' + img if img.startswith('//') else img
                elif item.get('middle_image'):
                    image_url = item['middle_image']
                avatar_url = item.get('media_avatar_url', '')
                source_url = item.get('source_url', '')
                if source_url.startswith('/'):
                    item_url = 'https://www.toutiao.com' + source_url
                elif source_url.startswith('http'):
                    item_url = source_url
                else:
                    item_url = f"https://www.toutiao.com/group/{item.get('item_id', '')}/"
                summary = item.get('abstract', '') or title
                summary = summary[:200] + '...' if len(summary) > 200 else summary
                results.append({
                    'title': title,
                    'url': item_url,
                    'cover_url': image_url,
                    'avatar_url': avatar_url,
                    'summary': summary,
                    'source': 'toutiao',
                    'source_name': item.get('source', '今日头条'),
                    'is_video': bool(item.get('has_video')),
                    'hot_score': item.get('video_play_count', 0) or item.get('comments_count', 0),
                })
            if items and isinstance(items[-1], dict) and items[-1].get('behot_time'):
                max_behot_time = items[-1]['behot_time']
            else:
                break
        except Exception as e:
            print(f'[Toutiao/{category}] page={page} error: {e}')
        time.sleep(0.4)
    print(f'[Toutiao/{category}] {len(results)} items', flush=True)
    return results


def fetch_toutiao_hot():
    results = []
    try:
        resp = requests.get(
            'https://www.toutiao.com/hot-event/hot-board/?origin=toutiao_pc',
            headers=HEADERS, verify=False, timeout=15
        )
        data = resp.json()
        for item in data.get('data', []):
            image_url = ''
            img_data = item.get('Image')
            if isinstance(img_data, dict):
                image_url = img_data.get('url', '')
            elif isinstance(img_data, str):
                image_url = img_data
            results.append({
                'title': item.get('Title', ''),
                'url': item.get('Url', ''),
                'cover_url': image_url,
                'avatar_url': item.get('LabelUrl', ''),
                'summary': item.get('Title', ''),
                'source': 'toutiao',
                'source_name': '今日头条热榜',
                'is_video': False,
                'hot_score': str(item.get('HotValue', '')),
            })
    except Exception as e:
        print(f'[Toutiao/Hot] error: {e}')
    print(f'[Toutiao/Hot] {len(results)} items')
    return results


def dedup_by_url(items):
    seen = set()
    return [x for x in items if not (x['url'] in seen or seen.add(x['url']))]


def assign_and_save(bilibili_data, sspai_data, toutiao_data, hot_data):
    random.seed(42)

    # 分离视频和文章
    bilibili_v = [x for x in bilibili_data if x.get('is_video')]
    sspai_a = [x for x in sspai_data if not x.get('is_video')]
    toutiao_v = [x for x in toutiao_data if x.get('is_video')]
    toutiao_a = [x for x in toutiao_data if not x.get('is_video')]
    hot_a = [x for x in hot_data if not x.get('is_video')]

    random.shuffle(bilibili_v)
    random.shuffle(sspai_a)
    random.shuffle(toutiao_v)
    random.shuffle(toutiao_a)
    random.shuffle(hot_a)

    print(f'\nPool: bilibili_v={len(bilibili_v)}, sspai_a={len(sspai_a)}, toutiao_v={len(toutiao_v)}, toutiao_a={len(toutiao_a)}, hot_a={len(hot_a)}')

    categories = {}

    # 视频: 1000条视频
    categories['视频'] = bilibili_v[:1000]
    bilibili_v = bilibili_v[1000:]

    # 其他5分类: 500视频 + 500文章
    for cat_name in ['推荐', '深圳', '热榜', '发现', '财经']:
        v_part = bilibili_v[:400] + toutiao_v[:100]
        bilibili_v = bilibili_v[400:]
        toutiao_v = toutiao_v[100:]

        a_part = sspai_a[:350] + toutiao_a[:100] + hot_a[:50]
        sspai_a = sspai_a[350:]
        toutiao_a = toutiao_a[100:]
        hot_a = hot_a[50:]

        cat_items = v_part + a_part
        random.shuffle(cat_items)
        categories[cat_name] = cat_items[:1000]

    # 去重并补充
    all_remain = dedup_by_url(bilibili_v + sspai_a + toutiao_v + toutiao_a + hot_a)
    random.shuffle(all_remain)

    for cat_name, items in categories.items():
        seen = set()
        unique = []
        for item in items:
            if item['url'] not in seen:
                seen.add(item['url'])
                unique.append(item)
        while len(unique) < 1000 and all_remain:
            item = all_remain.pop(0)
            if item['url'] not in seen:
                seen.add(item['url'])
                unique.append(item)
        categories[cat_name] = unique[:1000]
        vids = sum(1 for x in categories[cat_name] if x.get('is_video'))
        print(f'[Category] {cat_name}: {len(categories[cat_name])} items, videos={vids}')

    # 保存
    import os
    os.makedirs('data', exist_ok=True)
    for cat_name, items in categories.items():
        for item in items:
            item['category'] = cat_name
        filename = f'data/{cat_name}_v2.json'
        with open(filename, 'w', encoding='utf-8') as f:
            json.dump(items, f, ensure_ascii=False, indent=2)
        print(f'Saved {cat_name}_v2.json: {len(items)} items')


def main():
    print('=' * 60)
    print('v2 数据拉取开始')
    print('=' * 60)

    bilibili = fetch_bilibili_all(target_count=4000)
    sspai = fetch_sspai(target_count=3000)

    toutiao_all = []
    for cat in ['news_hot', 'news_finance', 'news_tech', 'news_entertainment', 'news_sports']:
        toutiao_all.extend(fetch_toutiao_feed(category=cat, pages=15))
        time.sleep(1)

    hot = fetch_toutiao_hot()

    print('\n' + '=' * 60)
    print('分配分类并保存')
    print('=' * 60)
    assign_and_save(bilibili, sspai, toutiao_all, hot)
    print('\n完成！')


if __name__ == '__main__':
    main()
