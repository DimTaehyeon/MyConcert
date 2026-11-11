"""Compare regional and unfiltered KOPIS requests. Never records the API key."""
import datetime, json, pathlib, time, urllib.parse, urllib.request, urllib.error
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
KEY = next(x.split('=',1)[1].strip() for x in (ROOT/'local.properties').read_text().splitlines() if x.startswith('kopis.api.key='))
START = datetime.date(2026,11,11)
END = START + datetime.timedelta(days=30)
results=[]
def fetch(params):
    for attempt in range(3):
        time.sleep(0.65)
        try:
            url='https://www.kopis.or.kr/openApi/restful/pblprfr?'+urllib.parse.urlencode({'service':KEY,'stdate':START.strftime('%Y%m%d'),'eddate':END.strftime('%Y%m%d'),'rows':100,**params})
            with urllib.request.urlopen(url,timeout=25) as r:
                root=ET.fromstring(r.read())
            if root.tag!='dbs': raise ValueError('Unexpected API response')
            return [{c.tag:(c.text or '').strip() for c in db} for db in root.findall('db')]
        except urllib.error.HTTPError:
            if attempt==2: raise
            time.sleep(2)
def save():
    report=dict(checkedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),start=START.isoformat(),end=END.isoformat(),
                hasArtistOrTitleFilter=False,results=results)
    (ROOT/'docs/qa-region-audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
for name,code in [('광주','29'),('전남','46'),('서울','11'),('전국',None)]:
    for genre in ['CCCD','EEEA',None]:
        if name=='전국' and genre is None: continue
        all_records=[]
        for page in range(1,20):
            params={'cpage':page}
            if code: params['signgucode']=code
            if genre: params['shcate']=genre
            records=fetch(params)
            all_records+=records
            if len(records)<100: break
        entry=dict(region=name,code=code,genre=genre or 'ALL',count=len(all_records),records=all_records)
        results.append(entry)
        save()
        from collections import Counter
        print(json.dumps({**{k:v for k,v in entry.items() if k!='records'},'areas':dict(Counter(r.get('area') for r in all_records))},ensure_ascii=False),flush=True)
save()
