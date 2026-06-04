import sys, json
from PIL import Image
from collections import Counter


def detect_horizontal_bands(img, y_start, y_end, w, color_threshold, min_width_ratio=0.6):
    """Detect horizontal bands of a given color."""
    bands = []
    in_band = False
    band_start = 0
    for y in range(y_start, y_end, 2):
        row = [img.getpixel((x, y)) for x in range(0, w, 8)]
        matches = sum(1 for p in row if all(a > b for a, b in zip(p, color_threshold)))
        ratio = matches / len(row) if row else 0
        if ratio > min_width_ratio and not in_band:
            in_band = True
            band_start = y
        elif ratio < 0.3 and in_band:
            in_band = False
            bands.append((band_start, y))
    if in_band:
        bands.append((band_start, y_end))
    return bands


def detect_text_region(img, y_start, y_end, w):
    """Detect horizontal regions with dense dark pixels (text)."""
    regions = []
    in_text = False
    text_start = 0
    for y in range(y_start, y_end, 2):
        row = [img.getpixel((x, y)) for x in range(0, w, 4)]
        dark = sum(1 for p in row if p[0] < 80 and p[1] < 80 and p[2] < 80)
        ratio = dark / len(row) if row else 0
        if ratio > 0.03 and not in_text:
            in_text = True
            text_start = y
        elif ratio < 0.01 and in_text:
            in_text = False
            h = y - text_start
            if h > 8:
                regions.append({"y": text_start, "height": h})
    if in_text:
        h = y_end - text_start
        if h > 8:
            regions.append({"y": text_start, "height": h})
    return regions


