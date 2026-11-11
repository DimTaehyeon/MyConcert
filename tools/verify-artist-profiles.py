"""Check Naver knowledge-profile identity and remote image URLs; never save image bytes."""
import datetime
import argparse
import io
import json
import pathlib
import time
import unicodedata
import urllib.parse
import urllib.request
from bs4 import BeautifulSoup
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[1]
HEADERS = {"User-Agent": "Mozilla/5.0", "Accept-Language": "ko-KR,ko;q=0.9"}


def normalize(value):
    return "".join(c.lower() for c in unicodedata.normalize("NFKC", value) if c.isalnum())


def parse_profile(html, names, group=False):
    soup = BeautifulSoup(html, "html.parser")
    identities = {normalize(name) for name in names}
    candidates = []
    for basic in soup.select("[class*=cmBasicInfoArea], section._au_people_content_wrap .detail_info"):
        card = basic.find_parent(class_="api_subject_bx") or basic.find_parent("section", class_="_au_people_content_wrap")
        if card is None:
            continue
        title = card.select_one("[class*=depthTitle] strong, .cm_top_wrap .area_text_title strong")
        if title is None or normalize(title.get_text()) not in identities:
            continue
        if group and any(dt.get_text().strip() == "출생" for dt in basic.select("dt")):
            continue
        image = basic.select_one("[class*=thumbBox] img[src], .thumb img[src]")
        if image is None:
            continue
        url = image.get("src", "")
        parsed = urllib.parse.urlparse(url)
        original = urllib.parse.parse_qs(parsed.query).get("src", [url])[0]
        if parsed.scheme != "https" or parsed.hostname not in ("search.pstatic.net", "sstatic.naver.net"):
            continue
        if "/people/" not in original:
            continue
        candidates.append({"matchedName": title.get_text().strip(), "imageUrl": url})
    unique = {c["imageUrl"]: c for c in candidates}
    return next(iter(unique.values())) if len(unique) == 1 else None


def main():
    parser=argparse.ArgumentParser();parser.add_argument("--missing-only",action="store_true");args=parser.parse_args()
    artists = json.loads((ROOT / "app/src/main/assets/artists.json").read_text(encoding="utf-8"))
    profiles, failures = [], []
    if args.missing_only:
        profiles=json.loads((ROOT / "app/src/main/assets/artist-profiles.json").read_text(encoding="utf-8"))
    for artist in artists:
        if args.missing_only and any(p["artistId"]==artist["id"] for p in profiles):
            continue
        names = list(dict.fromkeys([artist["name"], artist["english"], *artist["aliases"]]))
        match = None
        try:
            for name in names[:3]:
                time.sleep(0.45)
                source = "https://search.naver.com/search.naver?" + urllib.parse.urlencode({"query": name + " 프로필"})
                request = urllib.request.Request(source, headers=HEADERS)
                with urllib.request.urlopen(request, timeout=18) as response:
                    html = response.read().decode("utf-8")
                match = parse_profile(html, names, artist["kind"] in ("그룹", "밴드"))
                if match:
                    break
            if not match:
                raise ValueError("No unambiguous matching knowledge-profile photo")
            with urllib.request.urlopen(urllib.request.Request(match["imageUrl"], headers=HEADERS), timeout=18) as response:
                data = response.read(2_000_001)
            if len(data) > 2_000_000:
                raise ValueError("Image too large")
            with Image.open(io.BytesIO(data)) as image:
                width, height = image.size
            profiles.append({"artistId": artist["id"], **match, "sourceUrl": source, "width": width, "height": height})
            print(f'{artist["id"]}: OK {match["matchedName"]} {width}x{height}', flush=True)
        except Exception as error:
            failures.append({"artistId": artist["id"], "error": str(error)})
            print(f'{artist["id"]}: {error}', flush=True)
        report = {"checkedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(), "profiles": profiles, "failures": failures}
        (ROOT / "docs/artist-profile-verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    (ROOT / "app/src/main/assets/artist-profiles.json").write_text(json.dumps(profiles, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Verified {len(profiles)}/{len(artists)}; image files saved: 0", flush=True)


if __name__ == "__main__":
    main()
