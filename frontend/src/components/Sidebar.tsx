import React from "react";
import type { DatasourceSummary } from "../types/api";

export type ActiveTab = "analytics" | "schema" | "datasources";

interface SidebarProps {
  datasources: DatasourceSummary[];
  selectedDatasourceId: string;
  onSelectDatasource: (id: string) => void;
  activeTab: ActiveTab;
  onSelectTab: (tab: ActiveTab) => void;
  tableCount?: number;
}

export const Sidebar: React.FC<SidebarProps> = ({
  datasources,
  selectedDatasourceId,
  onSelectDatasource,
  activeTab,
  onSelectTab,
  tableCount,
}) => {
  const currentDatasource = datasources.find((d) => d.id === selectedDatasourceId);

  return (
    <aside className="w-full lg:w-72 shrink-0 border-b lg:border-b-0 lg:border-r border-slate-800 bg-slate-900/70 p-4 lg:min-h-[calc(100vh-61px)] flex flex-col justify-between space-y-6">
      <div className="space-y-6">
        {/* Datasource Selector */}
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">
            Target Datasource
          </label>
          <div className="space-y-2">
            {datasources.map((ds) => {
              const isSelected = ds.id === selectedDatasourceId;
              const isConnected = ds.status === "CONNECTED";
              return (
                <button
                  key={ds.id}
                  type="button"
                  onClick={() => onSelectDatasource(ds.id)}
                  className={`w-full text-left rounded-xl p-3 border transition-all ${
                    isSelected
                      ? "border-indigo-500 bg-indigo-950/40 shadow-sm shadow-indigo-500/10 text-white"
                      : "border-slate-800 bg-slate-800/40 hover:bg-slate-800 hover:border-slate-700 text-slate-300"
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-sm">{ds.name}</span>
                    <span
                      className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                        isConnected
                          ? "bg-emerald-500/20 text-emerald-300 border border-emerald-500/30"
                          : "bg-rose-500/20 text-rose-300 border border-rose-500/30"
                      }`}
                    >
                      <span
                        className={`w-1.5 h-1.5 rounded-full mr-1.5 ${
                          isConnected ? "bg-emerald-400" : "bg-rose-400"
                        }`}
                      ></span>
                      {isConnected ? "Connected" : "Offline"}
                    </span>
                  </div>
                  <p className="text-xs text-slate-400 mt-1 line-clamp-1">{ds.description}</p>
                  <div className="mt-2 flex items-center justify-between text-[11px] text-slate-400">
                    <span className="font-mono text-slate-400">{ds.id === "crm" ? "PostgreSQL" : "MySQL"}</span>
                    <span>{ds.tools.length} MCP Tools</span>
                  </div>
                </button>
              );
            })}
          </div>
        </div>

        {/* View Navigation Tabs */}
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">
            Navigation
          </label>
          <nav className="space-y-1">
            <button
              type="button"
              onClick={() => onSelectTab("analytics")}
              className={`w-full flex items-center space-x-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                activeTab === "analytics"
                  ? "bg-indigo-600 text-white shadow-sm"
                  : "text-slate-400 hover:text-slate-200 hover:bg-slate-800/60"
              }`}
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"
                />
              </svg>
              <span>Query & Visualizations</span>
            </button>

            <button
              type="button"
              onClick={() => onSelectTab("schema")}
              className={`w-full flex items-center space-x-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                activeTab === "schema"
                  ? "bg-indigo-600 text-white shadow-sm"
                  : "text-slate-400 hover:text-slate-200 hover:bg-slate-800/60"
              }`}
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4"
                />
              </svg>
              <div className="flex-1 flex items-center justify-between">
                <span>Schema Explorer</span>
                {typeof tableCount === "number" && (
                  <span className="text-[11px] px-2 py-0.5 rounded-full bg-slate-800 text-slate-300">
                    {tableCount} tables
                  </span>
                )}
              </div>
            </button>

            <button
              type="button"
              onClick={() => onSelectTab("datasources")}
              className={`w-full flex items-center space-x-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                activeTab === "datasources"
                  ? "bg-indigo-600 text-white shadow-sm"
                  : "text-slate-400 hover:text-slate-200 hover:bg-slate-800/60"
              }`}
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"
                />
              </svg>
              <span>Datasources Overview</span>
            </button>
          </nav>
        </div>

        {/* Selected Context Summary */}
        {currentDatasource && (
          <div className="rounded-xl border border-slate-800 bg-slate-950/40 p-3.5 space-y-2 text-xs">
            <span className="font-semibold text-slate-300 block">Datasource Profile</span>
            <div className="flex justify-between text-slate-400">
              <span>Identifier:</span>
              <span className="font-mono text-slate-200">{currentDatasource.id}</span>
            </div>
            <div className="flex justify-between text-slate-400">
              <span>Protocol:</span>
              <span className="text-slate-200 font-mono text-[11px]">Streamable HTTP MCP</span>
            </div>
            <div className="flex justify-between text-slate-400">
              <span>Tools Active:</span>
              <span className="text-indigo-400 font-semibold">{currentDatasource.tools.length} Registered</span>
            </div>
          </div>
        )}
      </div>

      {/* Security & System Footer */}
      <div className="pt-4 border-t border-slate-800/80 space-y-2 text-xs text-slate-400">
        <div className="flex items-center space-x-2 text-emerald-400">
          <svg className="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"
            />
          </svg>
          <span className="font-medium text-[11px]">Read-Only SQL Enforced</span>
        </div>
        <p className="text-[11px] text-slate-400 leading-relaxed">
          Direct database access isolated strictly behind Spring AI MCP connector clients.
        </p>
      </div>
    </aside>
  );
};
