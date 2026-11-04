import assert from 'node:assert/strict'
import { test } from 'node:test'
import { lookupCandidateNames } from '../src/candidateNames.ts'

test('a failed name lookup is retried on the next pass', async () => {
  const asked = new Set<string>()
  let attempts = 0
  const fetchCandidate = async () => {
    attempts += 1
    if (attempts === 1) throw new Error('upstream 503')
    return { parsed_data: { candidate_name: '  Ada Lovelace  ' } }
  }

  assert.deepEqual(await lookupCandidateNames(['cand-1'], asked, fetchCandidate), [])
  assert.equal(asked.has('cand-1'), false)

  assert.deepEqual(await lookupCandidateNames(['cand-1'], asked, fetchCandidate), [['cand-1', 'Ada Lovelace']])
  assert.equal(asked.has('cand-1'), true)
  assert.deepEqual(await lookupCandidateNames(['cand-1'], asked, fetchCandidate), [])
  assert.equal(attempts, 2)
})

test('an in-flight lookup is not started twice', async () => {
  const asked = new Set<string>()
  let calls = 0
  let release: (name: string) => void = () => {}
  const gate = new Promise<string>((resolve) => {
    release = resolve
  })
  const fetchCandidate = async () => {
    calls += 1
    return { parsed_data: { candidate_name: await gate } }
  }

  const first = lookupCandidateNames(['cand-1'], asked, fetchCandidate)
  const second = lookupCandidateNames(['cand-1'], asked, fetchCandidate)
  assert.equal(calls, 1)
  release('Ada Lovelace')

  assert.deepEqual(await first, [['cand-1', 'Ada Lovelace']])
  assert.deepEqual(await second, [])
  assert.equal(calls, 1)
})

test('a successful lookup with no name is not retried', async () => {
  const asked = new Set<string>()
  let calls = 0
  const fetchCandidate = async () => {
    calls += 1
    return { parsed_data: { candidate_name: '   ' } }
  }

  assert.deepEqual(await lookupCandidateNames(['cand-1'], asked, fetchCandidate), [])
  assert.deepEqual(await lookupCandidateNames(['cand-1'], asked, fetchCandidate), [])
  assert.equal(calls, 1)
})

test('one failed lookup does not block the rest of the batch', async () => {
  const asked = new Set<string>()
  const fetchCandidate = async (id: string) => {
    if (id === 'bad') throw new Error('network')
    return { parsed_data: { candidate_name: id === 'ok' ? 'Grace Hopper' : '' } }
  }

  const found = await lookupCandidateNames(['bad', 'ok'], asked, fetchCandidate)
  assert.deepEqual(found, [['ok', 'Grace Hopper']])
  assert.equal(asked.has('bad'), false)
  assert.equal(asked.has('ok'), true)
})
