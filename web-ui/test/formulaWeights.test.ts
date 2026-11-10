import assert from 'node:assert/strict'
import { test } from 'node:test'
import { formatWeight, weightDraftError, weightHundredths, weightText } from '../src/formulaWeights.ts'

test('parses weights as hundredths', () => {
  assert.equal(weightHundredths('50'), 5000)
  assert.equal(weightHundredths(' 33.3 '), 3330)
  assert.equal(weightHundredths('33.34'), 3334)
  assert.equal(weightHundredths('0'), 0)
  assert.equal(weightHundredths('100'), 10000)
  assert.equal(weightHundredths('100.00'), 10000)
  assert.equal(weightHundredths('100.01'), null)
  assert.equal(weightHundredths('60.001'), null)
  assert.equal(weightHundredths('-1'), null)
  assert.equal(weightHundredths(''), null)
  assert.equal(weightHundredths('50%'), null)
})

test('default and custom drafts', () => {
  assert.equal(weightDraftError({ skills: '50', experience: '35', education: '15' }), null)
  assert.equal(weightDraftError({ skills: '33.33', experience: '33.33', education: '33.34' }), null)
  assert.equal(
    weightDraftError({ skills: '60', experience: '30', education: '20' }),
    'Weights add up to 110. They must add up to 100.',
  )
  assert.match(
    weightDraftError({ skills: '10', experience: '10', education: '' }) ?? '',
    /at most two decimal places/,
  )
})

test('formats weights without trailing zeros', () => {
  assert.equal(formatWeight(50), '50')
  assert.equal(formatWeight(33.3), '33.3')
  assert.equal(formatWeight(33.34), '33.34')
  assert.equal(weightText(15), '15%')
})
