alter table notifications
    add constraint subscription_id_unique unique (subscription_id);
