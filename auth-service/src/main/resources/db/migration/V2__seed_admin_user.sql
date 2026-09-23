-- DEV SEED ONLY. Product mutations require role ADMIN, and self-registration always
-- assigns USER, so a demo/local environment needs at least one seeded admin to exercise
-- those endpoints. The password below is a fixed development value — it MUST be rotated
-- (or this row removed) before any real deployment.
--
-- Credentials: admin@orderhub.dev / Admin@123
-- Hash is BCrypt, cost factor 10: $2a$10$7WAq/dnlChNR11FT70H13eaP8GOPXiMnfDqF9TVcjm3g7eNHiEQRO
INSERT INTO users (id, email, password, first_name, last_name, role)
VALUES (
    gen_random_uuid(),
    'admin@orderhub.dev',
    '$2a$10$7WAq/dnlChNR11FT70H13eaP8GOPXiMnfDqF9TVcjm3g7eNHiEQRO',
    'Admin',
    'Orderhub',
    'ADMIN'
)
ON CONFLICT (email) DO NOTHING;
