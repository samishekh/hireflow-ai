import assert from 'node:assert/strict'
import { test } from 'node:test'
import { isPdf } from '../src/pdfFile.ts'

test('accepts PDF filenames with browser binary MIME types', () => {
  const accepted = [
    ['resume.pdf', 'application/pdf'],
    ['resume.PDF', 'Application/PDF'],
    ['resume.pdf', ''],
    ['resume.pdf', 'application/octet-stream'],
    ['resume.pdf', 'application/octet-stream; charset=binary'],
    ['resume.pdf', 'binary/octet-stream'],
    ['resume.pdf', 'application/x-pdf'],
    ['resume.pdf', 'application/vnd.pdf'],
    ['resume.pdf', 'text/pdf'],
  ] as const
  for (const [name, type] of accepted) {
    assert.equal(isPdf({ name, type }), true, `${name} (${type || 'empty'})`)
  }
})

test('rejects non-PDF names and unrelated MIME types', () => {
  const rejected = [
    ['notes.txt', 'application/pdf'],
    ['photo.png', 'application/octet-stream'],
    ['resume.pdf', 'image/png'],
    ['resume.pdf.exe', 'application/octet-stream'],
  ] as const
  for (const [name, type] of rejected) {
    assert.equal(isPdf({ name, type }), false, `${name} (${type})`)
  }
})
