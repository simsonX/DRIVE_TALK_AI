"""Loopback-only Qwen bridge. No API keys, no conversation logs, no mock inference."""
import json
import os
import re
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

MODEL = os.environ.get('QWEN_MODEL', 'qwen3:4b')
OLLAMA = os.environ.get('OLLAMA_URL', 'http://127.0.0.1:11434')
PROMPTS = Path(__file__).parent / 'prompts'
LEVELS = {'beginner', 'intermediate', 'advanced'}
CATEGORIES = {'business', 'daily', 'travel'}
SCHEMAS = {
    '/generate': {'type': 'object', 'properties': {'english': {'type': 'string'}, 'korean': {'type': 'string'}}, 'required': ['english', 'korean'], 'additionalProperties': False},
    '/question': {'type': 'object', 'properties': {'question': {'type': 'string'}}, 'required': ['question'], 'additionalProperties': False},
    '/feedback': {'type': 'object', 'properties': {'corrected': {'type': 'string'}, 'explanation': {'type': 'string'}, 'uncertain': {'type': 'boolean'}}, 'required': ['corrected', 'explanation', 'uncertain'], 'additionalProperties': False},
}

def bounded_string(data, key, low=1, high=500):
    value = data.get(key)
    if not isinstance(value, str) or not low <= len(value.strip()) <= high:
        raise ValueError(f'invalid {key}')
    if any(ord(c) < 32 and c not in '\n\t' for c in value):
        raise ValueError('control characters')
    return value.strip()

def validate_request(path, data):
    if not isinstance(data, dict): raise ValueError('object required')
    if path == '/generate':
        if not {'level', 'category', 'expression'} <= set(data) or set(data) - {'level', 'category', 'expression', 'previous_examples'}: raise ValueError('unexpected fields')
        previous = data.get('previous_examples', [])
        if not isinstance(previous, list) or len(previous) > 6: raise ValueError('invalid previous_examples')
        for sentence in previous:
            bounded_string({'sentence': sentence}, 'sentence', 1, 300)
        if data.get('category') not in CATEGORIES: raise ValueError('invalid category')
        bounded_string(data, 'expression', 1, 80)
    elif path == '/question':
        if set(data) != {'level', 'turn'}: raise ValueError('unexpected fields')
        if type(data.get('turn')) is not int or not 0 <= data['turn'] < 5: raise ValueError('invalid turn')
    elif path == '/feedback':
        if set(data) != {'answer', 'language'}: raise ValueError('unexpected fields')
        bounded_string(data, 'answer')
        if data.get('language') not in {'ko', 'en'}: raise ValueError('invalid language')
    else: raise ValueError('unknown route')
    if path != '/feedback' and data.get('level') not in LEVELS: raise ValueError('invalid level')
    return data

def normalized_english(value):
    return re.sub(r'[^a-z0-9]+', '', value.lower())

def validate_output(path, value, request=None):
    if not isinstance(value, dict) or set(value) != set(SCHEMAS[path]['required']): raise ValueError('JSON schema mismatch')
    for key in value:
        if key == 'uncertain':
            if type(value[key]) is not bool: raise ValueError('boolean required')
        else: bounded_string(value, key, 2, 500 if key in {'korean', 'explanation'} else 300)
    english = value.get('english', value.get('question', value.get('corrected', '')))
    if not re.search('[A-Za-z]', english) or re.search('[가-힣]', english): raise ValueError('English required')
    if path == '/generate':
        if not re.search('[가-힣]', value['korean']): raise ValueError('Korean translation required')
        words = len(value['english'].split())
        limits = {'beginner': (3, 12), 'intermediate': (8, 24), 'advanced': (10, 40)}
        low, high = limits[(request or {}).get('level', 'intermediate')]
        if not low <= words <= high: raise ValueError('level word-count check failed')
        previous = (request or {}).get('previous_examples', [])
        if normalized_english(value['english']) in {normalized_english(x) for x in previous}:
            raise ValueError('repeated English sentence')
    if path == '/feedback' and (request or {}).get('language') == 'ko' and not re.search('[가-힣]', value['explanation']):
        raise ValueError('Korean explanation required')
    if path == '/feedback' and request and value['corrected'].strip().lower() != request['answer'].strip().lower():
        if re.search(r'no correction|already.*natural|수정.{0,5}필요.{0,4}없', value['explanation'], re.I):
            raise ValueError('contradictory feedback')
    return value

