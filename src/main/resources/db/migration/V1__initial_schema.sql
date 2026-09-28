-- Initial Arkil schema. All later changes must use a new Flyway migration.

-- Spring Authorization Server JDBC schema
-- Compatible with Spring Boot 4.x / Spring Authorization Server 1.4+

CREATE TABLE IF NOT EXISTS oauth2_registered_client (
    id                            varchar(100)  NOT NULL,
    client_id                     varchar(100)  NOT NULL,
    client_id_issued_at           timestamp     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    client_secret                 varchar(200)  DEFAULT NULL,
    client_secret_expires_at      timestamp     DEFAULT NULL,
    client_name                   varchar(200)  NOT NULL,
    client_authentication_methods varchar(1000) NOT NULL,
    authorization_grant_types     varchar(1000) NOT NULL,
    redirect_uris                 varchar(1000) DEFAULT NULL,
    post_logout_redirect_uris     varchar(1000) DEFAULT NULL,
    scopes                        varchar(1000) NOT NULL,
    client_settings               varchar(2000) NOT NULL,
    token_settings                varchar(2000) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS oauth2_authorization (
    id                            varchar(100)  NOT NULL,
    registered_client_id          varchar(100)  NOT NULL,
    principal_name                varchar(200)  NOT NULL,
    authorization_grant_type      varchar(100)  NOT NULL,
    authorized_scopes             varchar(1000) DEFAULT NULL,
    attributes                    text          DEFAULT NULL,
    state                         varchar(500)  DEFAULT NULL,
    authorization_code_value      text          DEFAULT NULL,
    authorization_code_issued_at  timestamp     DEFAULT NULL,
    authorization_code_expires_at timestamp     DEFAULT NULL,
    authorization_code_metadata   text          DEFAULT NULL,
    access_token_value            text          DEFAULT NULL,
    access_token_issued_at        timestamp     DEFAULT NULL,
    access_token_expires_at       timestamp     DEFAULT NULL,
    access_token_metadata         text          DEFAULT NULL,
    access_token_type             varchar(100)  DEFAULT NULL,
    access_token_scopes           varchar(1000) DEFAULT NULL,
    oidc_id_token_value           text          DEFAULT NULL,
    oidc_id_token_issued_at       timestamp     DEFAULT NULL,
    oidc_id_token_expires_at      timestamp     DEFAULT NULL,
    oidc_id_token_metadata        text          DEFAULT NULL,
    oidc_id_token_claims          text          DEFAULT NULL,
    refresh_token_value           text          DEFAULT NULL,
    refresh_token_issued_at       timestamp     DEFAULT NULL,
    refresh_token_expires_at      timestamp     DEFAULT NULL,
    refresh_token_metadata        text          DEFAULT NULL,
    user_code_value               text          DEFAULT NULL,
    user_code_issued_at           timestamp     DEFAULT NULL,
    user_code_expires_at          timestamp     DEFAULT NULL,
    user_code_metadata            text          DEFAULT NULL,
    device_code_value             text          DEFAULT NULL,
    device_code_issued_at         timestamp     DEFAULT NULL,
    device_code_expires_at        timestamp     DEFAULT NULL,
    device_code_metadata          text          DEFAULT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
    registered_client_id varchar(100)  NOT NULL,
    principal_name       varchar(200)  NOT NULL,
    authorities          varchar(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);

-- Application tables generated from the current JPA model using PostgreSQLDialect.
create table api_keys (secret_key_last4 varchar(4) not null, created_at timestamp(6) with time zone not null, grace_period_ends_at timestamp(6) with time zone, last_used_at timestamp(6) with time zone, revoked_at timestamp(6) with time zone, id uuid not null, project_id uuid not null, key_id varchar(255) not null unique, key_type varchar(255) not null check ((key_type in ('LIVE','TEST'))), name varchar(255), publishable_key varchar(255) not null unique, revocation_reason varchar(255), revoked_by varchar(255), secret_key_hash varchar(255) not null, status varchar(255) not null check ((status in ('ACTIVE','ROTATING','REVOKED','EXPIRED'))), primary key (id));
create table arkil_users (email_verified boolean not null, enabled boolean not null, created_at timestamp(6) with time zone not null, last_login_at timestamp(6) with time zone, updated_at timestamp(6) with time zone, id uuid not null, tenant_id uuid not null, display_name varchar(255), email varchar(255) not null, username varchar(255) not null, primary key (id), constraint uk_user_tenant_username unique (tenant_id, username), constraint uk_user_tenant_email unique (tenant_id, email));
create table audit_logs (timestamp timestamp(6) with time zone not null, id uuid not null, actor_id varchar(255), actor_type varchar(255) check ((actor_type in ('USER','CLIENT','ADMIN','SYSTEM'))), details TEXT, event_type varchar(255) not null check ((event_type in ('AUTH_LOGIN_SUCCESS','AUTH_LOGIN_FAILURE','AUTH_LOGOUT','AUTH_TOKEN_ISSUED','AUTH_TOKEN_REVOKED','MFA_ENROLLED','MFA_VERIFIED','MFA_FAILED','ADMIN_LOGIN','ADMIN_LOGOUT','ADMIN_CREATED','CONFIG_CLIENT_CREATED','CONFIG_CLIENT_UPDATED','CONFIG_CLIENT_DELETED','CONFIG_POLICY_UPDATED','USER_CREATED','USER_UPDATED','USER_DELETED','USER_PASSWORD_CHANGED','USER_BLOCKED','USER_UNBLOCKED','SESSION_CREATED','WEBHOOK_CREATED','WEBHOOK_UPDATED','WEBHOOK_DELETED','WEBHOOK_DELIVERED','WEBHOOK_DELIVERY_FAILED','RATE_LIMIT_EXCEEDED'))), ip_address varchar(255), outcome varchar(255) check ((outcome in ('SUCCESS','FAILURE','BLOCKED'))), target_id varchar(255), user_agent varchar(255), primary key (id));
create table client_allowed_origins (policy_id uuid not null, origin varchar(255));
create table client_auth_policies (created_at timestamp(6) with time zone not null, updated_at timestamp(6) with time zone, version bigint, id uuid not null, client_id varchar(255) not null unique, mfa_policy TEXT, module_config TEXT, registered_client_internal_id varchar(255) not null unique, theme_config TEXT, updated_by varchar(255), primary key (id));
create table client_enabled_modules (policy_id uuid not null, module varchar(255) check ((module in ('EMAIL_PASSWORD','OAUTH2_GOOGLE','OAUTH2_GITHUB','OAUTH2_APPLE','OAUTH2_LINKEDIN','OAUTH2_CUSTOM_OIDC','MAGIC_LINK','PASSKEY','TOTP'))));
create table client_redirect_uris (policy_id uuid not null, redirect_uri varchar(255));
create table email_tokens (created_at timestamp(6) with time zone not null, expires_at timestamp(6) with time zone not null, used_at timestamp(6) with time zone, id uuid not null, user_id uuid not null, token varchar(64) not null unique, email varchar(255) not null, type varchar(255) not null check ((type in ('EMAIL_VERIFICATION','PASSWORD_RESET','MAGIC_LINK'))), primary key (id));
create table passkey_credentials (discoverable boolean, public_key_algorithm integer, user_verification_capable boolean, created_at timestamp(6) with time zone not null, last_used_at timestamp(6) with time zone, sign_count bigint not null, id uuid not null, user_id uuid not null, credential_id varchar(1024) not null, public_key varchar(2048) not null, aaguid varchar(255), label varchar(255), rp_id varchar(255) not null, primary key (id), constraint uk_passkey_credential_id unique (credential_id));
create table password_credentials (created_at timestamp(6) with time zone not null, updated_at timestamp(6) with time zone, id uuid not null, user_id uuid not null unique, algorithm varchar(255) not null, password_hash varchar(255) not null, primary key (id));
create table platform_admins (enabled boolean not null, created_at timestamp(6) with time zone not null, last_login_at timestamp(6) with time zone, id uuid not null, email varchar(255) not null, password_hash varchar(255) not null, username varchar(255) not null unique, primary key (id));
create table project_allowed_origins (project_id uuid not null, origin varchar(255));
create table project_oauth_providers (enabled boolean not null, created_at timestamp(6) with time zone not null, updated_at timestamp(6) with time zone, id uuid not null, project_id uuid not null, provider varchar(50) not null, display_name varchar(100), user_name_attribute varchar(100), scopes varchar(500), authorization_uri varchar(1000), issuer_uri varchar(1000), jwk_set_uri varchar(1000), token_uri varchar(1000), user_info_uri varchar(1000), client_id varchar(255) not null, client_secret_encrypted varchar(255) not null, environment varchar(255) not null check ((environment in ('DEVELOPMENT','STAGING','PRODUCTION'))), primary key (id), constraint uk_project_provider_env unique (project_id, provider, environment));
create table project_redirect_uris (project_id uuid not null, redirect_uri varchar(255));
create table projects (active boolean not null, created_at timestamp(6) with time zone not null, deleted_at timestamp(6) with time zone, updated_at timestamp(6) with time zone, id uuid not null, owner_id uuid not null, tenant_id uuid, description varchar(500), environment varchar(255) not null check ((environment in ('DEVELOPMENT','STAGING','PRODUCTION'))), icon_url varchar(255), name varchar(255) not null, registered_client_id varchar(255), slug varchar(255) not null unique, primary key (id));
create table refresh_tokens (created_at timestamp(6) with time zone not null, expires_at timestamp(6) with time zone not null, revoked_at timestamp(6) with time zone, used_at timestamp(6) with time zone, family_id uuid not null, id uuid not null, user_id uuid not null, token_hash varchar(64) not null unique, client_id varchar(255) not null, ip_address varchar(255), revocation_reason varchar(255) check ((revocation_reason in ('LOGOUT','ROTATION','REUSE_DETECTED','PASSWORD_CHANGE','ADMIN_ACTION','EXPIRED'))), user_agent varchar(255), primary key (id));
create table roles (id uuid not null, description varchar(255), name varchar(255) not null unique, primary key (id));
create table social_identities (created_at timestamp(6) with time zone not null, last_used_at timestamp(6) with time zone, id uuid not null, user_id uuid not null, picture_url varchar(255), provider varchar(255) not null, provider_display_name varchar(255), provider_email varchar(255), provider_subject_id varchar(255) not null, primary key (id), constraint uk_social_provider_subject unique (provider, provider_subject_id));
create table tenants (enabled boolean not null, created_at timestamp(6) with time zone not null, updated_at timestamp(6) with time zone, id uuid not null, name varchar(255) not null, slug varchar(255) not null unique, primary key (id));
create table totp_credentials (digits integer not null, enabled boolean not null, period integer not null, confirmed_at timestamp(6) with time zone, created_at timestamp(6) with time zone not null, last_used_at timestamp(6) with time zone, id uuid not null, user_id uuid not null unique, algorithm varchar(255) not null, backup_codes_hash TEXT, secret_encrypted varchar(255) not null, primary key (id));
create table user_roles (role_id uuid not null, user_id uuid not null, primary key (role_id, user_id));
create table webhooks (enabled boolean not null, created_at timestamp(6) with time zone not null, updated_at timestamp(6) with time zone, id uuid not null, project_id uuid not null, secret varchar(512) not null, events varchar(1024) not null, url varchar(2048) not null, description varchar(255), primary key (id));
create index idx_apikey_project on api_keys (project_id);
create index idx_apikey_key_id on api_keys (key_id);
create index idx_apikey_pk on api_keys (publishable_key);
create index idx_audit_event_type on audit_logs (event_type);
create index idx_audit_timestamp on audit_logs (timestamp);
create index idx_audit_actor on audit_logs (actor_id);
create index idx_policy_client_id on client_auth_policies (client_id);
create index idx_email_token on email_tokens (token);
create index idx_email_token_user on email_tokens (user_id);
create index idx_passkey_user_id on passkey_credentials (user_id);
create index idx_oauth_provider_project on project_oauth_providers (project_id);
create index idx_project_slug on projects (slug);
create index idx_project_owner on projects (owner_id);
create index idx_project_tenant on projects (tenant_id);
create index idx_refresh_token on refresh_tokens (token_hash);
create index idx_refresh_token_family on refresh_tokens (family_id);
create index idx_refresh_token_user on refresh_tokens (user_id);
create index idx_social_provider_subject on social_identities (provider, provider_subject_id);
create index idx_webhook_project on webhooks (project_id);
create index idx_webhook_enabled on webhooks (project_id, enabled);
alter table if exists arkil_users add constraint FKi090tqn8dcy9scwwrf19wtcpd foreign key (tenant_id) references tenants;
alter table if exists client_allowed_origins add constraint FK260skoa8wjs4p96iq0q4ru2h1 foreign key (policy_id) references client_auth_policies;
alter table if exists client_enabled_modules add constraint FKta54qlm68lhvf8ukmo3twiq3x foreign key (policy_id) references client_auth_policies;
alter table if exists client_redirect_uris add constraint FKcfhfaofvsila631l153k36xp4 foreign key (policy_id) references client_auth_policies;
alter table if exists passkey_credentials add constraint FKanxbpt2gyjg8b4hbkgo9tbuk7 foreign key (user_id) references arkil_users;
alter table if exists password_credentials add constraint FKhrqxq6rb8emmego3b6uge86n8 foreign key (user_id) references arkil_users;
alter table if exists project_allowed_origins add constraint FKlfb7d3s04dlhywadvorac2stk foreign key (project_id) references projects;
alter table if exists project_redirect_uris add constraint FKrokracwgicunaqv9dk0hrro6l foreign key (project_id) references projects;
alter table if exists social_identities add constraint FKshh5ovkdmsokfhcda6yed78rk foreign key (user_id) references arkil_users;
alter table if exists totp_credentials add constraint FKco0tk1ry0t1ayiw190450uilq foreign key (user_id) references arkil_users;
alter table if exists user_roles add constraint FKh8ciramu9cc9q3qcqiv4ue8a6 foreign key (role_id) references roles;
alter table if exists user_roles add constraint FK4f253wjjovmph4jceko3y96k3 foreign key (user_id) references arkil_users;
