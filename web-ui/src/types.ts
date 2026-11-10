export type CandidateStatus =
  | 'pending'
  | 'parsing'
  | 'scoring'
  | 'done'
  | 'failed'

export type OverrideDecision = 'accepted' | 'overridden'

export type Formula = {
  skills_weight: number
  experience_weight: number
  education_weight: number
  formula_version: string
  updated_at: string
  rescored_candidates: number
}

export type Score = {
  skills_score: number
  experience_score: number
  education_score: number
  overall_score: number
  matched_skills: string[]
  missing_skills: string[]
  strengths: string[]
  concerns: string[]
  summary: string
  formula_version: string
  model: string
}

export type Override = {
  id: string
  candidate_id: string
  decision: OverrideDecision
  note: string
  decided_by: string
  created_at: string
}

export type Ranking = {
  id: string
  original_filename: string
  status: CandidateStatus
  error_message: string | null
  score: Score | null
  override: Override | null
}

export type Job = {
  id: string
  title: string
  description: string
  created_at: string
}

export type WorkExperience = {
  company: string
  role: string
  duration: string
  description: string
}

export type Education = {
  institution: string
  degree: string
  field_of_study: string
  duration: string
}

export type ParsedResume = {
  candidate_name: string
  email: string
  phone: string
  skills: string[]
  years_of_experience: number
  work_history: WorkExperience[]
  education: Education[]
}

export type Candidate = {
  id: string
  job_id: string
  original_filename: string
  status: CandidateStatus
  error_message: string | null
  raw_text: string | null
  parsed_data: ParsedResume | null
  score: Score | null
}
