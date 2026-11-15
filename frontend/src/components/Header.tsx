import React from "react";
import type { DatasourceSummary, UserProfile } from "../types/api";

interface HeaderProps {
  datasources: DatasourceSummary[];
  loading: boolean;
  onRefresh: () => void;
  apiBaseUrl: string;
  user?: UserProfile | null;
  onLogout?: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  datasources,
  loading,
  onRefresh,
  apiBaseUrl,
  user,
  onLogout,
}) => {
  return (
    <header className="sticky top-0 z-30 border-b border-slate-800 bg-slate-900/95 backdrop-blur-md px-6 py-3.5 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-4">
        {/* Brand & Title */}
        <div className="flex items-center space-x-3.5">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-500 to-purple-600 shadow-md shadow-indigo-500/20 text-white font-bold text-lg tracking-wider">
            A²
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <h1 className="text-lg font-bold text-slate-100 tracking-tight">
                Agentic Analytics Platform
              </h1>
              <span className="rounded-full bg-indigo-500/10 border border-indigo-500/30 px-2 py-0.5 text-[11px] font-semibold text-indigo-400">
                Enterprise MCP Mesh
              </span>
            </div>
            <p className="text-xs text-slate-400">
              Live federated analytics across MCP business datasources
            </p>
          </div>
        </div>

        {/* Datasources Live Status Badges, Refresh & User Profile */}
        <div className="flex flex-wrap items-center gap-3">
          <div className="hidden md:flex items-center space-x-1.5 px-2.5 py-1 rounded-md bg-slate-800/80 border border-slate-700/60 text-xs text-slate-400">
            <span className="text-slate-500">API:</span>
            <code className="text-slate-300 font-mono text-[11px]">{apiBaseUrl}</code>
          </div>

          <div className="flex items-center space-x-2">
            {datasources.map((ds) => {
              const isConnected = ds.status === "CONNECTED";
              return (
                <div
                  key={ds.id}
                  className={`flex items-center space-x-2 rounded-lg border px-3 py-1.5 text-xs transition-colors ${
                    isConnected
                      ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                      : "border-rose-500/30 bg-rose-500/10 text-rose-300"
                  }`}
                  title={`${ds.name} (${ds.id}): ${ds.description}. ${ds.tools.length} MCP tools available.`}
                >
                  <span className="relative flex h-2 w-2">
                    {isConnected && (
                      <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                    )}
                    <span
                      className={`relative inline-flex rounded-full h-2 w-2 ${
                        isConnected ? "bg-emerald-400" : "bg-rose-500"
                      }`}
                    ></span>
                  </span>
                  <span className="font-semibold text-slate-200">{ds.name}</span>
                  <span
                    className={`font-mono text-[10px] font-medium uppercase px-1.5 py-0.2 rounded ${
                      isConnected
                        ? "bg-emerald-950/60 text-emerald-300"
                        : "bg-rose-950/60 text-rose-300"
                    }`}
                  >
                    {ds.status}
                  </span>
                </div>
              );
            })}
          </div>

          {/* Refresh Button */}
          <button
            type="button"
            onClick={onRefresh}
            disabled={loading}
            className="flex items-center space-x-1.5 rounded-lg border border-slate-700 bg-slate-800 px-3 py-1.5 text-xs font-medium text-slate-200 hover:bg-slate-700 hover:text-white transition-all disabled:opacity-50"
            title="Refresh Datasources and System Status"
          >
            <svg
              className={`h-3.5 w-3.5 ${loading ? "animate-spin text-indigo-400" : "text-slate-400"}`}
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
              />
            </svg>
            <span>{loading ? "Checking..." : "Refresh"}</span>
          </button>

          {/* User Profile & Sign Out */}
          {user && (
            <div className="flex items-center space-x-2.5 pl-3 border-l border-slate-800">
              <div className="flex items-center space-x-2">
                <div className="h-7 w-7 rounded-full bg-gradient-to-tr from-indigo-600 to-purple-600 flex items-center justify-center text-xs font-bold text-white shadow-sm uppercase">
                  {user.username.charAt(0)}
                </div>
                <div className="hidden sm:block text-left">
                  <div className="text-xs font-semibold text-slate-200 leading-tight">
                    {user.username}
                  </div>
                  <span
                    className={`inline-block text-[9px] font-mono px-1.5 py-0.2 rounded font-semibold uppercase ${
                      user.role === "ADMIN"
                        ? "bg-purple-500/20 text-purple-300 border border-purple-500/30"
                        : user.role === "ANALYST"
                        ? "bg-indigo-500/20 text-indigo-300 border border-indigo-500/30"
                        : "bg-slate-700/60 text-slate-300 border border-slate-600/40"
                    }`}
                  >
                    {user.role}
                  </span>
                </div>
              </div>

              {onLogout && (
                <button
                  type="button"
                  onClick={onLogout}
                  className="rounded-lg border border-slate-700/80 bg-slate-800/80 p-1.5 text-slate-400 hover:text-rose-300 hover:border-rose-500/40 hover:bg-rose-500/10 transition-colors"
                  title="Sign Out"
                >
                  <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"
                    />
                  </svg>
                </button>
              )}
            </div>
          )}
        </div>
      </div>
    </header>
  );
};
