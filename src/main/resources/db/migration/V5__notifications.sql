create table notifications
(
    id uuid not null
        constraint notifications_pkey
            primary key,
    notification_timestamp timestamp not null,
    retries_remaining integer not null,
    subscription_id bigint
        constraint notifications_subscriptions_id_fk
            references subscriptions
);
