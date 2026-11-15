import React from "react";
import type { DatasourceSummary } from "../types/api";

interface DatasourcesOverviewProps {
  datasources: DatasourceSummary[];
  selectedDatasourceId: string;
  onSelectDatasource: (id: string) => void;
  onRefresh: () => void;
  loading: boolean;
}

export const DatasourcesOverview: React.FC<DatasourcesOverviewProps> = ({
  datasources,
  selectedDatasourceId,
  onSelectDatasource,
  onRefresh,
  loading,
}) => {
  return (
    <div className="space-y-6">
      {/* View Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div>
          <h2 className="text-xl font-bold text-slate-100">
            Datasource Connectors & MCP Mesh
          </h2>
          <p className="text-sm text-slate-400 mt-1">
            Registered business datasources connected to Spring AI MCP client runtime.
          </p>
        </div>

        <button
          type="button"
          onClick={onRefresh}
          disabled={loading}
          className="flex items-center space-x-1.5 rounded-lg border border-slate-700 bg-slate-800 px-3 py-1.5 text-xs font-medium text-slate-200 hover:bg-slate-700 transition-all disabled:opacity-50"
        >
          <svg
            className={`h-3.5 w-3.5 ${loading ? "animate-spin text-indigo-400" : "text-slate-400"}`}
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          <span>Ping All MCP Servers</span>
        </button>
      </div>

      {/* Datasource Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {datasources.map((ds) => {
          const isSelected = ds.id === selectedDatasourceId;
          const isConnected = ds.status === "CONNECTED";
          const isPostgres = ds.id === "crm";

          return (
            <div
              key={ds.id}
              className={`rounded-2xl border p-6 transition-all ${
                isSelected
                  ? "border-indigo-500/80 bg-slate-900/90 shadow-lg shadow-indigo-500/10"
                  : "border-slate-800 bg-slate-900/50 hover:border-slate-700"
              }`}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="space-y-1">
                  <div className="flex items-center space-x-2">
                    <h3 className="text-lg font-bold text-slate-100">{ds.name}</h3>
                    <span className="font-mono text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300">
                      {ds.id}
                    </span>
                  </div>
                  <p className="text-xs text-slate-400 leading-relaxed">
                    {ds.description}
                  </p>
                </div>

                <span
                  className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold ${
                    isConnected
                      ? "bg-emerald-500/20 text-emerald-300 border border-emerald-500/30"
                      : "bg-rose-500/20 text-rose-300 border border-rose-500/30"
                  }`}
                >
                  <span
                    className={`w-2 h-2 rounded-full mr-1.5 ${
                      isConnected ? "bg-emerald-400 animate-pulse" : "bg-rose-400"
                    }`}
                  ></span>
                  {ds.status}
                </span>
              </div>

              {/* Details and Metadata */}
              <div className="mt-5 space-y-3 border-t border-slate-800/80 pt-4 text-xs">
                <div className="flex justify-between text-slate-400">
                  <span>Engine / RDBMS:</span>
                  <span className="font-semibold text-slate-200">
                    {isPostgres ? "PostgreSQL (port 5432)" : "MySQL (port 3306)"}
                  </span>
                </div>

                <div className="flex justify-between text-slate-400">
                  <span>MCP Server Port:</span>
                  <span className="font-mono text-indigo-300">
                    {isPostgres ? "http://127.0.0.1:8081/mcp" : "http://127.0.0.1:8082/mcp"}
                  </span>
                </div>

                <div className="flex justify-between text-slate-400">
                  <span>Transport:</span>
                  <span className="text-slate-300 font-mono text-[11px]">
                    Streamable HTTP (JSON-RPC 2.0)
                  </span>
                </div>

                <div className="space-y-1.5 pt-1">
                  <span className="text-slate-400 block font-medium">
                    Available MCP Tools ({ds.tools.length}):
                  </span>
                  {ds.tools.length > 0 ? (
                    <div className="flex flex-wrap gap-1.5">
                      {ds.tools.map((t) => (
                        <span
                          key={t}
                          className="px-2 py-0.5 rounded bg-slate-800 font-mono text-[10px] text-slate-300 border border-slate-700/60"
                        >
                          {t}
                        </span>
                      ))}
                    </div>
                  ) : (
                    <span className="text-slate-500 italic">
                      No tools discovered (Server offline or unreachable)
                    </span>
                  )}
                </div>
              </div>

              {/* Action Button */}
              <div className="mt-6 pt-4 border-t border-slate-800/60 flex items-center justify-between">
                <span className="text-[11px] text-slate-500">
                  {isSelected ? "Currently Active Datasource" : "Click to select"}
                </span>
                <button
                  type="button"
                  onClick={() => onSelectDatasource(ds.id)}
                  disabled={isSelected}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                    isSelected
                      ? "bg-slate-800 text-slate-400 cursor-default"
                      : "bg-indigo-600 text-white hover:bg-indigo-500 shadow-sm"
                  }`}
                >
                  {isSelected ? "Active" : `Switch to ${ds.name}`}
                </button>
              </div>
            </div>
          );
        })}
      </div>

      {/* Architecture Info Banner */}
      <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-5 space-y-3 text-xs text-slate-400">
        <h4 className="font-semibold text-slate-200 text-sm flex items-center space-x-2">
          <svg className="h-4 w-4 text-indigo-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span>Architecture & Resilience Protocol</span>
        </h4>
        <p className="leading-relaxed">
          The main Spring Boot REST Backend connects to the business databases exclusively via standard Model Context Protocol (MCP) clients using Spring AI 2.0.2. Schema discovery, table introspection, foreign key mapping, and read-only query execution are all performed dynamically via tool discovery over Streamable HTTP.
        </p>
        <p className="leading-relaxed">
          If either MCP server experiences an outage or network disconnect, fault tolerance guarantees that the backend and the other healthy datasource continue functioning uninterrupted.
        </p>
      </div>
    </div>
  );
};
