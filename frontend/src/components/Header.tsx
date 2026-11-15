import React from "react";
import type { DatasourceSummary } from "../types/api";

interface HeaderProps {
  datasources: DatasourceSummary[];
  loading: boolean;
  onRefresh: () => void;
  apiBaseUrl: string;
}

export const Header: React.FC<HeaderProps> = ({
  datasources,
  loading,
  onRefresh,
  apiBaseUrl,
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

        {/* Datasources Live Status Badges & Refresh */}
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
        </div>
      </div>
    </header>
  );
};