def classify_card(img, card_y, card_h, w, content_bottom):
    """Classify card type based on structure."""
    h_avail = img.height
    safe_y = min(card_y + card_h // 2, h_avail - 1)
    safe_bottom_y = min(card_y + int(card_h * 0.7), h_avail - 1)

    left_half = [img.getpixel((x, safe_y)) for x in range(0, w // 2, 8)]
    right_half = [img.getpixel((x, safe_y)) for x in range(w // 2, w, 8)]
    left_colors = len(set(left_half))
    right_colors = len(set(right_half))

    has_right_image = right_colors > 30
    full_row = [img.getpixel((x, safe_y)) for x in range(0, w, 4)]
    distinct = len(set(full_row))
    bottom_row = [img.getpixel((x, safe_bottom_y)) for x in range(0, w, 4)]
    bottom_distinct = len(set(bottom_row))

    top_region = img.crop((0, card_y, min(int(w * 0.3), w), card_y + min(30, card_h)))
    top_pixels = list(top_region.getdata())
    red_badge = sum(1 for p in top_pixels if p[0] > 200 and p[1] < 100 and p[2] < 100)

    if card_h > w * 0.5:
        return "LargeImageCard"
    elif has_right_image and card_h < int(w * 0.45):
        return "LeftTextRightImageCard"
    elif distinct > 80:
        return "LeftTextRightImageCard"
    elif red_badge > 5:
        return "TextTopCard"
    else:
        return "LeftTextRightImageCard"


def check_image_loaded(img, card_y, card_h, img_w, img_h):
    """Check if image region has actual content vs gray placeholder."""
    upper = max(0, card_y)
    lower = min(img_h, card_y + card_h)
    if lower <= upper:
        return False, 100
    region = img.crop((0, upper, img_w, lower))
    pixels = list(region.getdata())
    if not pixels:
        return False, 100
    gray = sum(1 for p in pixels if 180 < p[0] < 230 and 180 < p[1] < 230 and 180 < p[2] < 230)
    gray_pct = gray / len(pixels) * 100
    return gray_pct < 60, round(gray_pct, 1)


def analyze_image(path: str) -> dict:
    img = Image.open(path)
    w, h = img.width, img.height
    pixels = list(img.getdata())

    # Overall stats
    overall = Counter(pixels).most_common(10)
    total_px = len(pixels)
    top_colors = [{"hex": f"#{c[0]:02x}{c[1]:02x}{c[2]:02x}", "count": n, "pct": round(n / total_px * 100, 1)}
                  for c, n in overall]

    primary_red = next((c for c in overall if c[0][0] > 200 and c[0][1] < 100 and c[0][2] < 100), None)
    primary_red_hex = f"#{primary_red[0][0]:02x}{primary_red[0][1]:02x}{primary_red[0][2]:02x}" if primary_red else "N/A"
    primary_red_name = {
        (0xFF, 0x57, 0x57): "#FF5757 (design spec)",
        (0xD8, 0x1E, 0x06): "#D81E06 (old theme)",
    }.get(primary_red[0] if primary_red else None, primary_red_hex)

    # ── Header ──
    header_h = int(h * 0.12)
    header = img.crop((0, 0, w, header_h))
    hp = list(header.getdata())
    red_in_header = sum(1 for p in hp if p[0] > 200 and p[1] < 100 and p[2] < 100)
    red_pct = red_in_header / len(hp) * 100 if hp else 0
    avg_r = sum(p[0] for p in hp) // len(hp) if hp else 0
    avg_g_val = sum(p[1] for p in hp) // len(hp) if hp else 0
    avg_b = sum(p[2] for p in hp) // len(hp) if hp else 0
    avg_red_hex = f"#{avg_r:02x}{avg_g_val:02x}{avg_b:02x}"
    dark_in_header = sum(1 for p in hp if p[0] < 100 and p[1] < 100 and p[2] < 100)
    white_in_header = sum(1 for p in hp if p[0] > 230 and p[1] > 230 and p[2] > 230)
    white_header_pct = white_in_header / len(hp) * 100 if hp else 0

    # Detect search bar in header (white horizontal band in red area)
    search_bar = None
    for y in range(10, header_h - 20, 2):
        row = [img.getpixel((x, y)) for x in range(int(w * 0.1), int(w * 0.9), 4)]
        white = sum(1 for p in row if p[0] > 230 and p[1] > 230 and p[2] > 230)
        if white / len(row) > 0.6:
            search_bar = {"y": y, "width_ratio": round(white / len(row), 2)}
            break

    # Detect tab bar below header
    tab_bar_y_start = header_h
    tab_bar_y_end = min(header_h + int(h * 0.06), h)
    tab_region = img.crop((0, tab_bar_y_start, w, tab_bar_y_end))
    tab_pixels = list(tab_region.getdata())
    tab_dark = sum(1 for p in tab_pixels if p[0] < 100 and p[1] < 100 and p[2] < 100)
    tab_has_text = tab_dark > 20

    # ── Content ──
    content_top = header_h
    content_bottom = h - int(h * 0.08)
    content_h_px = content_bottom - content_top

    # Card detection
    cards_raw = []
    in_card = False
    card_start = 0
    for y in range(content_top, content_bottom, 4):
        row = [img.getpixel((x, y)) for x in range(0, w, 8)]
        non_bg = sum(1 for p in row if not (p[0] > 235 and p[1] > 235 and p[2] > 235))
        ratio = non_bg / len(row) if row else 0
        if ratio > 0.15 and not in_card:
            in_card = True
            card_start = y
        elif ratio < 0.05 and in_card:
            in_card = False
            card_height = y - card_start
            if card_height > 30:
                cards_raw.append({"y": card_start, "height": card_height, "y_end": y})
    if in_card:
        card_height = content_bottom - card_start
        if card_height > 30:
            cards_raw.append({"y": card_start, "height": card_height, "y_end": content_bottom})

    # Detailed card analysis
    cards = []
    for c in cards_raw:
        card_type = classify_card(img, c["y"], c["height"], w, content_bottom)

        # Check for image content
        mid_y = min(c["y"] + c["height"] // 2, content_bottom - 1)
        row = [img.getpixel((x, mid_y)) for x in range(0, w, 4)]
        distinct = len(set(row))
        has_image = distinct > 50

        # Check if image is loaded (not gray placeholder)
        img_loaded = False
        gray_pct = 100
        if has_image:
            img_loaded, gray_pct = check_image_loaded(img, c["y"], c["height"], w, h)

        # Check for text content
        text_regions = detect_text_region(img, c["y"], c["y_end"], w)
        text_lines = [t for t in text_regions if t["height"] < 50]

        cards.append({
            "index": len(cards) + 1,
            "y": c["y"],
            "height": c["height"],
            "type": card_type,
            "has_image_region": has_image,
            "image_loaded": img_loaded if has_image else None,
            "image_gray_pct": gray_pct,
            "text_lines": len(text_lines),
        })

    # Gray dividers
    gray_dividers_y = []
    for y in range(content_top, content_bottom, 2):
        row = [img.getpixel((x, y)) for x in range(0, w, 20)]
        avg_r_v = sum(p[0] for p in row) // len(row)
        avg_g_v = sum(p[1] for p in row) // len(row)
        avg_b_v = sum(p[2] for p in row) // len(row)
        if 200 < avg_r_v < 245 and abs(avg_r_v - avg_g_v) < 5 and abs(avg_g_v - avg_b_v) < 5:
            gray_dividers_y.append(y)

    # Merge nearby divider lines
    divider_count = 0
    if gray_dividers_y:
        divider_count = 1
        for i in range(1, len(gray_dividers_y)):
            if gray_dividers_y[i] - gray_dividers_y[i - 1] > 10:
                divider_count += 1

    # Empty space estimation
    content_pixels = list(img.crop((0, content_top, w, content_bottom)).getdata())
    bg_pixels = sum(1 for p in content_pixels if p[0] > 235 and p[1] > 235 and p[2] > 235)
    empty_ratio = bg_pixels / len(content_pixels) * 100 if content_pixels else 0
    card_fill_ratio = 100 - empty_ratio

    # ── Bottom Nav ──
    nav_h = int(h * 0.08)
    nav = img.crop((0, h - nav_h, w, h))
    np = list(nav.getdata())
    nav_white = sum(1 for p in np if p[0] > 240 and p[1] > 240 and p[2] > 240)
    nav_white_pct = nav_white / len(np) * 100 if np else 0
    dark_in_nav = sum(1 for p in np if p[0] < 60 and p[1] < 60 and p[2] < 80)
    selected_nav = sum(1 for p in np if p[0] == 0x17 and p[1] == 0x1e and p[2] == 0x38)

    # Determine which nav item is selected by horizontal position of #171E38
    nav_w = nav.width
    nav_item_w = nav_w / 5
    selected_index = None
    for x in range(0, nav_w):
        for y_offset in range(0, nav_h, 4):
            p = nav.getpixel((x, y_offset))
            if p[0] == 0x17 and p[1] == 0x1e and p[2] == 0x38:
                idx = int(x // nav_item_w)
                if selected_index is None or idx != selected_index:
                    if selected_index is None:
                        selected_index = idx
                break

    # ── Problem Detection ──
    problems = []
    warnings = []

    # 1. Empty content
    if len(cards) == 0:
        problems.append("No cards detected — content area is empty")
    elif len(cards) <= 2:
        warnings.append(f"Only {len(cards)} cards visible — may need to scroll or load more data")

    # 2. Card fill ratio
    if card_fill_ratio < 10 and len(cards) == 0:
        problems.append("Content area is almost entirely empty (%.1f%% fill)" % card_fill_ratio)
    elif card_fill_ratio < 30 and len(cards) > 0:
        warnings.append(f"Low content density ({card_fill_ratio:.0f}% filled by cards)")

    # 3. Header red detection
    if red_pct < 15:
        problems.append(f"Header missing red background (only {red_pct:.0f}% red pixels)")
    elif red_pct > 95:
        warnings.append(f"Header is almost entirely red ({red_pct:.0f}%) — is text/search bar visible?")

    # 4. Theme color check (only flag if it's the OLD red, ignore case)
    old_red = (0xD8, 0x1E, 0x06)
    expected_red = (0xFF, 0x57, 0x57)
    if primary_red and primary_red[0] == old_red:
        warnings.append("Theme red is #D81E06 (old), expected #FF5757 (design)")

    # 5. Header text missing (keep threshold low — white text on red is common)
    if dark_in_header < 10 and white_header_pct < 5 and red_pct > 15:
        warnings.append("Header appears to have no visible content")

    # 6. Search bar (white text search placeholder counts too)
    if search_bar is None and white_header_pct < 3:
        warnings.append("Search bar not detected in header")

    # 7. Tab bar — detect by looking for tab-like structure (horizontal row of distinct elements)
    # even if text is white-on-red (no dark pixels). Check for non-solid-color horizontal band.
    if not tab_has_text:
        tab_band = img.crop((0, tab_bar_y_start, w, tab_bar_y_end))
        tp = list(tab_band.getdata())
        distinct_colors = len(set(tp))
        if distinct_colors < 5:
            warnings.append("Tab bar below header may be missing")

    # 8. Card images
    cards_with_images = [c for c in cards if c["has_image_region"]]
    cards_loaded = [c for c in cards_with_images if c.get("image_loaded")]
    if len(cards_with_images) == 0 and len(cards) > 2:
        warnings.append("No images detected in any card — Coil may not have loaded them")
    elif cards_with_images and len(cards_loaded) < len(cards_with_images):
        loaded_names = [c["index"] for c in cards_loaded]
        all_img = [c["index"] for c in cards_with_images]
        failed = [i for i in all_img if i not in loaded_names]
        warnings.append(f"Images not loaded in cards: {failed} — possible network/Coil issue")

    # 9. Bottom nav white background
    if nav_white_pct < 50:
        problems.append(f"Bottom nav background is only {nav_white_pct:.0f}% white (expected ~100%)")
    elif nav_white_pct < 85:
        warnings.append(f"Bottom nav background is {nav_white_pct:.0f}% white — should be pure white")

    # 10. Bottom nav selected indicator
    if selected_nav == 0:
        warnings.append("No selected tab indicator (#171E38) found in bottom nav")

    # 11. Gray dividers between cards (be more tolerant)
    expected_dividers = max(0, len(cards) - 1)
    if divider_count == 0 and len(cards) >= 2:
        warnings.append("No card dividers found — cards may blend together")
    elif divider_count > expected_dividers * 2 + 2:
        warnings.append(f"Unusually many gray lines ({divider_count})")

    # 12. Header/Card overlap (content usually needs ~20px clearance)
    min_safe_y = content_top + 8
    if cards and cards[0]["y"] < content_top + 2:
        warnings.append("First card starts immediately after header — consider adding padding")

    # 13. Aspect ratio check
    expected_aspect = (564, 1220)  # design spec
    actual_aspect_ratio = w / h
    design_aspect_ratio = expected_aspect[0] / expected_aspect[1]
    if abs(actual_aspect_ratio - design_aspect_ratio) > 0.05:
        warnings.append(
            f"Aspect ratio {w / h:.3f} differs from design {design_aspect_ratio:.3f} ({w}x{h} vs {expected_aspect[0]}x{expected_aspect[1]})")

    # ── Natural Description ──
    desc_parts = []
    desc_parts.append(f"The screen is {w}x{h}px")
    desc_parts.append(f"The top has a {header_h}px red header (avg {avg_red_hex})")
    if search_bar:
        desc_parts.append("with a search bar")
    if dark_in_header > 100:
        desc_parts.append("and visible title/tab text")
    if tab_has_text:
        desc_parts.append(f"Below the header is a tab bar with channel labels")
    desc_parts.append(f"The main content area ({content_h_px}px) is a gray (#F5F5F5) background with {len(cards)} card(s)")

    for c in cards:
        status = []
        if c["has_image_region"]:
            if c["image_loaded"]:
                status.append("image loaded")
            else:
                status.append(f"image gray ({c['image_gray_pct']:.0f}%)")
        status.append(f"{c['text_lines']} text lines")
        desc_parts.append(
            f"  Card {c['index']}: {c['type']} ({c['height']}px) — {', '.join(status)}")

    desc_parts.append(
        f"The bottom has a {nav_h}px navigation bar (white background, {dark_in_nav}px dark elements)" +
        (f", tab #{selected_index} selected" if selected_index is not None else ""))

    description = "\n".join(desc_parts)

    return {
        "dimensions": {"width": w, "height": h},
        "overall_top_colors": top_colors[:8],
        "theme_red": primary_red_hex,
        "header": {
            "height_px": header_h,
            "height_pct": round(header_h / h * 100, 1),
            "avg_color": avg_red_hex,
            "red_pixels_pct": round(red_pct, 1),
            "dark_text_pixels": dark_in_header,
            "search_bar_detected": search_bar is not None,
            "search_bar_y": search_bar["y"] if search_bar else None,
            "tab_bar_detected": tab_has_text,
        },
        "content": {
            "height_px": content_h_px,
            "start_y": content_top,
            "end_y": content_bottom,
            "background_gray_pct": round(empty_ratio, 1),
            "card_fill_pct": round(card_fill_ratio, 1),
            "cards": cards,
            "gray_dividers": divider_count,
        },
        "bottom_nav": {
            "height_px": nav_h,
            "height_pct": round(nav_h / h * 100, 1),
            "bg_white_pct": round(nav_white_pct, 1),
            "selected_color_px": selected_nav,
            "dark_elements_px": dark_in_nav,
            "selected_tab_index": selected_index,
        },
        "description": description,
        "problems": problems,
        "warnings": warnings,
    }


if __name__ == "__main__":
    path = sys.argv[1]
    result = analyze_image(path)
    print(json.dumps(result, indent=2))
