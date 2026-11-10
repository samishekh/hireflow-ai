create table scoring_formula (
    id smallint primary key,
    skills_weight numeric(5, 2) not null,
    experience_weight numeric(5, 2) not null,
    education_weight numeric(5, 2) not null,
    formula_version varchar(32) not null,
    updated_at timestamptz not null,
    constraint scoring_formula_singleton_chk check (id = 1),
    constraint scoring_formula_range_chk check (
        skills_weight >= 0 and skills_weight <= 100
        and experience_weight >= 0 and experience_weight <= 100
        and education_weight >= 0 and education_weight <= 100
    ),
    constraint scoring_formula_sum_chk check (
        skills_weight + experience_weight + education_weight = 100
    )
);

insert into scoring_formula (
    id,
    skills_weight,
    experience_weight,
    education_weight,
    formula_version,
    updated_at
) values (
    1,
    50.00,
    35.00,
    15.00,
    'v1',
    now()
);
