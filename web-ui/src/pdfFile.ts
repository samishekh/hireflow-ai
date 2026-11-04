const GENERIC_BINARY_TYPES = new Set([
  '',
  'application/octet-stream',
  'binary/octet-stream',
  'application/x-download',
  'application/download',
  'application/force-download',
  'application/unknown',
  'application/binary',
])

export function isPdf(file: { name: string; type: string }): boolean {
  if (!file.name.toLowerCase().endsWith('.pdf')) return false
  const type = file.type.toLowerCase().split(';')[0].trim()
  return GENERIC_BINARY_TYPES.has(type) || type.includes('pdf')
}
