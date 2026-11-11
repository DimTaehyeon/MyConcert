import pathlib,datetime,json,urllib.request,urllib.parse,urllib.error,time,xml.etree.ElementTree as E
ROOT=pathlib.Path(__file__).resolve().parents[1]
KEY=next(x.split('=',1)[1].strip() for x in (ROOT/'local.properties').read_text().splitlines() if x.startswith('kopis.api.key='))
def get(path,params):
    for attempt in range(3):
        time.sleep(.7)
        try:
            url='https://www.kopis.or.kr/openApi/restful/'+path+'?'+urllib.parse.urlencode({'service':KEY,**params})
            with urllib.request.urlopen(url,timeout=25) as response:root=E.fromstring(response.read())
            if root.tag!='dbs':raise ValueError('API envelope')
            return [{e.tag:(e.text or '').strip() for e in row} for row in root.findall('db')]
        except urllib.error.HTTPError:
            if attempt==2:raise
            time.sleep(2)
results=[];details={}
for query in ['실리카겔','Silica Gel','QWER','터치드','TOUCHED','한로로','HANRORO','브로콜리너마저']:
    records={}
    for start,end in [('20261012','20261111'),('20261112','20261211')]:
        items=get('pblprfr',dict(stdate=start,eddate=end,cpage=1,rows=100,shprfnm=query))
        records.update({r['mt20id']:r for r in items})
    result=dict(query=query,count=len(records),records=list(records.values()))
    results.append(result)
    print(json.dumps(dict(query=query,count=len(records)),ensure_ascii=False),flush=True)
    for record in list(records.values())[:4]:
        key=record['mt20id']
        if key not in details:
            items=get('pblprfr/'+key,{})
            if items:details[key]=items[0]
    (ROOT/'docs/qa-artist-discovery.json').write_text(json.dumps(dict(checkedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),results=results,details=list(details.values())),ensure_ascii=False,indent=2),encoding='utf-8')