def infer(path, data):
    validate_request(path, data)
    template_name = 'feedback-ko' if path == '/feedback' and data['language'] == 'ko' else path[1:]
    template = (PROMPTS / (template_name + '.txt')).read_text()
    if path == '/question':
        topics = ['what the learner did yesterday', "the learner's work or job", "the learner's weekend plans", 'a place the learner wants to visit', 'a daily habit the learner enjoys']
        template = template.replace('{{topic}}', topics[data['turn']])
    user_content = json.dumps(data, ensure_ascii=False)
    if path == '/feedback' and data['language'] == 'ko':
        user_content += '\nJSON 형식으로 답하세요. explanation은 반드시 한국어로 작성하세요.'
    payload = {'model': MODEL, 'stream': False, 'think': False, 'format': SCHEMAS[path], 'keep_alive': '10m',
               'messages': [{'role': 'user', 'content': template + '\n\n요청 데이터 / Request data:\n' + user_content}],
               'options': {'temperature': 0.7 if path == '/generate' else 0.25, 'num_predict': 350, 'num_ctx': 2048}}
    last = None
    started = time.monotonic()
    # One bounded retry for transport or invalid model JSON; no synthetic substitution.
    for attempt in range(2):
        try:
            req = urllib.request.Request(OLLAMA + '/api/chat', data=json.dumps(payload).encode(), headers={'Content-Type': 'application/json'})
            with urllib.request.urlopen(req, timeout=18) as response:
                raw = response.read(65537)
                if len(raw) > 65536: raise ValueError('model output too large')
                result = json.loads(raw)
            value = validate_output(path, json.loads(result['message']['content']), data)
            return {**value, 'source': 'qwen:' + MODEL, 'elapsed_ms': round((time.monotonic()-started)*1000), 'validation': 'schema-and-language-rules; not human-reviewed'}
        except (ValueError, KeyError, OSError, urllib.error.URLError) as error:
            last = error
            if attempt == 0:
                if path == '/generate':
                    payload['messages'].append({'role':'user', 'content':'The previous output failed validation. Create a different natural English sentence, avoiding every previous_examples entry. Keep the same requested level, category and expression, with a matching Korean translation.'})
                time.sleep(0.2)
    raise RuntimeError('Qwen inference or JSON validation failed') from last

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_): pass  # Avoid recording user answers.
    def send(self, code, body):
        output = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(code); self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(output))); self.end_headers(); self.wfile.write(output)
    def do_GET(self):
        if self.path != '/health': return self.send(404, {'error': 'not found'})
        try:
            with urllib.request.urlopen(OLLAMA + '/api/tags', timeout=2) as response: models = json.load(response)['models']
            available = any(m['name'] == MODEL for m in models)
            self.send(200 if available else 503, {'model': MODEL, 'available': available, 'inference_verified': False})
        except OSError: self.send(503, {'model': MODEL, 'available': False})
    def do_POST(self):
        if self.path not in SCHEMAS: return self.send(404, {'error': 'not found'})
        try:
            size = int(self.headers.get('Content-Length', '0'))
            if not 0 < size <= 4096: raise ValueError('body size')
            data = json.loads(self.rfile.read(size)); validate_request(self.path, data)
        except (ValueError, UnicodeError): return self.send(400, {'error': 'invalid request'})
        try: self.send(200, infer(self.path, data))
        except RuntimeError: self.send(503, {'error': 'Qwen unavailable or output rejected; use reviewed base lessons'})

if __name__ == '__main__':
    print(f'DRIVE TALK AI on 127.0.0.1:8765 | {MODEL}', flush=True)
    ThreadingHTTPServer(('127.0.0.1', 8765), Handler).serve_forever()
