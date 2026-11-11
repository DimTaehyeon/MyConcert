"""Read-only KOPIS survey. Never prints or stores the API credential in reports.
Run from the project root: python tools/verify-artists.py
Positive samples verify candidate queries, not future schedules or group identity.
"""
import concurrent.futures, datetime, json, os, pathlib, re, time, threading, urllib.error
import urllib.parse, urllib.request, xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
key = os.environ.get('KOPIS_API_KEY')
if not key:
    key = next((x.split('=', 1)[1].strip() for x in (ROOT/'local.properties').read_text().splitlines()
                if x.startswith('kopis.api.key=')), '')
if not key:
    raise SystemExit('Set KOPIS_API_KEY or kopis.api.key in local.properties')
artists = json.loads((ROOT/'app/src/main/assets/artists.json').read_text(encoding='utf-8'))
windows = [('20251101','20251130'), ('20260801','20260831'), ('20261101','20261130')]
last_request = 0.0
rate_lock = threading.Lock()
def request(params, path='pblprfr'):
    global last_request
    url = 'https://www.kopis.or.kr/openApi/restful/' + path + '?' + urllib.parse.urlencode({'service':key, **params})
    for attempt in range(4):
        with rate_lock:
            time.sleep(max(0,0.5-(time.monotonic()-last_request)))
            last_request=time.monotonic()
        try:
            with urllib.request.urlopen(url, timeout=20) as response:
                root = ET.fromstring(response.read())
            break
        except urllib.error.HTTPError as exc:
            if attempt == 3 or exc.code not in (400, 429, 403, 503):
                raise
            time.sleep(2**attempt)
    if root.tag != 'dbs':
        raise ValueError('API error envelope')
    return [{c.tag:(c.text or '') for c in db} for db in root.findall('db')]

def survey(job):
    artist, query, window = job
    try:
        records = request(dict(stdate=window[0], eddate=window[1], cpage=1, rows=100, shprfnm=query))
        return dict(artistId=artist['id'], query=query, start=window[0], end=window[1],
                    count=len(records), capped=len(records)==100, samples=records[:8])
    except Exception as exc:
        return dict(artistId=artist['id'], query=query, start=window[0], end=window[1], error=type(exc).__name__, status=getattr(exc,'code',None))

jobs = [(a,q,w) for a in artists for q in a['aliases'] for w in windows]
results=[]
with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
    for result in pool.map(survey, jobs):
        results.append(result)
        if len(results)%40==0:
            print(f'Surveyed {len(results)}/{len(jobs)} queries', flush=True)

# Fetch detail for deduplicated samples to make the group/member issue reviewable.
unique = {r['mt20id']:r for s in results for r in s.get('samples',[]) if r.get('mt20id')}
details=[]
def detail(item):
    try:
        data=request({},'pblprfr/'+item['mt20id'])
        return data[0] if data else item
    except Exception:
        return item
with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
    details=list(pool.map(detail,unique.values()))
report=dict(checkedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(), windows=windows,
            note='Sampled months only. Positive API results are candidates; identity is validated by ArtistMatcher. Empty samples do not prove absence.',
            results=results, details=details)
(ROOT/'docs').mkdir(exist_ok=True)
(ROOT/'docs/artist-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
summary=[]
for a in artists:
    items=[r for r in results if r['artistId']==a['id']]
    def identity(title, alias):
        needle='[\\s*._’\'‐‑–—-]*'.join(re.escape(c) for c in alias.lower() if c not in ' *._’\'‐‑–—-')
        return bool(re.search(r'(?<![\w])'+needle+r'(?![\w])',title.lower()))
    hits=[r for r in items if any(any(identity(s.get('prfnm',''),alias) for alias in a['aliases']) for s in r.get('samples',[]))]
    summary.append(dict(artistId=a['id'], positiveQueries=list(dict.fromkeys(r['query'] for r in hits)),
                        errors=sum('error' in r for r in items), sampleCount=sum(r.get('count',0) for r in items)))
(ROOT/'docs/artist-query-survey.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(dict(artists=len(artists),requests=len(results),positiveArtists=sum(bool(a['positiveQueries']) for a in summary),
                      errors=sum('error' in r for r in results),details=len(details)),ensure_ascii=False))
