update candidates
set status = 'done'
where status = 'comparing';

alter table candidates drop constraint candidates_status_chk;

alter table candidates add constraint candidates_status_chk check (
    status in ('pending', 'parsing', 'scoring', 'done', 'failed')
);
