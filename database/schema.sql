-- This table stores member details and prevents duplicate email addresses.
CREATE TABLE IF NOT EXISTS members (
 id INT PRIMARY KEY CHECK (id > 0),
 name VARCHAR(200) NOT NULL,
 email VARCHAR(254) NOT NULL UNIQUE,
 age INT NOT NULL CHECK (age > 0)
);
-- This table stores workouts and their dates. A member with linked workouts cannot be deleted.
CREATE TABLE IF NOT EXISTS workouts (
 id INT PRIMARY KEY CHECK (id > 0),
 member_id INT NOT NULL,
 activity_type VARCHAR(200) NOT NULL,
 duration_minutes INT NOT NULL CHECK (duration_minutes > 0),
 workout_date DATE NOT NULL,
 CONSTRAINT fk_workout_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE RESTRICT,
 INDEX idx_workout_member_date (member_id,workout_date)
);
-- This table stores member goals, goal types, and start and end dates.
CREATE TABLE IF NOT EXISTS challenges (
 id INT PRIMARY KEY CHECK (id > 0),
 member_id INT NOT NULL,
 name VARCHAR(200) NOT NULL,
 type VARCHAR(20) NOT NULL CHECK (type IN ('MINUTES','SESSIONS')),
 target INT NOT NULL CHECK (target > 0),
 start_date DATE NOT NULL,
 end_date DATE NOT NULL,
 CHECK (end_date >= start_date),
 CONSTRAINT fk_challenge_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE RESTRICT
);
