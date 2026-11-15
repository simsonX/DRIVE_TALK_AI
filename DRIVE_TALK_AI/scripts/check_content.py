import json
from pathlib import Path
from collections import Counter
rows=json.loads((Path(__file__).resolve().parents[1]/'app/src/main/assets/lessons/lessons.json').read_text())
assert len(rows)>=50 and len({r['id'] for r in rows})==len(rows)
for row in rows:
    assert set(row)=={'id','category','level','expression','english','korean'}
    assert row['english'].endswith(('.', '?'))
    assert any('가'<=c<='힣' for c in row['korean'])
counts=Counter((r['category'],r['level']) for r in rows)
assert len(counts)==9 and min(counts.values())>=6
print(f'PASS: {len(rows)} original lessons; 9 balanced category/level groups; schema/punctuation/language checks')
print('Grammar and translation were manually reviewed during authoring; no independent native-speaker review claimed.')
