"""Rebuild deterministic OpenAPI and simulated handoff examples; no network/DB calls."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
EXAMPLES = ROOT / 'examples'
EXAMPLES.mkdir(exist_ok=True)
RID = '83c87e30-8aba-4a82-b5d4-9233f52ead48'

def obj(props, required=None, additional=False):
    required_fields = list(props) if required is None else required
    return {'type': 'object', 'properties': props,
            **({'required': required_fields} if required_fields else {}), 'additionalProperties': additional}

def string(**kw):
    return {'type': 'string', **kw}

def integer(minimum=0, maximum=None, **kw):
    return {'type': 'integer', 'minimum': minimum, **({'maximum': maximum} if maximum is not None else {}), **kw}

def array(items, **kw):
    return {'type': 'array', 'items': items, **kw}

def ref(name):
    return {'$ref': '#/components/schemas/' + name}

def dump(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

OPERATORS = ['eq', 'ne', 'gt', 'ge', 'lt', 'le', 'contains', 'startsWith', 'endsWith',
             'between', 'in', 'isNull', 'isNotNull']
TYPES = ['STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME']
name = string(minLength=1, maxLength=128)
cell = string(nullable=True)
version = string(minLength=1, maxLength=128)
page = {'pageNum': integer(1, 100000), 'pageSize': integer(1, 200),
        'total': integer(0, 9007199254740991), 'totalPages': integer(0, 9007199254740991)}
schemas = {}
schemas['Sort'] = obj({'field': name, 'direction': string(enum=['ASC', 'DESC'])})
schemas['FilterCondition'] = obj({'field': name, 'operator': string(enum=OPERATORS),
    'values': array(string(maxLength=1000), maxItems=100)}, additional=False)
schemas['FilterGroup'] = obj({'logic': string(enum=['AND', 'OR']), 'conditions': array(
    {'oneOf': [ref('FilterCondition'), ref('FilterGroup')]}, minItems=1, maxItems=20)})
schemas['FilterGroup']['description'] = '最多3层组、全树20个叶子。操作符取值数量和类型另按元数据及文档校验。'
schemas['Field'] = obj({'name': name, 'label': string(minLength=1), 'order': integer(1),
    'dataType': string(enum=TYPES), 'nullable': {'type': 'boolean'},
    'filterOperators': array(string(enum=OPERATORS), uniqueItems=True), 'sortable': {'type': 'boolean'}})
schemas['Parameter'] = obj({'name': name, 'label': string(minLength=1), 'dataType': string(enum=TYPES),
    'required': {'type': 'boolean'}, 'nullable': {'type': 'boolean'}})
schemas['Defaults'] = obj({'displayFields': array(name, minItems=1, uniqueItems=True),
    'filterFields': array(name, uniqueItems=True), 'sort': array(ref('Sort'), maxItems=5),
    'pageSize': integer(1, 200)})
schemas['Limits'] = obj({'maxPageSize': integer(200, 200), 'maxFilterConditions': integer(20, 20),
    'maxFilterDepth': integer(3, 3), 'maxSortFields': integer(5, 5), 'maxInValues': integer(100, 100),
    'queryTimeoutMs': integer(10000, 10000)})
schemas['ResultSet'] = obj({'resultSetCode': name, 'resultSetName': string(minLength=1),
    'selectionMode': string(enum=['SINGLE']), 'fields': array(ref('Field'), minItems=1),
    'parameters': array(ref('Parameter')), 'defaults': ref('Defaults'), 'limits': ref('Limits')})
schemas['Metadata'] = obj({'taskCode': name, 'taskName': string(minLength=1),
    'taskType': string(enum=['REFERENCE']), 'executionMode': string(enum=['SYNC_QUERY']),
    'metadataVersion': version, 'resultSets': array(ref('ResultSet'), minItems=1)})
schemas['Task'] = obj({'taskCode': name, 'taskName': string(minLength=1), 'metadataVersion': version})
schemas['TaskPage'] = obj({'items': array(ref('Task')), **page})
schemas['TaskPage']['properties']['pageSize'] = integer(1, 100)
schemas['Status'] = obj({'status': string(enum=['UP']), 'apiVersion': string(enum=['1.0.0']),
                         'clientId': name})
schemas['Context'] = obj({key: string(nullable=True, minLength=1, maxLength=128)
                         for key in ['formId', 'masterId', 'summaryId']}, required=[])
schemas['QueryRequest'] = obj({'resultSetCode': name, 'metadataVersion': version,
    'pageNum': integer(1, 100000, default=1), 'pageSize': integer(1, 200, default=200),
    'filter': ref('FilterGroup'), 'sort': array(ref('Sort'), maxItems=5),
    'parameters': {'type': 'object', 'additionalProperties': cell}, 'context': ref('Context')},
    required=['resultSetCode', 'metadataVersion'])
schemas['Row'] = {'type': 'object', 'additionalProperties': cell,
    'description': '所有发布的SQL字段必须出现。动态字段，不按显示列投影。单元格字符串或null。'}
schemas['QueryPage'] = obj({'taskCode': name, 'resultSetCode': name, 'metadataVersion': version,
    'rows': array(ref('Row'), maxItems=200), **page})
common = {'code': string(enum=['OK']), 'message': string(), 'requestId': string(format='uuid'),
          'executionId': string(nullable=True, minLength=1)}
for model in ['Status', 'TaskPage', 'Metadata', 'QueryPage']:
    schemas[model + 'Response'] = obj({**common, 'data': ref(model)})
    if model == 'QueryPage':
        schemas[model + 'Response']['properties']['executionId'] = string(minLength=1)
    else:
        schemas[model + 'Response']['properties']['executionId'] = string(nullable=True, enum=[None])

ERRORS = {
    '400': ['INVALID_ARGUMENT', 'FILTER_NOT_ALLOWED', 'SORT_NOT_ALLOWED', 'PARAMETER_NOT_ALLOWED'],
    '401': ['UNAUTHORIZED'], '403': ['FORBIDDEN'],
    '404': ['TASK_NOT_FOUND', 'RESULT_SET_NOT_FOUND'],
    '409': ['TASK_DISABLED', 'METADATA_VERSION_MISMATCH', 'RESULT_SCHEMA_MISMATCH'],
    '413': ['REQUEST_TOO_LARGE'], '415': ['UNSUPPORTED_MEDIA_TYPE'],
    '503': ['DATASOURCE_UNAVAILABLE', 'SERVICE_UNAVAILABLE'],
    '504': ['QUERY_TIMEOUT'], '500': ['INTERNAL_ERROR']}
schemas['ErrorResponse'] = obj({**common,
    'code': string(enum=[code for codes in ERRORS.values() for code in codes]),
    'data': {'type': 'object', 'nullable': True, 'enum': [None]},
    'error': obj({'retryable': {'type': 'boolean'}, 'details': array(obj({'field': string(), 'reason': string()}))})})

def envelope(data, execution=None):
    return {'code': 'OK', 'message': '成功', 'requestId': RID, 'executionId': execution, 'data': data}

def error(code, message, field='', execution=None):
    return {'code': code, 'message': message, 'requestId': RID, 'executionId': execution, 'data': None,
        'error': {'retryable': code in ['DATASOURCE_UNAVAILABLE', 'SERVICE_UNAVAILABLE', 'QUERY_TIMEOUT'],
                  'details': [{'field': field, 'reason': message}] if field else []}}

labels = [('zldj', '质量等级', 1), ('iMassDate', '保质期天数', 2), ('cInvCode', '编码', 3),
    ('fdw', '辅单位', 4), ('cInvStd', '规格', 6), ('zdw', '主单位', 7), ('cInvName', '名称', 8),
    ('zdwbm', '主单位编码', 9), ('fdwbm', '辅单位编码', 10), ('chdl', '存货大类', 11),
    ('hsl', '换算率', 12), ('cd', '产地', 13), ('jz', '净重', 14), ('mrscbm', '默认生产部门', 15)]
fields = []
for key, label, order in labels:
    dtype = 'DECIMAL' if key in ['jz', 'hsl'] else 'INTEGER' if key == 'iMassDate' else 'STRING'
    filters = ['eq', 'contains', 'startsWith', 'isNull', 'isNotNull'] if key in ['cInvCode', 'cInvName', 'cInvStd'] else []
    fields.append({'name': key, 'label': label, 'order': order, 'dataType': dtype, 'nullable': True,
                   'filterOperators': filters, 'sortable': key in ['cInvCode', 'cInvName', 'cInvStd']})
metadata = {'taskCode': 'U8_MATERIAL_REFERENCE', 'taskName': '物料参照', 'taskType': 'REFERENCE',
    'executionMode': 'SYNC_QUERY', 'metadataVersion': '1', 'resultSets': [{
        'resultSetCode': 'tou', 'resultSetName': '物料参照', 'selectionMode': 'SINGLE',
        'fields': fields, 'parameters': [],
        'defaults': {'displayFields': ['cInvCode', 'cInvName', 'cInvStd', 'zdw', 'fdw', 'zldj'],
                     'filterFields': ['cInvCode', 'cInvName', 'cInvStd'],
                     'sort': [{'field': 'cInvCode', 'direction': 'ASC'}], 'pageSize': 200},
        'limits': {'maxPageSize': 200, 'maxFilterConditions': 20, 'maxFilterDepth': 3,
                   'maxSortFields': 5, 'maxInValues': 100, 'queryTimeoutMs': 10000}}]}
query = {'resultSetCode': 'tou', 'metadataVersion': '1', 'pageNum': 1, 'pageSize': 200,
    'filter': {'logic': 'AND', 'conditions': [{'field': 'cInvName', 'operator': 'contains', 'values': ['清洗']}]},
    'sort': [{'field': 'cInvCode', 'direction': 'ASC'}], 'parameters': {},
    'context': {'formId': 'demo-form', 'masterId': None}}
row = {'jz': '25.000000', 'zdwbm': '0001', 'fdwbm': '0002', 'cInvCode': '00000050003',
    'cInvName': '清洗剂（模拟物料）', 'cInvStd': '10kg*2桶/箱', 'zdw': '桶', 'fdw': '箱',
    'hsl': '2.000000', 'zldj': '', 'iMassDate': '365', 'chdl': '模拟存货类', 'cd': None, 'mrscbm': 'DEMO001'}
row2 = {**row, 'hsl': '2.100000'}
query_page = {'taskCode': 'U8_MATERIAL_REFERENCE', 'resultSetCode': 'tou', 'metadataVersion': '1',
    'rows': [row, row2], 'total': 2, 'pageNum': 1, 'pageSize': 200, 'totalPages': 1}
samples = {
    'status.response': ('StatusResponse', envelope({'status': 'UP', 'apiVersion': '1.0.0', 'clientId': 'oa-plugin-test'})),
    'tasks.response': ('TaskPageResponse', envelope({'items': [{'taskCode': 'U8_MATERIAL_REFERENCE',
        'taskName': '物料参照', 'metadataVersion': '1'}], 'total': 1, 'pageNum': 1, 'pageSize': 20, 'totalPages': 1})),
    'metadata.response': ('MetadataResponse', envelope(metadata)),
    'query.request': ('QueryRequest', query),
    'query.response': ('QueryPageResponse', envelope(query_page, '9007199254740993')),
    'query-empty.response': ('QueryPageResponse', envelope({**query_page, 'rows': [], 'total': 0, 'totalPages': 0}, '9007199254740994')),
    'query-version-conflict.response': ('ErrorResponse', error('METADATA_VERSION_MISMATCH', '参照配置已更新，请刷新配置后重试', 'metadataVersion')),
    'query-timeout.response': ('ErrorResponse', error('QUERY_TIMEOUT', '查询超时，请缩小筛选范围或稍后重试', execution='9007199254740995')),
    'unauthorized.response': ('ErrorResponse', error('UNAUTHORIZED', '服务凭据缺失或无效')),
}
for filename, (_, value) in samples.items():
    dump(EXAMPLES / (filename + '.json'), value)
binding = {'description': '仅为插件内部配置示意，非平台API；所有OA字段ID均为占位值',
    'serviceName': 'BL数据交换平台', 'taskCode': 'U8_MATERIAL_REFERENCE', 'resultSetCode': 'tou',
    'metadataVersion': '1', 'selectorField': 'field0001', 'selectionMode': 'SINGLE',
    'displayFields': metadata['resultSets'][0]['defaults']['displayFields'],
    'filterFields': metadata['resultSets'][0]['defaults']['filterFields'],
    'mappings': [{'sourceField': src, 'targetField': dst} for src, dst in
                 [('cInvCode', 'field0001'), ('cInvName', 'field0002'), ('cInvStd', 'field0003'),
                  ('zdw', 'field0004'), ('hsl', 'field0005'), ('mrscbm', 'field0006')]]}
dump(EXAMPLES / 'plugin-binding.example.json', binding)
dump(ROOT / 'example-schema-map.json', {key + '.json': model for key, (model, _) in samples.items()})

headers = {'X-Request-Id': {'description': '关联请求UUID', 'schema': string(format='uuid')},
           'Cache-Control': {'description': '固定no-store', 'schema': string(enum=['no-store'])}}
def response(model, description, example=None):
    media = {'schema': ref(model)}
    if example is not None:
        media['example'] = example
    return {'description': description, 'headers': headers, 'content': {'application/json': media}}

request_id = {'name': 'X-Request-Id', 'in': 'header', 'required': True,
              'schema': string(format='uuid'), 'description': '每次HTTP请求生成UUID，重试使用新值'}
task_code = {'name': 'taskCode', 'in': 'path', 'required': True, 'schema': name,
             'example': 'U8_MATERIAL_REFERENCE'}
def operation(operation_id, summary, model, sample_name, codes, params=None):
    responses = {'200': response(model, '成功', samples[sample_name][1])}
    for status in codes:
        responses[status] = response('ErrorResponse', ' / '.join(ERRORS[status]))
    return {'operationId': operation_id, 'summary': summary, 'tags': ['Reference'],
            'parameters': [request_id] + (params or []), 'responses': responses}

paths = {
    '/status': {'get': operation('getIntegrationStatus', '检查协议入口和服务身份；不测试U8',
        'StatusResponse', 'status.response', ['400', '401', '403', '500', '503'])},
    '/reference/tasks': {'get': operation('listReferenceTasks', '列出已启用且有权限的参照任务',
        'TaskPageResponse', 'tasks.response', ['400', '401', '403', '500', '503'], [
            {'name': 'keyword', 'in': 'query', 'schema': string(maxLength=100)},
            {'name': 'pageNum', 'in': 'query', 'schema': integer(1, 100000, default=1)},
            {'name': 'pageSize', 'in': 'query', 'schema': integer(1, 100, default=20)}])},
    '/reference/tasks/{taskCode}/metadata': {'get': operation('getReferenceMetadata', '读取完整元数据和查询能力',
        'MetadataResponse', 'metadata.response', ['400', '401', '403', '404', '409', '500', '503'], [task_code])},
    '/reference/tasks/{taskCode}/query': {'post': operation('queryReference', '同步查询；每行保留全部结果字段',
        'QueryPageResponse', 'query.response', list(ERRORS), [task_code])},
}
paths['/reference/tasks/{taskCode}/query']['post']['requestBody'] = {
    'required': True, 'description': '最大64KiB；不接受SQL/whereString/字段投影。',
    'content': {'application/json': {'schema': ref('QueryRequest'), 'example': query}}}
oas = {'openapi': '3.0.3', 'info': {'title': 'data-platform / OA 参照接口', 'version': '1.0.0',
    'description': '开发契约，尚未部署。完全独立于DEE。全部样例为模拟数据，逻辑类型须在真实SQL发布时验证。'},
    'servers': [{'url': 'https://platform.example.invalid/integration/openapi/v1',
                 'description': '占位地址，联调时替换'}],
    'security': [{'IntegrationKey': []}], 'paths': paths,
    'components': {'securitySchemes': {'IntegrationKey': {'type': 'apiKey', 'in': 'header',
        'name': 'X-Integration-Key', 'description': '由平台发给OA插件服务端的专用凭据；不进入浏览器'}},
        'schemas': schemas}}
dump(ROOT / 'openapi.json', oas)

http = '''# 替换baseUrl和integrationKey。真实密钥仅放本地环境变量，勿提交或转发。
# 每次实际发送均重新生成UUID；以下为手工占位值。
@baseUrl = https://platform.example.invalid/integration/openapi/v1
@integrationKey = REPLACE_LOCALLY
@requestId = 83c87e30-8aba-4a82-b5d4-9233f52ead48
@taskCode = U8_MATERIAL_REFERENCE

### 连接（不代表U8查询通过）
GET {{baseUrl}}/status
X-Integration-Key: {{integrationKey}}
X-Request-Id: {{requestId}}

### 任务目录
GET {{baseUrl}}/reference/tasks?pageNum=1&pageSize=20
X-Integration-Key: {{integrationKey}}
X-Request-Id: {{requestId}}

### 完整元数据
GET {{baseUrl}}/reference/tasks/{{taskCode}}/metadata
X-Integration-Key: {{integrationKey}}
X-Request-Id: {{requestId}}

### 查询（请使用最新metadataVersion）
POST {{baseUrl}}/reference/tasks/{{taskCode}}/query
X-Integration-Key: {{integrationKey}}
X-Request-Id: {{requestId}}
Content-Type: application/json

'''
(ROOT / 'requests.http').write_text(http + json.dumps(query, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'Generated OpenAPI with {len(paths)} paths, {len(schemas)} schemas, {len(samples)} API examples and 1 binding example.')
