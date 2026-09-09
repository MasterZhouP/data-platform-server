"""Validate the handoff contract and mock examples; does not test deployed APIs."""
import argparse
import json
import re
import sys
import warnings
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--library-dir')
args = parser.parse_args()
if args.library_dir:
    sys.path.insert(0, args.library_dir)
warnings.filterwarnings('ignore', category=DeprecationWarning)
from jsonschema import RefResolver
from openapi_spec_validator import validate
from openapi_schema_validator import OAS30Validator

root = Path(__file__).resolve().parent
def read(path):
    return json.loads(path.read_text(encoding='utf-8'))

spec = read(root / 'openapi.json')
validate(spec)
resolver = RefResolver.from_schema(spec)
mapping = read(root / 'example-schema-map.json')
for filename, model in mapping.items():
    OAS30Validator(spec['components']['schemas'][model], resolver=resolver).validate(read(root / 'examples' / filename))

meta = read(root / 'examples/metadata.response.json')['data']
result = meta['resultSets'][0]
fields = {f['name']: f for f in result['fields']}
assert len(fields) == len(result['fields']) == 14
expected = set('jz zdwbm fdwbm cInvCode cInvName cInvStd zdw fdw hsl zldj iMassDate chdl cd mrscbm'.split())
assert set(fields) == expected
for part in ['displayFields', 'filterFields']:
    assert set(result['defaults'][part]) <= fields.keys()
for key in result['defaults']['filterFields']:
    assert fields[key]['filterOperators']
for sort in result['defaults']['sort']:
    assert fields[sort['field']]['sortable']

for filename in ['query.response.json', 'query-empty.response.json']:
    data = read(root / 'examples' / filename)['data']
    assert data['metadataVersion'] == meta['metadataVersion']
    assert data['totalPages'] == (data['total'] + data['pageSize'] - 1) // data['pageSize']
    for row in data['rows']:
        assert set(row) == fields.keys()
        for key, value in row.items():
            assert value is None or isinstance(value, str)
            if value is not None and fields[key]['dataType'] == 'INTEGER':
                assert re.fullmatch(r'-?\d+', value)
            if value is not None and fields[key]['dataType'] == 'DECIMAL':
                assert re.fullmatch(r'-?\d+(\.\d+)?', value)
rows = read(root / 'examples/query.response.json')['data']['rows']
assert rows[0]['cInvCode'].startswith('0')
assert rows[0]['cInvCode'] == rows[1]['cInvCode'] and rows[0]['hsl'] != rows[1]['hsl']
assert rows[0]['cd'] is None and rows[0]['zldj'] == ''
binding = read(root / 'examples/plugin-binding.example.json')
assert all(m['sourceField'] in fields for m in binding['mappings'])
assert any(m['sourceField'] == 'mrscbm' for m in binding['mappings'])

request = read(root / 'examples/query.request.json')
validator = OAS30Validator(spec['components']['schemas']['QueryRequest'], resolver=resolver)
negative = [{**request, 'whereString': 'where 1=1'}, {**request, 'pageSize': 201},
            {**request, 'pageNum': 0}, {**request, 'filter': {'logic': 'AND', 'conditions': []}},
            {k: v for k, v in request.items() if k != 'metadataVersion'}]
for invalid in negative:
    assert list(validator.iter_errors(invalid)), f'Invalid example was accepted: {invalid}'

operation_ids = []
for path, methods in spec['paths'].items():
    for method, operation in methods.items():
        operation_ids.append(operation['operationId'])
        assert any(p['name'] == 'X-Request-Id' and p['required'] for p in operation['parameters'])
        assert path in (root / 'README.md').read_text(encoding='utf-8')
assert len(operation_ids) == len(set(operation_ids)) == 4
assert spec['security'] == [{'IntegrationKey': []}]
assert spec['components']['securitySchemes']['IntegrationKey']['name'] == 'X-Integration-Key'
for url in re.findall(r'\]\(([^)]+)\)', (root / 'README.md').read_text(encoding='utf-8')):
    if '://' not in url:
        assert (root / url.split('#')[0]).exists(), url

report = {
    'status': 'PASS', 'scope': '静态契约校验，不代表已实现或部署，不包含真实OA/U8联调',
    'openapiVersion': spec['openapi'], 'apiVersion': spec['info']['version'],
    'operationCount': len(operation_ids), 'schemaCount': len(spec['components']['schemas']),
    'validatedApiExamples': len(mapping), 'rejectedInvalidRequestExamples': len(negative),
    'materialFieldCount': len(fields),
    'checks': ['OpenAPI规范', '全部API样例schema', '元数据和结果字段一致', '前导零/精度/空值/同编码多行',
               '映射引用合法', '分页总数一致', '认证与请求ID声明', '文档路径与本地链接'],
    'remainingRuntimeChecks': 'README第9节全部真实环境验收项'
}
(root / 'validation.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps(report, ensure_ascii=False))
