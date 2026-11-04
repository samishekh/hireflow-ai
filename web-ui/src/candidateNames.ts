type NamedCandidate = {
  parsed_data?: { candidate_name?: string | null } | null
}

export async function lookupCandidateNames(
  ids: readonly string[],
  asked: Set<string>,
  fetchCandidate: (id: string) => Promise<NamedCandidate>,
): Promise<Array<readonly [string, string]>> {
  const pending = ids.filter((id) => !asked.has(id))
  if (pending.length === 0) return []
  for (const id of pending) asked.add(id)
  const found: Array<readonly [string, string]> = []
  await Promise.all(
    pending.map(async (id) => {
      try {
        const candidate = await fetchCandidate(id)
        const name = candidate.parsed_data?.candidate_name?.trim() ?? ''
        if (name) found.push([id, name])
      } catch {
        asked.delete(id)
      }
    }),
  )
  return found
}
