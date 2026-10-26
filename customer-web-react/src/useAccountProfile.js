import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "./api.js";

export function useAccountProfile(token) {
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const generation = useRef(0);

  const reload = useCallback(async () => {
    const requestGeneration = ++generation.current;
    if (!token) { setProfile(null); setLoading(false); setError(""); return; }
    setLoading(true);
    setError("");
    try {
      const result = await api.getProfile(token);
      if (requestGeneration === generation.current) setProfile(result);
    } catch (failure) {
      if (requestGeneration === generation.current) setError(failure.message || "We could not load your profile.");
    } finally {
      if (requestGeneration === generation.current) setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    setProfile(null);
    setSaving(false);
    reload();
    return () => { generation.current += 1; };
  }, [reload]);

  const save = useCallback(async (draft) => {
    if (!token) throw new Error("Sign in to save your profile.");
    const requestGeneration = generation.current;
    setSaving(true);
    try {
      const result = await api.updateProfile(token, draft);
      if (requestGeneration === generation.current) setProfile(result);
      return result;
    } finally {
      if (requestGeneration === generation.current) setSaving(false);
    }
  }, [token]);

  return { profile, loading, error, saving, reload, save };
}
