alter table content_sources
    add column rights_status varchar(32) default 'UNKNOWN' not null;

alter table content_sources
    add column rights_reviewed_at timestamp(6) with time zone;

alter table content_sources
    add column rights_review_note varchar(2000);

alter table content_sources
    add column attribution_required boolean default false not null;
