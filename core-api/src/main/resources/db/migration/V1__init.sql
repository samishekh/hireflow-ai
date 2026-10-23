create table jobs (
    id uuid primary key,
    title varchar(200) not null,
    description text not null,
    description_hash varchar(64) not null,
    created_at timestamptz not null
);

create table candidates (
    id uuid primary key,
    job_id uuid not null references jobs (id),
    original_filename varchar(512) not null,
    content_hash varchar(64) not null,
    parsed_data jsonb,
    raw_text text,
    status varchar(20) not null,
    error_message text,
    attempt_count integer not null default 0,
    created_at timestamptz not null,
    constraint candidates_status_chk check (
        status in ('pending', 'parsing', 'scoring', 'comparing', 'done', 'failed')
    )
);

create index idx_candidates_job_content on candidates (job_id, content_hash);
create index idx_candidates_content_hash on candidates (content_hash);

create table match_results (
    candidate_id uuid primary key references candidates (id),
    skills_score double precision not null,
    experience_score double precision not null,
    education_score double precision not null,
    overall_score double precision not null,
    matched_skills jsonb not null,
    missing_skills jsonb not null,
    strengths jsonb not null,
    concerns jsonb not null,
    summary text not null,
    formula_version varchar(32) not null,
    comparison_rank integer,
    comparison_reason text,
    model varchar(128) not null,
    cache_key varchar(64) not null,
    created_at timestamptz not null
);

create index idx_match_results_cache_key on match_results (cache_key);

create table score_overrides (
    id uuid primary key,
    candidate_id uuid not null references candidates (id),
    decision varchar(20) not null,
    note text not null,
    decided_by varchar(200) not null,
    created_at timestamptz not null,
    constraint score_overrides_decision_chk check (decision in ('accepted', 'overridden'))
);

create index idx_score_overrides_candidate_created
    on score_overrides (candidate_id, created_at desc);
