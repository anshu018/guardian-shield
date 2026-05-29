-- Enable UUID-OSSP extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. families Table
CREATE TABLE families (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    family_code VARCHAR(6) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 2. parents Table
CREATE TABLE parents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    family_id UUID REFERENCES families(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(15) UNIQUE NOT NULL
);

-- 3. children Table
CREATE TABLE children (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    family_id UUID REFERENCES families(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    age INTEGER,
    phone VARCHAR(15)
);

-- 4. child_location Table
CREATE TABLE child_location (
    id BIGSERIAL PRIMARY KEY,
    child_id UUID REFERENCES children(id) ON DELETE CASCADE NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    battery_percentage INTEGER,
    accuracy_radius REAL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 5. sos_events Table
CREATE TABLE sos_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    child_id UUID REFERENCES children(id) ON DELETE CASCADE NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    is_active BOOLEAN DEFAULT true NOT NULL,
    triggered_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 6. remote_commands Table
CREATE TABLE remote_commands (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    child_id UUID REFERENCES children(id) ON DELETE CASCADE NOT NULL,
    command_type VARCHAR(50) NOT NULL,
    payload JSONB,
    is_executed BOOLEAN DEFAULT false NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    executed_at TIMESTAMP WITH TIME ZONE
);

-- 7. app_usage Table
CREATE TABLE app_usage (
    id BIGSERIAL PRIMARY KEY,
    child_id UUID REFERENCES children(id) ON DELETE CASCADE NOT NULL,
    package_name VARCHAR(255) NOT NULL,
    app_name VARCHAR(100) NOT NULL,
    opened_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE
);

-- Enable Row-Level Security (RLS) on all tables
ALTER TABLE families ENABLE ROW LEVEL SECURITY;
ALTER TABLE parents ENABLE ROW LEVEL SECURITY;
ALTER TABLE children ENABLE ROW LEVEL SECURITY;
ALTER TABLE child_location ENABLE ROW LEVEL SECURITY;
ALTER TABLE sos_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE remote_commands ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_usage ENABLE ROW LEVEL SECURITY;

-- Families RLS Policies
CREATE POLICY "Families are readable only by verified members"
ON families FOR SELECT
USING (
  id IN (SELECT family_id FROM parents WHERE user_id = auth.uid())
);

-- Parents RLS Policies
CREATE POLICY "Parents can view their own profile"
ON parents FOR SELECT
USING (user_id = auth.uid());

CREATE POLICY "Parents can update their own profile"
ON parents FOR UPDATE
USING (user_id = auth.uid());

CREATE POLICY "Parents can insert their profile during registration"
ON parents FOR INSERT
WITH CHECK (auth.uid() IS NOT NULL);

-- Children RLS Policies
CREATE POLICY "Children are readable only by family parents"
ON children FOR SELECT
USING (
  family_id IN (SELECT family_id FROM parents WHERE user_id = auth.uid())
);

CREATE POLICY "Children can be inserted by family parents"
ON children FOR INSERT
WITH CHECK (
  family_id IN (SELECT family_id FROM parents WHERE user_id = auth.uid())
);

-- Child Location RLS Policies
CREATE POLICY "Child locations are readable only by family parents"
ON child_location FOR SELECT
USING (
  child_id IN (
    SELECT c.id FROM children c
    JOIN parents p ON c.family_id = p.family_id
    WHERE p.user_id = auth.uid()
  )
);

CREATE POLICY "Child app can insert its own locations"
ON child_location FOR INSERT
WITH CHECK (true);

-- SOS Events RLS Policies
CREATE POLICY "SOS events are readable only by family parents"
ON sos_events FOR SELECT
USING (
  child_id IN (
    SELECT c.id FROM children c
    JOIN parents p ON c.family_id = p.family_id
    WHERE p.user_id = auth.uid()
  )
);

CREATE POLICY "SOS events can be updated/resolved by family parents"
ON sos_events FOR UPDATE
USING (
  child_id IN (
    SELECT c.id FROM children c
    JOIN parents p ON c.family_id = p.family_id
    WHERE p.user_id = auth.uid()
  )
);

CREATE POLICY "Child app can insert SOS events"
ON sos_events FOR INSERT
WITH CHECK (true);

-- Remote Commands RLS Policies
CREATE POLICY "Remote commands are readable only by family parents"
ON remote_commands FOR SELECT
USING (
  child_id IN (
    SELECT c.id FROM children c
    JOIN parents p ON c.family_id = p.family_id
    WHERE p.user_id = auth.uid()
  )
);

CREATE POLICY "Remote commands can be inserted by family parents"
ON remote_commands FOR INSERT
WITH CHECK (
  child_id IN (
    SELECT c.id FROM children c
    JOIN parents p ON c.family_id = p.family_id
    WHERE p.user_id = auth.uid()
  )
);

CREATE POLICY "Child app can read and update commands for execution"
ON remote_commands FOR SELECT
USING (true);

CREATE POLICY "Child app can update commands upon execution"
ON remote_commands FOR UPDATE
USING (true);

-- App Usage RLS Policies
CREATE POLICY "App usage logs are readable only by family parents"
ON app_usage FOR SELECT
USING (
  child_id IN (
    SELECT c.id FROM children c
    JOIN parents p ON c.family_id = p.family_id
    WHERE p.user_id = auth.uid()
  )
);

CREATE POLICY "Child app can insert usage logs"
ON app_usage FOR INSERT
WITH CHECK (true);

-- Realtime Setup
-- Remove existing publication tables from realtime if they exist
-- (Supabase default publication name is usually supabase_realtime)
BEGIN;
  DROP PUBLICATION IF EXISTS supabase_realtime;
  CREATE PUBLICATION supabase_realtime;
COMMIT;

ALTER PUBLICATION supabase_realtime ADD TABLE child_location;
ALTER PUBLICATION supabase_realtime ADD TABLE sos_events;
ALTER PUBLICATION supabase_realtime ADD TABLE remote_commands;
