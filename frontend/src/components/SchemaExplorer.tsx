import React, { useState, useEffect } from "react";
import { api, extractErrorMessage } from "../services/api";
import type {
  TableItem,
  TableSchemaResponse,
  RelationshipItem,
} from "../types/api";

interface SchemaExplorerProps {
  datasourceId: string;
  datasourceName: string;
  onUseTableInQuery: (tableName: string) => void;
}

export const SchemaExplorer: React.FC<SchemaExplorerProps> = ({
  datasourceId,
  datasourceName,
  onUseTableInQuery,
}) => {
  const [tables, setTables] = useState<TableItem[]>([]);
  const [selectedTable, setSelectedTable] = useState<string | null>(null);
  const [tableSchema, setTableSchema] = useState<TableSchemaResponse | null>(null);
  const [relationships, setRelationships] = useState<RelationshipItem[]>([]);
  const [loadingTables, setLoadingTables] = useState<boolean>(false);
  const [loadingSchema, setLoadingSchema] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState<string>("");

  useEffect(() => {
    let isMounted = true;
    const loadTablesAndRelationships = async () => {
      setLoadingTables(true);
      setError(null);
      setSelectedTable(null);
      setTableSchema(null);

      try {
        const [tablesData, relData] = await Promise.all([
          api.getTables(datasourceId),
          api.getRelationships(datasourceId).catch((err) => {
            console.warn("Could not load relationships:", err);
            return { schema: "", relationships: [] };
          }),
        ]);

        if (!isMounted) return;
        setTables(tablesData.tables || []);
        setRelationships(relData.relationships || []);

        // Auto-select first table if present
        if (tablesData.tables && tablesData.tables.length > 0) {
          const first = tablesData.tables[0].tableName;
          setSelectedTable(first);
          loadTableSchema(first);
        }
      } catch (err) {
        if (!isMounted) return;
        setError(extractErrorMessage(err));
      } finally {
        if (isMounted) setLoadingTables(false);
      }
    };

    loadTablesAndRelationships();
    return () => {
      isMounted = false;
    };
  }, [datasourceId]);

  const loadTableSchema = async (tableName: string) => {
    setSelectedTable(tableName);
    setLoadingSchema(true);
    try {
      const schemaData = await api.getTableSchema(datasourceId, tableName);
      setTableSchema(schemaData);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoadingSchema(false);
    }
  };

  const filteredTables = tables.filter((t) =>
    t.tableName.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const tableRelationships = relationships.filter(
    (r) =>
      r.sourceTable.toLowerCase() === (selectedTable || "").toLowerCase() ||
      r.targetTable.toLowerCase() === (selectedTable || "").toLowerCase()
  );

  return (
    <div className="space-y-6">
      {/* View Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div>
          <h2 className="text-xl font-bold text-slate-100 flex items-center space-x-2">
            <span>Schema & Entity Explorer</span>
            <span className="text-xs px-2.5 py-0.5 rounded-full bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 font-mono">
              {datasourceName}
            </span>
          </h2>
          <p className="text-sm text-slate-400 mt-1">
            Explore live physical schemas, column definitions, data types, and foreign key relationships discovered via MCP.
          </p>
        </div>
      </div>

      {error && (
        <div className="rounded-xl border border-rose-500/30 bg-rose-500/10 p-4 text-rose-300 text-sm flex items-start space-x-3">
          <svg className="h-5 w-5 shrink-0 text-rose-400 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          <div>
            <span className="font-semibold block">Schema Introspection Error</span>
            <span>{error}</span>
          </div>
        </div>
      )}

      {loadingTables ? (
        <div className="flex flex-col items-center justify-center p-16 space-y-3 text-slate-400">
          <svg className="animate-spin h-8 w-8 text-indigo-500" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
          </svg>
          <p className="text-sm">Querying MCP server for database catalog...</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Tables List Panel */}
          <div className="lg:col-span-4 rounded-xl border border-slate-800 bg-slate-900/60 p-4 space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Discovered Tables ({tables.length})
              </span>
            </div>

            <div className="relative">
              <input
                type="text"
                placeholder="Filter tables..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full rounded-lg border border-slate-700 bg-slate-800/80 px-3 py-2 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500"
              />
            </div>

            <div className="space-y-1.5 max-h-[550px] overflow-y-auto pr-1">
              {filteredTables.map((t) => {
                const isSelected = selectedTable === t.tableName;
                return (
                  <button
                    key={t.tableName}
                    type="button"
                    onClick={() => loadTableSchema(t.tableName)}
                    className={`w-full flex items-center justify-between rounded-lg px-3 py-2.5 text-xs font-medium transition-all ${
                      isSelected
                        ? "bg-indigo-600 text-white shadow-sm"
                        : "text-slate-300 bg-slate-800/40 hover:bg-slate-800 hover:text-white"
                    }`}
                  >
                    <div className="flex items-center space-x-2">
                      <svg className="h-4 w-4 opacity-70" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 10h18M3 14h18m-9-4v8m-7 0h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
                      </svg>
                      <span className="font-mono">{t.tableName}</span>
                    </div>
                    <span className="text-[10px] opacity-60 font-mono">
                      {t.schema}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Table Schema & Columns Details Panel */}
          <div className="lg:col-span-8 space-y-6">
            {selectedTable && (
              <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-4">
                <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-800 pb-4">
                  <div>
                    <div className="flex items-center space-x-2">
                      <h3 className="text-lg font-bold text-slate-100 font-mono">
                        {selectedTable}
                      </h3>
                      {tableSchema && (
                        <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-400 font-mono">
                          schema: {tableSchema.schema}
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-slate-400 mt-1">
                      {tableSchema ? `${tableSchema.columns.length} columns defined` : "Loading columns..."}
                    </p>
                  </div>

                  <button
                    type="button"
                    onClick={() => onUseTableInQuery(selectedTable)}
                    className="flex items-center space-x-1.5 rounded-lg bg-indigo-600 px-3.5 py-1.5 text-xs font-semibold text-white hover:bg-indigo-500 shadow-sm transition-all"
                  >
                    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                    </svg>
                    <span>Open in Query Workspace</span>
                  </button>
                </div>

                {loadingSchema ? (
                  <div className="flex items-center justify-center p-10 text-slate-400 space-x-2">
                    <svg className="animate-spin h-5 w-5 text-indigo-500" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
                    </svg>
                    <span className="text-xs">Fetching column metadata...</span>
                  </div>
                ) : tableSchema ? (
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs border-collapse">
                      <thead>
                        <tr className="border-b border-slate-800 text-slate-400 uppercase tracking-wider text-[11px] bg-slate-950/40">
                          <th className="py-2.5 px-3">#</th>
                          <th className="py-2.5 px-3">Column</th>
                          <th className="py-2.5 px-3">Data Type</th>
                          <th className="py-2.5 px-3">Primary Key</th>
                          <th className="py-2.5 px-3">Nullable</th>
                          <th className="py-2.5 px-3">Default</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-800/60 font-mono">
                        {tableSchema.columns.map((col) => (
                          <tr key={col.name} className="hover:bg-slate-800/30 transition-colors">
                            <td className="py-2 px-3 text-slate-500">{col.ordinalPosition}</td>
                            <td className="py-2 px-3 font-semibold text-slate-200 flex items-center space-x-1.5">
                              {col.primaryKey && (
                                <span title="Primary Key" className="text-amber-400">
                                  🔑
                                </span>
                              )}
                              <span>{col.name}</span>
                            </td>
                            <td className="py-2 px-3 text-indigo-400">{col.type}</td>
                            <td className="py-2 px-3">
                              {col.primaryKey ? (
                                <span className="inline-block px-2 py-0.5 rounded text-[10px] font-sans font-semibold bg-amber-500/10 text-amber-300 border border-amber-500/20">
                                  PK
                                </span>
                              ) : (
                                <span className="text-slate-600">—</span>
                              )}
                            </td>
                            <td className="py-2 px-3">
                              {col.nullable ? (
                                <span className="text-slate-400 font-sans">NULL</span>
                              ) : (
                                <span className="text-rose-400 font-sans font-medium">NOT NULL</span>
                              )}
                            </td>
                            <td className="py-2 px-3 text-slate-400 truncate max-w-[150px]">
                              {col.default !== null && col.default !== undefined
                                ? String(col.default)
                                : "—"}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : null}
              </div>
            )}

            {/* Foreign Key Relationships */}
            <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 space-y-4">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <h4 className="text-sm font-semibold text-slate-200 flex items-center space-x-2">
                  <span>Entity Relationships & Foreign Keys</span>
                  <span className="text-xs px-2 py-0.5 rounded-full bg-slate-800 text-slate-400 font-mono">
                    {relationships.length} total in schema
                  </span>
                </h4>
              </div>

              {relationships.length === 0 ? (
                <p className="text-xs text-slate-500 py-3">
                  No explicit foreign key relationships defined in this schema catalog.
                </p>
              ) : (
                <div className="space-y-2 max-h-[300px] overflow-y-auto">
                  {(selectedTable ? tableRelationships : relationships).map((rel, idx) => (
                    <div
                      key={idx}
                      className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-slate-800/80 bg-slate-950/40 p-3 text-xs"
                    >
                      <div className="flex items-center space-x-2 font-mono">
                        <span className="px-2 py-1 rounded bg-slate-800 text-indigo-300 font-semibold">
                          {rel.sourceTable}.{rel.sourceColumn}
                        </span>
                        <span className="text-slate-500">→</span>
                        <span className="px-2 py-1 rounded bg-slate-800 text-emerald-300 font-semibold">
                          {rel.targetTable}.{rel.targetColumn}
                        </span>
                      </div>
                      <span className="text-[11px] font-mono text-slate-500">
                        constraint: {rel.constraintName}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
