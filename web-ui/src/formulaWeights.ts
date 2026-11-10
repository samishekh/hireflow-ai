export type WeightDraft = {
  skills: string
  experience: string
  education: string
}

export function weightHundredths(raw: string): number | null {
  const text = raw.trim()
  if (!/^\d{1,3}(\.\d{1,2})?$/.test(text)) return null
  const [whole, frac = ''] = text.split('.')
  const hundredths = Number(whole) * 100 + Number((frac + '00').slice(0, 2))
  if (hundredths > 10000) return null
  return hundredths
}

export function formatHundredths(hundredths: number): string {
  const whole = Math.trunc(hundredths / 100)
  const frac = Math.abs(hundredths % 100)
  if (frac === 0) return String(whole)
  if (frac % 10 === 0) return `${whole}.${frac / 10}`
  return `${whole}.${String(frac).padStart(2, '0')}`
}

export function formatWeight(value: number): string {
  return formatHundredths(Math.round(value * 100))
}

export function weightText(value: number): string {
  return `${formatWeight(value)}%`
}

export function weightDraftError(draft: WeightDraft): string | null {
  const skills = weightHundredths(draft.skills)
  const experience = weightHundredths(draft.experience)
  const education = weightHundredths(draft.education)
  if (skills == null || experience == null || education == null) {
    return 'Enter each weight from 0 to 100, with at most two decimal places.'
  }
  const total = skills + experience + education
  if (total !== 10000) {
    return `Weights add up to ${formatHundredths(total)}. They must add up to 100.`
  }
  return null
}
