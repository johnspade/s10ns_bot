create table exchange_rates
(
	currency varchar(3) not null
		constraint exchange_rates_pk
			primary key,
	rate numeric not null
);

create table users
(
	id bigint not null
		constraint users_pkey
			primary key,
	chat_id bigint,
	first_name varchar(255) not null,
	default_currency varchar(3) not null,
	dialog jsonb
);

create table subscriptions
(
	id bigserial not null
		constraint subscriptions_key
			primary key,
	name varchar(255) not null,
	amount bigint not null,
	currency varchar(3) not null,
	description varchar(2048),
	one_time boolean not null,
	period_duration integer,
	period_unit varchar(6),
	first_payment_date date,
	user_id bigint
		constraint subscriptions_users_id_fk
			references users
);

create table exchange_rates_refresh_timestamp
(
	id boolean default true not null
		constraint exchange_rates_refresh_timestamp_pkey
			primary key
		constraint onerow_uni
			check (id),
	refresh_timestamp timestamp not null
);
