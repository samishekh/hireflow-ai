import type { JobDocument as JobDocumentModel } from '../jobText'
import { parseDescription } from '../jobText'

export function JobDocument({ description }: { description: string }) {
  const doc = parseDescription(description)
  if (!doc.hasContent) {
    return <p className="muted">The posting preview fills in as you complete each section.</p>
  }
  if (!doc.structured) {
    return (
      <article className="doc">
        <p className="muted">This posting is stored as plain text, without the usual sections.</p>
        <p className="prose">{doc.raw}</p>
      </article>
    )
  }
  return <Structured doc={doc} />
}

function Structured({ doc }: { doc: JobDocumentModel }) {
  return (
    <article className="doc">
      <div className="meta-row">
        {doc.location ? <span className="pill">{doc.location}</span> : null}
        {doc.employmentType ? <span className="pill">{doc.employmentType}</span> : null}
        {doc.seniority ? <span className="pill">{doc.seniority}</span> : null}
        {doc.team ? <span className="pill">{doc.team}</span> : null}
      </div>
      {doc.about ? (
        <section>
          <h3>About the role</h3>
          <p className="prose">{doc.about}</p>
        </section>
      ) : null}
      <BulletList title="Responsibilities" items={doc.responsibilities} />
      <BulletList title="Requirements" items={doc.requirements} />
      <BulletList title="Nice to have" items={doc.niceToHave} />
    </article>
  )
}

function BulletList({ title, items }: { title: string; items: string[] }) {
  if (items.length === 0) return null
  return (
    <section>
      <h3>{title}</h3>
      <ul>
        {items.map((item, index) => (
          <li key={`${item}-${index}`}>{item}</li>
        ))}
      </ul>
    </section>
  )
}
