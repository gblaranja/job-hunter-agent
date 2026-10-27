import assert from 'node:assert/strict'
import { test } from 'node:test'
import { jobCommand, jsonLdArray, originPattern, previewRequest, problemMessage } from '../payload.js'

test('junta os blocos JSON-LD válidos num array', () => {
  assert.equal(jsonLdArray(['{"@type":"JobPosting"}', 'não é json', '{"@graph":[]}']), '[{"@type":"JobPosting"},{"@graph":[]}]')
  assert.equal(jsonLdArray([]), null)
  // Gupy: o JSON-LD vem com as aspas escapadas como entidades HTML.
  assert.equal(jsonLdArray(['{&quot;@type&quot;:&quot;JobPosting&quot;,&quot;title&quot;:&quot;P&amp;D&quot;}']), '[{"@type":"JobPosting","title":"P&D"}]')
})

test('o preview leva o link, o JSON-LD e a seleção aparada', () => {
  assert.deepEqual(previewRequest({ url: 'https://x.com/v/1', jsonLdBlocks: [], selection: '  Dev Java  ' }), {
    url: 'https://x.com/v/1',
    jsonLd: null,
    text: 'Dev Java',
  })
  assert.equal(previewRequest({ url: 'u', jsonLdBlocks: [], selection: 'a'.repeat(60_000) }).text.length, 50_000)
})

test('a vaga salva usa os campos do ManualJobCommand', () => {
  const command = jobCommand({ title: ' Dev ', company: 'Acme', url: 'https://x', method: 'JSON_LD', warnings: [] })
  assert.deepEqual(Object.keys(command), ['title', 'company', 'location', 'workModel', 'scope', 'salaryRange', 'url', 'description'])
  assert.equal(command.title, 'Dev')
})

test('permissão só para a origem da API e mensagens de erro', () => {
  assert.equal(originPattern('http://localhost:8080/api'), 'http://localhost:8080/*')
  assert.match(problemMessage(401, null), /Token recusado/)
  assert.equal(problemMessage(400, { detail: 'Link do LinkedIn' }), 'Link do LinkedIn')
})
