alter table subscriptions
    add send_notifications boolean default false not null;

alter table subscriptions
    alter column send_notifications drop default;
