export const EMPLOYMENT_TYPES = ['Full-time', 'Part-time', 'Contract', 'Internship'] as const
export const SENIORITY_LEVELS = ['Junior', 'Mid-level', 'Senior', 'Staff', 'Lead'] as const

export type JobDraft = {
  title: string
  location: string
  employmentType: string
  seniority: string
  team: string
  about: string
  responsibilities: string
  requirements: string
  niceToHave: string
}

export type JobDocument = {
  structured: boolean
  hasContent: boolean
  location?: string
  employmentType?: string
  seniority?: string
  team?: string
  about?: string
  responsibilities: string[]
  requirements: string[]
  niceToHave: string[]
  raw: string
}

const SECTION_ORDER: { heading: string; key: keyof Pick<JobDraft, 'about' | 'responsibilities' | 'requirements' | 'niceToHave'> }[] = [
  { heading: 'About the role', key: 'about' },
  { heading: 'Responsibilities', key: 'responsibilities' },
  { heading: 'Requirements', key: 'requirements' },
  { heading: 'Nice to have', key: 'niceToHave' },
]

export function emptyDraft(): JobDraft {
  return {
    title: '',
    location: '',
    employmentType: '',
    seniority: '',
    team: '',
    about: '',
    responsibilities: '',
    requirements: '',
    niceToHave: '',
  }
}

export function linesOf(text: string): string[] {
  return text
    .split('\n')
    .map((line) => line.trim().replace(/^[-*•]\s*/, ''))
    .filter((line) => line.length > 0)
}

export function composeDescription(draft: JobDraft): string {
  const blocks: Record<string, string> = {
    about: draft.about.trim(),
    responsibilities: linesOf(draft.responsibilities).map((line) => `- ${line}`).join('\n'),
    requirements: linesOf(draft.requirements).map((line) => `- ${line}`).join('\n'),
    niceToHave: linesOf(draft.niceToHave).map((line) => `- ${line}`).join('\n'),
  }
  const lines = [
    `Location: ${draft.location.trim()}`,
    `Employment: ${draft.employmentType.trim()}`,
    `Seniority: ${draft.seniority.trim()}`,
    `Team: ${draft.team.trim()}`,
    '',
  ]
  for (const section of SECTION_ORDER) {
    lines.push(`## ${section.heading}`, blocks[section.key], '')
  }
  return lines.join('\n').trim()
}

function listFrom(section: string | undefined): string[] {
  if (!section) return []
  return section
    .split('\n')
    .map((line) => line.trim().replace(/^[-*•]\s*/, ''))
    .filter((line) => line.length > 0)
}

export function parseDescription(description: string): JobDocument {
  const raw = description ?? ''
  const meta: Record<string, string> = {}
  const sections: Record<string, string> = {}
  let current: string | null = null
  const buffer: string[] = []
  const flush = () => {
    if (current) sections[current] = buffer.join('\n').trim()
    buffer.length = 0
  }
  for (const line of raw.split('\n')) {
    const heading = /^##\s+(.+)$/.exec(line.trim())
    if (heading) {
      flush()
      current = heading[1].trim().toLowerCase()
      continue
    }
    if (!current) {
      const match = /^(Location|Employment|Seniority|Team):\s*(.*)$/.exec(line.trim())
      if (match && match[2].trim()) meta[match[1].toLowerCase()] = match[2].trim()
      continue
    }
    buffer.push(line)
  }
  flush()
  const about = sections['about the role']?.trim()
  const responsibilities = listFrom(sections.responsibilities)
  const requirements = listFrom(sections.requirements)
  const niceToHave = listFrom(sections['nice to have'])
  const structured = Boolean(about || responsibilities.length || requirements.length)
  const hasContent = Boolean(
    meta.location || meta.employment || meta.seniority || meta.team || about
    || responsibilities.length || requirements.length || niceToHave.length,
  )
  return {
    structured,
    hasContent,
    location: meta.location,
    employmentType: meta.employment,
    seniority: meta.seniority,
    team: meta.team,
    about,
    responsibilities,
    requirements,
    niceToHave,
    raw,
  }
}

export function excerpt(description: string): string {
  const doc = parseDescription(description)
  const source = (doc.about || doc.raw).replace(/\s+/g, ' ').trim()
  if (source.length <= 160) return source
  return `${source.slice(0, 157)}…`
}

