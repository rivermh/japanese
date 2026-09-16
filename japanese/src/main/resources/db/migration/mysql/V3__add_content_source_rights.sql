alter table content_sources
    add column rights_status varchar(32) not null default 'UNKNOWN',
    add column rights_reviewed_at datetime(6) null,
    add column rights_review_note varchar(2000) null,
    add column attribution_required bit not null default b'0';
