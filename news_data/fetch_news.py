#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
拉取今日头条、少数派、Bilibili 数据，按分类保存为JSON
"""

import json
import time
import requests
import urllib3

urllib3.disable_warnings()

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
}


def fetch_bilibili(pages=5):
    """获取Bilibili热门视频"""
    results = []
    headers = {
        **HEADERS,
        'Referer': 'https://www.bilibili.com/',
    }
    for pn in range(1, pages + 1):
        url = f'https://api.bilibili.com/x/web-interface/popular?pn={pn}&ps=50'
        try:
            resp = requests.get(url, headers=headers, verify=False, timeout=15)
            data = resp.json()
            if data.get('code') == 0:
                for item in data['data']['list']:
                    owner = item.get('owner', {})
                    stat = item.get('stat', {})
                    desc = item.get('desc', '')
                    rcmd = item.get('rcmd_reason', {}).get('content', '')
                    if not desc:
                        desc = rcmd
                    if not desc:
                        desc = item.get('title', '')
                    summary = desc[:200] + '...' if len(desc) > 200 else desc
                    results.append({
                        'title': item.get('title', ''),
                        'url': f"https://www.bilibili.com/video/{item.get('bvid', '')}",
                        'cover_url': item.get('pic', ''),
                        'avatar_url': owner.get('face', ''),
                        'summary': summary,
                        'source': 'bilibili',
                        'source_name': owner.get('name', 'Bilibili'),
                        'is_video': True,
                        'hot_score': stat.get('view', 0),
                    })
                print(f"[Bilibili] page {pn}: fetched {len(data['data']['list'])} items")
            else:
                print(f"[Bilibili] page {pn} error: {data}")
        except Exception as e:
            print(f"[Bilibili] page {pn} exception: {e}")
        time.sleep(0.5)
    print(f"[Bilibili] total: {len(results)}")
    return results


def fetch_sspai(max_offset=200):
    """获取少数派热门文章"""
    results = []
    headers = {
        **HEADERS,
        'Referer': 'https://sspai.com/',
    }
    for offset in range(0, max_offset, 20):
        url = f'https://sspai.com/api/v1/article/tag/page/get?limit=20&tag=热门文章&offset={offset}'
        try:
            resp = requests.get(url, headers=headers, verify=False, timeout=15)
            data = resp.json()
            items = data.get('data', []) if isinstance(data, dict) else []
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
            print(f"[SSPAI] offset {offset}: fetched {len(items)} items")
        except Exception as e:
            print(f"[SSPAI] offset {offset} exception: {e}")
        time.sleep(0.5)
    print(f"[SSPAI] total: {len(results)}")
    return results


def fetch_toutiao_feed(category='news_hot', pages=10):
    """获取今日头条feed流"""
    results = []
    max_behot_time = 0
    retries = 0
    for page in range(pages):
        url = f'https://www.toutiao.com/api/pc/feed/?category={category}&utm_source=toutiao&widen=1&max_behot_time={max_behot_time}'
        try:
            resp = requests.get(url, headers=HEADERS, verify=False, timeout=15)
            data = resp.json()
            items = data.get('data', [])
            if not items:
                if retries < 2:
                    retries += 1
                    print(f"[Toutiao/{category}] page {page}: empty, retry {retries}")
                    time.sleep(1)
                    continue
                else:
                    print(f"[Toutiao/{category}] page {page}: no items after retries, stopping")
                    break
            retries = 0
            for item in items:
                if item.get('is_feed_ad'):
                    continue
                title = item.get('title', '')
                if not title:
                    continue
                image_url = ''
                if item.get('image_url'):
                    img = item['image_url']
                    if img.startswith('//'):
                        image_url = 'https:' + img
                    elif img.startswith('http'):
                        image_url = img
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
                summary = item.get('abstract', '')
                if not summary:
                    summary = title
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
            print(f"[Toutiao/{category}] page {page}: fetched {len(items)} items")
        except Exception as e:
            print(f"[Toutiao/{category}] page {page} exception: {e}")
        time.sleep(0.8)
    print(f"[Toutiao/{category}] total: {len(results)}")
    return results


def fetch_toutiao_hot():
    """获取今日头条热榜"""
    results = []
    url = 'https://www.toutiao.com/hot-event/hot-board/?origin=toutiao_pc'
    try:
        resp = requests.get(url, headers=HEADERS, verify=False, timeout=15)
        data = resp.json()
        items = data.get('data', [])
        for item in items:
            title = item.get('Title', '')
            url_link = item.get('Url', '')
            image_url = ''
            img_data = item.get('Image')
            if isinstance(img_data, dict):
                image_url = img_data.get('url', '')
            elif isinstance(img_data, str):
                image_url = img_data
            avatar_url = item.get('LabelUrl', '')
            hot_value = item.get('HotValue', '')
            results.append({
                'title': title,
                'url': url_link,
                'cover_url': image_url,
                'avatar_url': avatar_url,
                'summary': title,
                'source': 'toutiao',
                'source_name': '今日头条热榜',
                'is_video': False,
                'hot_score': str(hot_value),
            })
        print(f"[Toutiao/Hot] fetched {len(results)} items")
    except Exception as e:
        print(f"[Toutiao/Hot] exception: {e}")
    return results


def dedup_by_url(items):
    """按URL去重"""
    seen = set()
    result = []
    for item in items:
        if item['url'] not in seen:
            seen.add(item['url'])
            result.append(item)
    return result


def assign_categories(bilibili_data, sspai_data, toutiao_feeds, toutiao_hot):
    """按分类分配数据，严格控制视频比例"""
    import random
    random.seed(42)

    # 去重并分离视频/文章
    bilibili = dedup_by_url(bilibili_data)
    sspai = dedup_by_url(sspai_data)
    toutiao = dedup_by_url(toutiao_feeds)
    hot = dedup_by_url(toutiao_hot)

    # 分离视频和文章
    bilibili_v = [x for x in bilibili if x.get('is_video')]
    bilibili_a = [x for x in bilibili if not x.get('is_video')]
    toutiao_v = [x for x in toutiao if x.get('is_video')]
    toutiao_a = [x for x in toutiao if not x.get('is_video')]
    hot_a = [x for x in hot if not x.get('is_video')]
    sspai_a = [x for x in sspai if not x.get('is_video')]

    random.shuffle(bilibili_v)
    random.shuffle(bilibili_a)
    random.shuffle(toutiao_v)
    random.shuffle(toutiao_a)
    random.shuffle(hot_a)
    random.shuffle(sspai_a)

    print(f"Videos: bilibili={len(bilibili_v)}, toutiao={len(toutiao_v)}")
    print(f"Articles: bilibili={len(bilibili_a)}, toutiao={len(toutiao_a)}, hot={len(hot_a)}, sspai={len(sspai_a)}")

    categories = {
        '推荐': [],
        '深圳': [],
        '热榜': [],
        '视频': [],
        '发现': [],
        '财经': [],
    }

    # 视频: 100条视频
    categories['视频'] = bilibili_v[:100]
    bilibili_v = bilibili_v[100:]

    # 推荐: 30视频 + 60文章 + 10补充
    rec_v = bilibili_v[:15] + toutiao_v[:15]; bilibili_v = bilibili_v[15:]; toutiao_v = toutiao_v[15:]
    rec_a = toutiao_a[:20] + sspai_a[:20] + hot_a[:20]
    toutiao_a = toutiao_a[20:]; sspai_a = sspai_a[20:]; hot_a = hot_a[20:]
    categories['推荐'] = rec_v + rec_a
    random.shuffle(categories['推荐'])

    # 深圳: 30视频 + 60文章 + 10补充
    sz_v = bilibili_v[:15] + toutiao_v[:15]; bilibili_v = bilibili_v[15:]; toutiao_v = toutiao_v[15:]
    sz_a = toutiao_a[:30] + hot_a[:30]
    toutiao_a = toutiao_a[30:]; hot_a = hot_a[30:]
    categories['深圳'] = sz_v + sz_a
    random.shuffle(categories['深圳'])

    # 热榜: 30视频 + 60文章 + 10补充
    hot_v = bilibili_v[:15] + toutiao_v[:15]; bilibili_v = bilibili_v[15:]; toutiao_v = toutiao_v[15:]
    hot_a2 = toutiao_a[:30] + sspai_a[:30]
    toutiao_a = toutiao_a[30:]; sspai_a = sspai_a[30:]
    categories['热榜'] = hot_v + hot_a2
    random.shuffle(categories['热榜'])

    # 发现: 30视频 + 60文章 + 10补充
    dis_v = bilibili_v[:15] + toutiao_v[:15]; bilibili_v = bilibili_v[15:]; toutiao_v = toutiao_v[15:]
    dis_a = sspai_a[:40] + toutiao_a[:20]
    sspai_a = sspai_a[40:]; toutiao_a = toutiao_a[20:]
    categories['发现'] = dis_v + dis_a
    random.shuffle(categories['发现'])

    # 财经: 30视频 + 60文章 + 10补充
    fin_v = bilibili_v[:15] + toutiao_v[:15]; bilibili_v = bilibili_v[15:]; toutiao_v = toutiao_v[15:]
    fin_a = toutiao_a[:30] + sspai_a[:30]
    toutiao_a = toutiao_a[30:]; sspai_a = sspai_a[30:]
    categories['财经'] = fin_v + fin_a
    random.shuffle(categories['财经'])

    # 去重并补充（优先补充文章）
    all_remain_a = dedup_by_url(bilibili_a + toutiao_a + hot_a + sspai_a)
    all_remain_v = dedup_by_url(bilibili_v + toutiao_v)
    random.shuffle(all_remain_a)
    random.shuffle(all_remain_v)

    for cat in categories:
        unique = dedup_by_url(categories[cat])
        # 优先补充文章
        while len(unique) < 100 and all_remain_a:
            item = all_remain_a.pop(0)
            if item['url'] not in {x['url'] for x in unique}:
                unique.append(item)
        # 再补充视频
        while len(unique) < 100 and all_remain_v:
            item = all_remain_v.pop(0)
            if item['url'] not in {x['url'] for x in unique}:
                unique.append(item)
        # 最后补充任何剩余
        all_remain = dedup_by_url(all_remain_a + all_remain_v)
        random.shuffle(all_remain)
        while len(unique) < 100 and all_remain:
            item = all_remain.pop(0)
            if item['url'] not in {x['url'] for x in unique}:
                unique.append(item)
        categories[cat] = unique[:100]
        vids = sum(1 for x in categories[cat] if x.get('is_video'))
        print(f"[Category] {cat}: {len(categories[cat])} items, videos={vids}")

    return categories


def save_categories(categories):
    """保存各分类为JSON"""
    import os
    os.makedirs('data', exist_ok=True)
    for cat_name, items in categories.items():
        # 给每条数据加上category字段
        for item in items:
            item['category'] = cat_name
        filename = f"data/{cat_name}.json"
        with open(filename, 'w', encoding='utf-8') as f:
            json.dump(items, f, ensure_ascii=False, indent=2)
        print(f"Saved {cat_name}: {len(items)} items -> {filename}")


def main():
    print("=" * 50)
    print("开始拉取新闻数据...")
    print("=" * 50)

    bilibili = fetch_bilibili(pages=5)
    sspai = fetch_sspai(max_offset=200)

    # 从多个category获取更多头条数据
    toutiao_all = []
    for cat in ['news_hot', 'news_finance', 'news_tech', 'news_entertainment', 'news_sports']:
        toutiao_all.extend(fetch_toutiao_feed(category=cat, pages=8))
        time.sleep(1)

    hot = fetch_toutiao_hot()

    print("\n" + "=" * 50)
    print("分配分类...")
    print("=" * 50)
    categories = assign_categories(bilibili, sspai, toutiao_all, hot)

    print("\n" + "=" * 50)
    print("保存数据...")
    print("=" * 50)
    save_categories(categories)

    print("\n完成！")


if __name__ == '__main__':
    main()
