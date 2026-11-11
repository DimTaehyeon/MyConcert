import datetime,json,pathlib,time,urllib.parse,urllib.request,urllib.error,xml.etree.ElementTree as E
ROOT=pathlib.Path(__file__).resolve().parents[1]
KEY=next(x.split('=',1)[1].strip() for x in (ROOT/'local.properties').read_text().splitlines() if x.startswith('kopis.api.key='))
def get(path,params):
    for attempt in range(4):
        time.sleep(.7)
        try:
            url='https://www.kopis.or.kr/openApi/restful/'+path+'?'+urllib.parse.urlencode({'service':KEY,**params})
            with urllib.request.urlopen(url,timeout=25) as r: root=E.fromstring(r.read())
            if root.tag!='dbs':raise ValueError('Unexpected response')
            return [{c.tag:(c.text or '').strip() for c in db} for db in root.findall('db')]
        except urllib.error.HTTPError:
            if attempt==3:raise
            time.sleep(2)
records={};counts=[]
for code in ['12','45','52']:
    for genre in ['CCCD','EEEA']:
        result=get('pblprfr',dict(stdate='20261111',eddate='20261211',cpage=1,rows=100,signgucode=code,shcate=genre))
        counts.append(dict(code=code,genre=genre,count=len(result)))
        print(json.dumps(counts[-1]),flush=True)
        if code=='12':records.update({r['mt20id']:r for r in result})
facilities={}
for r in records.values():
    detail=get('pblprfr/'+r['mt20id'],{})[0]
    facilityId=detail.get('mt10id')
    if facilityId and facilityId not in facilities:
        facilities[facilityId]=get('prfplc/'+facilityId,{})[0]
    r['detail']=detail;r['facility']=facilities.get(facilityId,{})
    print(json.dumps(dict(title=r['prfnm'],address=r['facility'].get('adres')),ensure_ascii=False),flush=True)
report=dict(checkedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),start='2026-11-11',end='2026-12-11',counts=counts,records=list(records.values()))
(ROOT/'docs/qa-region-migration.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
