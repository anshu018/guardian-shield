import { serve } from "https://deno.land/x/sift@0.6.0/mod.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req) => {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  const { family_code, name, age } = await req.json();

  if (!family_code || !name || !age) {
    return new Response(
      JSON.stringify({ error: "Missing required fields" }),
      { status: 400, headers: { "Content-Type": "application/json" } }
    );
  }

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
  );

  const { data: family, error: familyError } = await supabase
    .from("families")
    .select("id")
    .eq("family_code", family_code)
    .single();

  if (familyError || !family) {
    return new Response(
      JSON.stringify({ error: "Invalid family code" }),
      { status: 404, headers: { "Content-Type": "application/json" } }
    );
  }

  const { data: child, error: childError } = await supabase
    .from("children")
    .insert({
      family_id: family.id,
      name: name,
      age: age
    })
    .select()
    .single();

  if (childError) {
    return new Response(
      JSON.stringify({ error: childError.message }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }

  return new Response(
    JSON.stringify({ success: true, child_id: child.id, family_id: family.id }),
    { status: 200, headers: { "Content-Type": "application/json" } }
  );
});
