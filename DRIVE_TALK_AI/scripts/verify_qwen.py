"""Real integration evaluation; requires the local server and downloaded model."""
import json,time,urllib.request
from pathlib import Path
out=[]
for path,data in [
    ('generate',dict(level='beginner',category='business',expression='meeting')),
    ('generate',dict(level='intermediate',category='business',expression='meeting')),
    ('question',dict(level='beginner',turn=0)),
    ('feedback',dict(answer='I go to work yesterday.',language='ko')),
]:
    start=time.monotonic()
    request=urllib.request.Request('http://127.0.0.1:8765/'+path,data=json.dumps(data).encode(),headers={'Content-Type':'application/json'})
    try:
        with urllib.request.urlopen(request,timeout=45) as response: value=json.load(response)
        assert value['source'].startswith('qwen:')
        out.append(dict(path=path,request=data,response=value,elapsed_ms=round((time.monotonic()-start)*1000),success=True))
    except Exception as error: out.append(dict(path=path,request=data,error=str(error),success=False))
    print(json.dumps(out[-1],ensure_ascii=False),flush=True)
if len(out)>=2 and out[0]['success'] and out[1]['success']:
    assert out[0]['response']['english']!=out[1]['response']['english'], 'Levels must produce different sentences'
path=Path(__file__).resolve().parents[1]/'docs/qwen-evidence.json'
path.write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n')
raise SystemExit(0 if all(row['success'] for row in out) else 1)
