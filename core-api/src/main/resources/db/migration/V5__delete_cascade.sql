alter table candidates drop constraint candidates_job_id_fkey;
alter table candidates
    add constraint candidates_job_id_fkey
    foreign key (job_id) references jobs (id) on delete cascade;

alter table match_results drop constraint match_results_candidate_id_fkey;
alter table match_results
    add constraint match_results_candidate_id_fkey
    foreign key (candidate_id) references candidates (id) on delete cascade;

alter table score_overrides drop constraint score_overrides_candidate_id_fkey;
alter table score_overrides
    add constraint score_overrides_candidate_id_fkey
    foreign key (candidate_id) references candidates (id) on delete cascade;
