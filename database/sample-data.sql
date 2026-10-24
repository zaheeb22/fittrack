-- This selects the database used by this script.
USE defaultdb;
-- This starts a transaction so the sample records can be saved together.
START TRANSACTION;

-- This adds made-up members only when their IDs and email addresses are not already used.
INSERT INTO members (id, name, email, age)
SELECT 910101, 'Jamie Taylor', 'jamie.demo@example.com', 24
WHERE NOT EXISTS (SELECT 1 FROM members WHERE id = 910101 OR email = 'jamie.demo@example.com');

INSERT INTO members (id, name, email, age)
SELECT 910102, 'Sam Morgan', 'sam.demo@example.com', 31
WHERE NOT EXISTS (SELECT 1 FROM members WHERE id = 910102 OR email = 'sam.demo@example.com');

INSERT INTO members (id, name, email, age)
SELECT 910103, 'Casey Patel', 'casey.demo@example.com', 28
WHERE NOT EXISTS (SELECT 1 FROM members WHERE id = 910103 OR email = 'casey.demo@example.com');

INSERT INTO members (id, name, email, age)
SELECT 910104, 'Robin Lewis', 'robin.demo@example.com', 45
WHERE NOT EXISTS (SELECT 1 FROM members WHERE id = 910104 OR email = 'robin.demo@example.com');

-- This adds workouts for the sample members. Existing workout IDs are skipped.
INSERT INTO workouts (id, member_id, activity_type, duration_minutes, workout_date)
SELECT 920101, id, 'Running', 30, '2026-10-05' FROM members
WHERE id = 910101 AND email = 'jamie.demo@example.com'
AND NOT EXISTS (SELECT 1 FROM workouts WHERE id = 920101);

INSERT INTO workouts (id, member_id, activity_type, duration_minutes, workout_date)
SELECT 920102, id, 'Cycling', 45, '2026-10-08' FROM members
WHERE id = 910101 AND email = 'jamie.demo@example.com'
AND NOT EXISTS (SELECT 1 FROM workouts WHERE id = 920102);

INSERT INTO workouts (id, member_id, activity_type, duration_minutes, workout_date)
SELECT 920103, id, 'Walking', 25, '2026-10-06' FROM members
WHERE id = 910102 AND email = 'sam.demo@example.com'
AND NOT EXISTS (SELECT 1 FROM workouts WHERE id = 920103);

INSERT INTO workouts (id, member_id, activity_type, duration_minutes, workout_date)
SELECT 920104, id, 'Swimming', 35, '2026-10-10' FROM members
WHERE id = 910102 AND email = 'sam.demo@example.com'
AND NOT EXISTS (SELECT 1 FROM workouts WHERE id = 920104);

INSERT INTO workouts (id, member_id, activity_type, duration_minutes, workout_date)
SELECT 920105, id, 'Running', 40, '2026-10-07' FROM members
WHERE id = 910103 AND email = 'casey.demo@example.com'
AND NOT EXISTS (SELECT 1 FROM workouts WHERE id = 920105);

INSERT INTO workouts (id, member_id, activity_type, duration_minutes, workout_date)
SELECT 920106, id, 'Strength training', 30, '2026-10-11' FROM members
WHERE id = 910103 AND email = 'casey.demo@example.com'
AND NOT EXISTS (SELECT 1 FROM workouts WHERE id = 920106);

INSERT INTO workouts (id, member_id, activity_type, duration_minutes, workout_date)
SELECT 920107, id, 'Walking', 20, '2026-10-09' FROM members
WHERE id = 910104 AND email = 'robin.demo@example.com'
AND NOT EXISTS (SELECT 1 FROM workouts WHERE id = 920107);

INSERT INTO workouts (id, member_id, activity_type, duration_minutes, workout_date)
SELECT 920108, id, 'Yoga', 30, '2026-10-12' FROM members
WHERE id = 910104 AND email = 'robin.demo@example.com'
AND NOT EXISTS (SELECT 1 FROM workouts WHERE id = 920108);

-- This adds sample goals. Existing challenge IDs are skipped.
INSERT INTO challenges (id, member_id, name, type, target, start_date, end_date)
SELECT 930101, id, 'October exercise minutes', 'MINUTES', 150, '2026-10-01', '2026-10-31' FROM members
WHERE id = 910101 AND email = 'jamie.demo@example.com'
AND NOT EXISTS (SELECT 1 FROM challenges WHERE id = 930101);

INSERT INTO challenges (id, member_id, name, type, target, start_date, end_date)
SELECT 930102, id, 'Complete two workouts', 'SESSIONS', 2, '2026-10-01', '2026-10-31' FROM members
WHERE id = 910102 AND email = 'sam.demo@example.com'
AND NOT EXISTS (SELECT 1 FROM challenges WHERE id = 930102);

INSERT INTO challenges (id, member_id, name, type, target, start_date, end_date)
SELECT 930103, id, 'October training sessions', 'SESSIONS', 5, '2026-10-01', '2026-10-31' FROM members
WHERE id = 910103 AND email = 'casey.demo@example.com'
AND NOT EXISTS (SELECT 1 FROM challenges WHERE id = 930103);

INSERT INTO challenges (id, member_id, name, type, target, start_date, end_date)
SELECT 930104, id, 'Reach fifty exercise minutes', 'MINUTES', 50, '2026-10-01', '2026-10-31' FROM members
WHERE id = 910104 AND email = 'robin.demo@example.com'
AND NOT EXISTS (SELECT 1 FROM challenges WHERE id = 930104);

-- This saves the sample records without replacing existing records.
COMMIT;

-- This displays all three record lists after the sample data is saved.
SELECT * FROM members ORDER BY id;
SELECT * FROM workouts ORDER BY id;
SELECT * FROM challenges ORDER BY id;