export function validateDraft(draft: JobDraft): Record<string, string> {
  const errors: Record<string, string> = {}
  const title = draft.title.trim()
  if (!title) errors.title = 'Add a job title.'
  else if (title.length > 200) errors.title = 'Keep the title under 200 characters.'
  if (!draft.location.trim()) errors.location = 'Add a location, even if it is remote.'
  else if (draft.location.trim().length > 120) errors.location = 'Keep the location under 120 characters.'
  if (!EMPLOYMENT_TYPES.includes(draft.employmentType as (typeof EMPLOYMENT_TYPES)[number])) {
    errors.employmentType = 'Choose full-time, part-time, contract, or internship.'
  }
  if (!SENIORITY_LEVELS.includes(draft.seniority as (typeof SENIORITY_LEVELS)[number])) {
    errors.seniority = 'Choose a seniority level.'
  }
  if (!draft.team.trim()) errors.team = 'Name the team that owns this role.'
  else if (draft.team.trim().length > 120) errors.team = 'Keep the team name under 120 characters.'
  if (draft.about.trim().length < 80) {
    errors.about = 'Write a real overview. A sentence is not enough for scoring.'
  }
  if (linesOf(draft.responsibilities).length < 3) {
    errors.responsibilities = 'Add at least three responsibilities, one per line.'
  }
  if (linesOf(draft.requirements).length < 3) {
    errors.requirements = 'Add at least three requirements, one per line.'
  }
  if (linesOf(draft.niceToHave).length < 1) errors.niceToHave = 'Add at least one nice-to-have.'
  if (!errors.description && composeDescription(draft).length > 20000) {
    errors.description = 'This posting is over the 20,000 character limit. Shorten a section.'
  }
  return errors
}

export const EXAMPLES: { id: string; label: string; draft: JobDraft }[] = [
  {
    id: 'backend',
    label: 'Senior backend example',
    draft: {
      title: 'Senior Backend Engineer',
      location: 'Berlin, or remote within the EU',
      employmentType: 'Full-time',
      seniority: 'Senior',
      team: 'Logistics Platform',
      about: 'Northwind Logistics is rebuilding the systems that move freight from the warehouse to the last mile. The platform team owns the APIs that carriers, warehouses, and the internal control tower use every day. We are hiring a senior backend engineer to design services that stay correct under burst traffic, make shipment state easy to trace, and leave the next person a codebase they can change without fear. You will sit with product and on-call engineers and own production behavior, not only the pull request.',
      responsibilities: `Lead the design of shipment and tracking APIs used by carriers and the control tower
Break large workflows into services with clear ownership, timeouts, and failure modes
Review production incidents, write the fix, and follow it with a test that would have caught it
Mentor other backend engineers through design reviews and on-call shadowing
Partner with data engineering on the events the forecasting models consume
Keep the public API contract stable for external carrier integrations`,
      requirements: `5 or more years building backend services in production, with Java or Python as a primary language
Experience designing APIs and data models that other teams depend on
Comfort debugging latency, retries, and partial failure across more than one service
Clear writing: you can explain a tradeoff to an engineer and to a dispatcher
Evidence of taking a system from a prototype to something on-call can trust
Legal right to work in the EU`,
      niceToHave: `Experience with PostgreSQL query plans and schema migrations
Familiarity with Kafka or another log used as a source of truth
Previous work in logistics, maps, or other physical-world operations`,
    },
  },
  {
    id: 'data',
    label: 'Data engineer example',
    draft: {
      title: 'Senior Data Engineer',
      location: 'Amsterdam, hybrid three days on site',
      employmentType: 'Full-time',
      seniority: 'Senior',
      team: 'Forecasting',
      about: 'The forecasting team estimates volume, dwell time, and missed connections for every lane Northwind runs. Those numbers decide staffing in the warehouses and which carrier gets the next tender. We need a senior data engineer who can turn messy scan events into tables people trust, and who will say no when a metric is not ready to drive an operation. The work is close to the business: a wrong forecast on a Friday shows up as overtime on Monday.',
      responsibilities: `Own the pipelines from carrier scan events to the warehouse staffing forecast
Define data contracts with the platform team and reject events that break them
Build the late-arriving and duplicate-scan handling that operations actually hit
Publish data-quality checks that page the team before a planner uses a bad number
Document every metric a non-engineer sees, including what it excludes
Pair with the forecasting scientists on the features they train on`,
      requirements: `5 or more years building production data pipelines, not only notebooks
Strong SQL and a typed language you use for transformations, such as Python or Scala
Experience with a warehouse or lakehouse and with orchestrating backfills safely
Judgment about freshness, cost, and correctness when they conflict
Ability to sit with a warehouse planner and leave with a sharper definition`,
      niceToHave: `dbt, Spark, or an equivalent transformation layer in production
Work with streaming ingestion, even if the published model is a batch table
Domain experience in logistics, retail operations, or capacity planning`,
    },
  },
]
