"""Derive an external test corpus; do not modify or include vehicle logs in source."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('archive', type=Path)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
count = 0
with zipfile.ZipFile(args.archive) as archive, args.output.open('x', encoding='utf-8', newline='\n') as target:
    name, = [name for name in archive.namelist() if name.endswith('raw_io.jsonl')]
    with archive.open(name) as source:
        for raw in source:
            if not raw.strip():
                continue
            row = json.loads(raw)
            fields = [row['command_sent'], str(row['prompt_seen']).lower(), row['status'], row['normalized_hex']]
            fields += [base64.b64encode(line.encode('utf-8')).decode('ascii') for line in row['raw_response_lines']]
            assert all('\t' not in field and '\n' not in field for field in fields)
            target.write('\t'.join(fields) + '\n')
            count += 1
print(json.dumps({'transactions': count, 'archive_sha256': hashlib.sha256(args.archive.read_bytes()).hexdigest(),
                  'corpus_sha256': hashlib.sha256(args.output.read_bytes()).hexdigest()}))
