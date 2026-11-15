import React, { useState } from "react";
import { useAuth } from "../context/AuthContext";

export const LoginPage: React.FC = () => {
  const { login, register, error, clearError } = useAuth();
  const [isRegisterMode, setIsRegisterMode] = useState<boolean>(false);

  // Form states
  const [username, setUsername] = useState<string>("");
  const [email, setEmail] = useState<string>("");
  const [password, setPassword] = useState<string>("");
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [validationError, setValidationError] = useState<string | null>(null);

  const toggleMode = (mode: boolean) => {
    setIsRegisterMode(mode);
    setValidationError(null);
    clearError();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setValidationError(null);
    clearError();

    if (!username.trim()) {
      setValidationError("Username is required.");
      return;
    }

    if (isRegisterMode && !email.trim()) {
      setValidationError("Email is required for registration.");
      return;
    }

    if (!password) {
      setValidationError("Password is required.");
      return;
    }

    if (isRegisterMode && password.length < 8) {
      setValidationError("Password must be at least 8 characters long.");
      return;
    }

    setSubmitting(true);
    try {
      if (isRegisterMode) {
        await register({
          username: username.trim(),
          email: email.trim(),
          password,
        });
      } else {
        await login({
          username: username.trim(),
          password,
        });
      }
    } catch {
      // Error handled by AuthContext
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col justify-center items-center p-4 selection:bg-indigo-500 selection:text-white">
      {/* Background Glow Effect */}
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 bg-indigo-600/10 rounded-full blur-3xl pointer-events-none" />

      <div className="w-full max-w-md z-10">
        {/* Brand Header */}
        <div className="text-center mb-8">
          <div className="inline-flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-indigo-500 to-purple-600 shadow-lg shadow-indigo-500/25 text-white font-extrabold text-2xl tracking-wider mb-4">
            A²
          </div>
          <h1 className="text-2xl font-bold text-slate-100 tracking-tight">
            Agentic Analytics Platform
          </h1>
          <p className="text-sm text-slate-400 mt-1">
            Enterprise Federated Intelligence &amp; MCP Analytics
          </p>
        </div>

        {/* Auth Card */}
        <div className="rounded-2xl border border-slate-800 bg-slate-900/80 backdrop-blur-xl p-8 shadow-2xl space-y-6">
          {/* Mode Switch Tabs */}
          <div className="grid grid-cols-2 p-1 bg-slate-950/60 rounded-xl border border-slate-800 text-xs font-semibold">
            <button
              type="button"
              onClick={() => toggleMode(false)}
              className={`py-2 rounded-lg transition-all ${
                !isRegisterMode
                  ? "bg-indigo-600 text-white shadow-sm"
                  : "text-slate-400 hover:text-slate-200"
              }`}
            >
              Sign In
            </button>
            <button
              type="button"
              onClick={() => toggleMode(true)}
              className={`py-2 rounded-lg transition-all ${
                isRegisterMode
                  ? "bg-indigo-600 text-white shadow-sm"
                  : "text-slate-400 hover:text-slate-200"
              }`}
            >
              Create Account
            </button>
          </div>

          {/* Error Alert */}
          {(validationError || error) && (
            <div className="rounded-xl border border-rose-500/30 bg-rose-500/10 p-3.5 text-xs text-rose-300 flex items-start space-x-2">
              <svg className="w-4 h-4 shrink-0 mt-0.5 text-rose-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
              <span>{validationError || error}</span>
            </div>
          )}

          {/* Form */}
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1.5">
                {isRegisterMode ? "Username" : "Username or Email"}
              </label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder={isRegisterMode ? "e.g. jdoe_analyst" : "Enter your username or email"}
                required
                className="w-full rounded-xl border border-slate-700 bg-slate-950/80 px-3.5 py-2.5 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition-colors"
              />
            </div>

            {isRegisterMode && (
              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1.5">
                  Email Address
                </label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="e.g. user@enterprise.corp"
                  required
                  className="w-full rounded-xl border border-slate-700 bg-slate-950/80 px-3.5 py-2.5 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition-colors"
                />
              </div>
            )}

            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="block text-xs font-medium text-slate-300">
                  Password
                </label>
                {isRegisterMode && (
                  <span className="text-[11px] text-slate-400">Min 8 chars, 1 letter, 1 number</span>
                )}
              </div>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••••••"
                required
                className="w-full rounded-xl border border-slate-700 bg-slate-950/80 px-3.5 py-2.5 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition-colors"
              />
            </div>

            <button
              type="submit"
              disabled={submitting}
              className="w-full mt-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 px-4 py-2.5 text-sm font-semibold text-white shadow-lg shadow-indigo-600/30 transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center space-x-2"
            >
              {submitting ? (
                <>
                  <svg className="animate-spin h-4 w-4" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
                  </svg>
                  <span>{isRegisterMode ? "Creating Account..." : "Signing In..."}</span>
                </>
              ) : (
                <span>{isRegisterMode ? "Create Analyst Account" : "Sign In to Platform"}</span>
              )}
            </button>
          </form>

          {/* RBAC Notice */}
          <div className="pt-4 border-t border-slate-800 text-center text-xs text-slate-400 space-y-1.5">
            <p className="flex items-center justify-center space-x-1.5 text-slate-400">
              <svg className="w-3.5 h-3.5 text-indigo-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
              </svg>
              <span>Role-Based Access Control Active</span>
            </p>
            <p className="text-[11px] text-slate-500">
              New accounts receive default <strong className="text-indigo-300">ANALYST</strong> role with natural language &amp; SQL privileges.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};
