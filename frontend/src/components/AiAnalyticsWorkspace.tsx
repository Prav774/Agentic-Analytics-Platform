import React, { useState } from "react";
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  LineChart,
  Line,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
} from "recharts";
import type {
  DatasourceSummary,
  AnalyticsQueryResponseData,
} from "../types/api";

const PROMPT_SUGGESTIONS = [
  {
    title: "Monthly Revenue Trend",
    query: "What was our monthly revenue trend?",
    datasource: "ecommerce",
  },
  {
    title: "Highest Revenue Categories",
    query: "Which product categories generated the highest revenue?",
    datasource: "ecommerce",
  },
  {
    title: "Monthly Order Count (Last 6 Months)",
    query: "Show the monthly order count for the last six months.",
    datasource: "ecommerce",
  },
  {
    title: "Sales Opportunities in Pipeline",
    query: "Which sales opportunities are currently in the pipeline?",
    datasource: "crm",
  },
  {
    title: "Compare Orders Across Statuses",
    query: "Compare the number of orders across order statuses.",
    datasource: "ecommerce",
  },
];

const CHART_PALETTE = [
  "#6366f1", // Indigo
  "#10b981", // Emerald
  "#f59e0b", // Amber
  "#06b6d4", // Cyan
  "#8b5cf6", // Violet
  "#ec4899", // Pink
  "#14b8a6", // Teal
  "#f97316", // Orange
  "#3b82f6", // Blue
  "#84cc16", // Lime
];

interface AiAnalyticsWorkspaceProps {
  datasources: DatasourceSummary[];
  selectedDatasourceId: string;
  onSelectDatasource: (id: string) => void;
  onExecuteAiQuery: (question: string, datasourceId?: string) => Promise<void>;
  loading: boolean;
  currentResponse: AnalyticsQueryResponseData | null;
  conversationHistory: AnalyticsQueryResponseData[];
  onResetSession: () => void;
  onOpenInManualWorkspace: (sql: string, datasourceId: string) => void;
  error: string | null;
}

export const AiAnalyticsWorkspace: React.FC<AiAnalyticsWorkspaceProps> = ({
  datasources,
  selectedDatasourceId,
  onSelectDatasource,
  onExecuteAiQuery,
  loading,
  currentResponse,
  conversationHistory,
  onResetSession,
  onOpenInManualWorkspace,
  error,
}) => {
  const [question, setQuestion] = useState<string>("");
  const [followUpQuestion, setFollowUpQuestion] = useState<string>("");
  const [copiedSql, setCopiedSql] = useState<boolean>(false);
  const [activeChartOverride, setActiveChartOverride] = useState<string | null>(null);
  const [tableSearch, setTableSearch] = useState<string>("");
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [pageSize, setPageSize] = useState<number>(15);

  const handleSubmit = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!question.trim() || loading) return;
    onExecuteAiQuery(question.trim(), selectedDatasourceId || undefined);
    setQuestion("");
    setActiveChartOverride(null);
  };

  const handleFollowUpSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!followUpQuestion.trim() || loading) return;
    onExecuteAiQuery(followUpQuestion.trim(), selectedDatasourceId || undefined);
    setFollowUpQuestion("");
    setActiveChartOverride(null);
  };

  const handleChipClick = (q: string, ds: string) => {
    setQuestion(q);
    if (ds && ds !== selectedDatasourceId) {
      onSelectDatasource(ds);
    }
  };

  const handleCopySql = () => {
    if (currentResponse?.sql) {
      navigator.clipboard.writeText(currentResponse.sql);
      setCopiedSql(true);
      setTimeout(() => setCopiedSql(false), 2000);
    }
  };

  const handleExportCsv = () => {
    if (!currentResponse || currentResponse.rows.length === 0) return;
    const headers = currentResponse.columns.join(",");
    const rows = currentResponse.rows.map((r) =>
      currentResponse.columns
        .map((col) => {
          const val = r[col];
          if (val === null || val === undefined) return "";
          return `"${String(val).replace(/"/g, '""')}"`;
        })
        .join(",")
    );
    const csv = [headers, ...rows].join("\n");
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `analytics_export_${Date.now()}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  // Determine active chart type
  const effectiveChartType = activeChartOverride || currentResponse?.visualization?.chartType || "table";

  // Prepare chart data from rows
  const chartData = React.useMemo(() => {
    if (!currentResponse || currentResponse.rows.length === 0) return [];
    const xCol = currentResponse.visualization?.xAxisColumn || currentResponse.columns[0];
    const yCol = currentResponse.visualization?.yAxisColumn || currentResponse.columns[1] || currentResponse.columns[0];

    return currentResponse.rows.slice(0, 30).map((row) => ({
      name: String(row[xCol] ?? "N/A"),
      value: typeof row[yCol] === "number" ? row[yCol] : parseFloat(String(row[yCol])) || 0,
    }));
  }, [currentResponse]);

  // Filtered rows for data table
  const filteredRows = React.useMemo(() => {
    if (!currentResponse || !currentResponse.rows) return [];
    if (!tableSearch.trim()) return currentResponse.rows;
    const query = tableSearch.toLowerCase();
    return currentResponse.rows.filter((row) =>
      currentResponse.columns.some((col) => {
        const val = row[col];
        return val !== null && val !== undefined && String(val).toLowerCase().includes(query);
      })
    );
  }, [currentResponse, tableSearch]);

  const totalPages = Math.max(1, Math.ceil(filteredRows.length / pageSize));
  const paginatedRows = React.useMemo(() => {
    const start = (currentPage - 1) * pageSize;
    return filteredRows.slice(start, start + pageSize);
  }, [filteredRows, currentPage, pageSize]);

  return (
    <div className="space-y-6">
      {/* View Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div>
          <div className="flex items-center space-x-2.5">
            <h2 className="text-xl font-bold text-slate-100">
              AI-Powered Agentic Analytics
            </h2>
            <span className="rounded-full bg-gradient-to-r from-indigo-500/20 to-purple-500/20 border border-indigo-500/30 px-2.5 py-0.5 text-xs font-semibold text-indigo-300">
              Groq LLaMA 3.3 + Spring AI MCP
            </span>
          </div>
          <p className="text-sm text-slate-400 mt-1">
            Ask natural language business questions. The agentic mesh discovers real database schemas via MCP, generates verified SQL, and returns evidence-backed insights.
          </p>
        </div>

        {conversationHistory.length > 0 && (
          <button
            type="button"
            onClick={onResetSession}
            className="flex items-center space-x-1.5 rounded-lg border border-slate-700 bg-slate-800 px-3 py-1.5 text-xs font-medium text-slate-300 hover:text-white hover:bg-slate-700 transition-colors"
          >
            <svg className="w-3.5 h-3.5 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
            </svg>
            <span>Reset Session ({conversationHistory.length} turns)</span>
          </button>
        )}
      </div>

      {/* Suggested Prompt Chips */}
      <div className="space-y-2">
        <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
          Suggested Analytics Questions:
        </span>
        <div className="flex flex-wrap gap-2">
          {PROMPT_SUGGESTIONS.map((item, idx) => (
            <button
              key={idx}
              type="button"
              onClick={() => handleChipClick(item.query, item.datasource)}
              className="rounded-lg border border-slate-800 bg-slate-900/80 px-3 py-1.5 text-xs text-slate-300 hover:border-indigo-500/50 hover:bg-slate-800 hover:text-white transition-all text-left flex items-center space-x-2"
            >
              <span className="h-1.5 w-1.5 rounded-full bg-indigo-400"></span>
              <span>{item.title}</span>
              <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-slate-800 text-slate-400">
                {item.datasource}
              </span>
            </button>
          ))}
        </div>
      </div>

      {/* Main Question Input Box */}
      <form onSubmit={handleSubmit} className="rounded-2xl border border-slate-800 bg-slate-900/80 p-4 space-y-3 shadow-lg shadow-indigo-950/20">
        <div className="flex flex-wrap items-center justify-between gap-3 text-xs">
          <label className="font-semibold uppercase tracking-wider text-slate-400">
            Ask Business Question
          </label>
          <div className="flex items-center space-x-2">
            <span className="text-slate-400">Target Datasource:</span>
            <select
              value={selectedDatasourceId}
              onChange={(e) => onSelectDatasource(e.target.value)}
              className="rounded-lg border border-slate-700 bg-slate-800 px-2.5 py-1 text-xs text-slate-200 focus:outline-none focus:border-indigo-500"
            >
              {datasources.map((ds) => (
                <option key={ds.id} value={ds.id}>
                  {ds.name} ({ds.id === "crm" ? "PostgreSQL" : "MySQL"})
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="relative flex items-center">
          <input
            type="text"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            disabled={loading}
            placeholder="e.g. 'What was our monthly revenue trend?' or 'Which product categories generated highest sales?'"
            className="w-full rounded-xl border border-slate-700 bg-slate-950 px-4 py-3 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500 pr-28 transition-all"
          />
          <button
            type="submit"
            disabled={loading || !question.trim()}
            className="absolute right-2 flex items-center space-x-1.5 rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white hover:bg-indigo-500 shadow-md shadow-indigo-600/30 disabled:opacity-40 disabled:cursor-not-allowed transition-all"
          >
            {loading ? (
              <>
                <svg className="animate-spin h-3.5 w-3.5 text-white" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
                </svg>
                <span>Analyzing...</span>
              </>
            ) : (
              <>
                <span>Ask AI</span>
                <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                </svg>
              </>
            )}
          </button>
        </div>
      </form>

      {/* Loading & Agent Pipeline Indicator */}
      {loading && (
        <div className="rounded-xl border border-indigo-500/30 bg-indigo-950/20 p-5 space-y-3 animate-pulse">
          <div className="flex items-center space-x-3 text-indigo-300">
            <svg className="animate-spin h-5 w-5 text-indigo-400 shrink-0" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
            </svg>
            <span className="font-semibold text-sm">
              Agentic Pipeline Processing Query...
            </span>
          </div>
          <div className="flex flex-wrap gap-2 text-[11px] text-slate-400 font-mono pl-8">
            <span className="text-indigo-400">1. Intent Analysis</span> →
            <span className="text-indigo-400">2. MCP Schema Discovery</span> →
            <span className="text-indigo-400">3. SQL Generation</span> →
            <span className="text-indigo-400">4. JSqlParser Validation</span> →
            <span className="text-indigo-400">5. MCP Execution</span> →
            <span className="text-indigo-400">6. Evidence Synthesis</span>
          </div>
        </div>
      )}

      {/* Error Alert Banner */}
      {error && (
        <div className="rounded-xl border border-rose-500/40 bg-rose-500/10 p-4 text-rose-300 text-xs space-y-1">
          <div className="flex items-center space-x-2 font-bold text-rose-200">
            <svg className="h-4 w-4 shrink-0 text-rose-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
            <span>Agent Pipeline Error</span>
          </div>
          <p className="font-mono pl-6 text-rose-300 break-words leading-relaxed">{error}</p>
        </div>
      )}

      {/* Clarification Alert Banner */}
      {currentResponse?.clarificationNeeded && (
        <div className="rounded-xl border border-amber-500/40 bg-amber-500/10 p-5 text-amber-200 text-sm space-y-2">
          <div className="flex items-center space-x-2 font-bold text-amber-300">
            <svg className="h-5 w-5 shrink-0 text-amber-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>Clarification Needed</span>
          </div>
          <p className="pl-7 text-xs text-amber-200 leading-relaxed">
            {currentResponse.clarificationPrompt}
          </p>
        </div>
      )}

      {/* Complete Query Response Display */}
      {currentResponse && !currentResponse.clarificationNeeded && (
        <div className="space-y-6">
          {/* Metadata & Query Info Header */}
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-800 bg-slate-900/60 p-4 text-xs text-slate-400">
            <div className="flex flex-wrap items-center gap-3">
              <span className="font-semibold text-slate-200">
                Question: &quot;{currentResponse.question}&quot;
              </span>
              <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-slate-800 text-indigo-300">
                {currentResponse.datasourceName} ({currentResponse.metadata.databaseEngine})
              </span>
            </div>
            <div className="flex items-center space-x-4 text-[11px] font-mono text-slate-500">
              <span>Model: <span className="text-slate-300">{currentResponse.metadata.llmModel}</span></span>
              <span>Time: <span className="text-slate-300">{currentResponse.metadata.executionTimeMs}ms</span></span>
              <span>Rows: <span className="text-slate-300">{currentResponse.rowCount}</span></span>
            </div>
          </div>

          {/* KPI Cards */}
          {currentResponse.visualization?.kpis && currentResponse.visualization.kpis.length > 0 && (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              {currentResponse.visualization.kpis.map((kpi, idx) => (
                <div key={idx} className="rounded-xl border border-slate-800 bg-slate-900/70 p-4 space-y-1">
                  <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">
                    {kpi.label}
                  </span>
                  <div className="text-2xl font-bold text-indigo-400 font-mono truncate">
                    {kpi.value}
                  </div>
                  <p className="text-[10px] text-slate-500">Evidence from MCP database query</p>
                </div>
              ))}
            </div>
          )}

          {/* AI Evidence-Based Insights Section */}
          <div className="rounded-2xl border border-indigo-500/20 bg-slate-900/90 p-5 space-y-4 shadow-md">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <div className="flex items-center space-x-2">
                <div className="flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-500/20 text-indigo-400">
                  <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                  </svg>
                </div>
                <h3 className="text-sm font-bold text-slate-100">
                  AI-Generated Evidence Insights
                </h3>
              </div>
              <span className="text-[11px] text-slate-500">
                Ground truth from verified query execution
              </span>
            </div>

            {/* Executive Summary */}
            <p className="text-sm text-slate-200 leading-relaxed font-medium bg-slate-950/60 p-3.5 rounded-xl border border-slate-800">
              {currentResponse.insights.summary}
            </p>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-1">
              {/* Key Observations */}
              {currentResponse.insights.observations.length > 0 && (
                <div className="rounded-xl border border-slate-800/80 bg-slate-950/40 p-3.5 space-y-2">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-emerald-400 block">
                    Key Observations
                  </span>
                  <ul className="space-y-1.5 text-xs text-slate-300">
                    {currentResponse.insights.observations.map((obs, i) => (
                      <li key={i} className="flex items-start space-x-2">
                        <span className="text-emerald-400 shrink-0">•</span>
                        <span>{obs}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Trends / Comparisons */}
              {(currentResponse.insights.trends.length > 0 || currentResponse.insights.comparisons.length > 0) && (
                <div className="rounded-xl border border-slate-800/80 bg-slate-950/40 p-3.5 space-y-2">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-sky-400 block">
                    Trends & Comparisons
                  </span>
                  <ul className="space-y-1.5 text-xs text-slate-300">
                    {[...currentResponse.insights.trends, ...currentResponse.insights.comparisons].map((item, i) => (
                      <li key={i} className="flex items-start space-x-2">
                        <span className="text-sky-400 shrink-0">→</span>
                        <span>{item}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>

            {/* Strategic Recommendations */}
            {currentResponse.insights.recommendations.length > 0 && (
              <div className="rounded-xl border border-indigo-500/20 bg-indigo-950/20 p-3.5 space-y-2">
                <span className="text-[11px] font-bold uppercase tracking-wider text-indigo-300 block">
                  Actionable Recommendations (Derived from Data)
                </span>
                <ul className="space-y-1.5 text-xs text-indigo-200">
                  {currentResponse.insights.recommendations.map((rec, i) => (
                    <li key={i} className="flex items-start space-x-2">
                      <span className="text-indigo-400 shrink-0">✓</span>
                      <span>{rec}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>

          {/* Generated SQL Panel */}
          {currentResponse.sql && (
            <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-4 space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center space-x-2">
                  <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                    Generated & Validated SQL
                  </span>
                  <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-300 border border-emerald-500/20">
                    JSqlParser Verified
                  </span>
                </div>

                <div className="flex items-center space-x-2">
                  <button
                    type="button"
                    onClick={handleCopySql}
                    className="px-2.5 py-1 text-xs rounded-lg border border-slate-700 bg-slate-800 text-slate-300 hover:text-white hover:bg-slate-700 transition-colors"
                  >
                    {copiedSql ? "Copied!" : "Copy SQL"}
                  </button>

                  <button
                    type="button"
                    onClick={() => onOpenInManualWorkspace(currentResponse.sql!, currentResponse.datasourceId)}
                    className="flex items-center space-x-1.5 px-3 py-1 text-xs rounded-lg bg-indigo-600 text-white font-medium hover:bg-indigo-500 transition-colors"
                  >
                    <span>Open in SQL Editor</span>
                    <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                    </svg>
                  </button>
                </div>
              </div>

              <div className="overflow-x-auto rounded-lg border border-slate-800 bg-slate-950 p-3">
                <pre className="font-mono text-xs text-indigo-300 whitespace-pre-wrap break-words">
                  {currentResponse.sql}
                </pre>
              </div>
            </div>
          )}

          {/* Visualization Section (Recharts) */}
          {currentResponse.rows.length > 0 && effectiveChartType !== "table" && (
            <div className="rounded-xl border border-slate-800 bg-slate-900/70 p-5 space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-800 pb-4">
                <div>
                  <h3 className="text-sm font-bold text-slate-200">
                    {currentResponse.visualization?.title || "Visual Analytics"}
                  </h3>
                  <span className="text-[11px] text-slate-500 font-mono">
                    ({currentResponse.visualization?.yAxisColumn} by {currentResponse.visualization?.xAxisColumn})
                  </span>
                </div>

                {/* Chart Toggle */}
                <div className="inline-flex rounded-lg border border-slate-700 bg-slate-800 p-0.5 text-xs">
                  <button
                    type="button"
                    onClick={() => setActiveChartOverride("bar")}
                    className={`px-2.5 py-1 rounded-md transition-colors ${
                      effectiveChartType === "bar" ? "bg-indigo-600 text-white" : "text-slate-400 hover:text-slate-200"
                    }`}
                  >
                    Bar
                  </button>
                  <button
                    type="button"
                    onClick={() => setActiveChartOverride("line")}
                    className={`px-2.5 py-1 rounded-md transition-colors ${
                      effectiveChartType === "line" ? "bg-indigo-600 text-white" : "text-slate-400 hover:text-slate-200"
                    }`}
                  >
                    Line
                  </button>
                  <button
                    type="button"
                    onClick={() => setActiveChartOverride("pie")}
                    className={`px-2.5 py-1 rounded-md transition-colors ${
                      effectiveChartType === "pie" ? "bg-indigo-600 text-white" : "text-slate-400 hover:text-slate-200"
                    }`}
                  >
                    Pie
                  </button>
                </div>
              </div>

              {/* Chart Render Container */}
              <div className="w-full h-80 pt-2">
                <ResponsiveContainer width="100%" height="100%">
                  {effectiveChartType === "bar" ? (
                    <BarChart data={chartData} margin={{ top: 10, right: 20, left: 10, bottom: 40 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#334155" opacity={0.5} />
                      <XAxis
                        dataKey="name"
                        stroke="#94a3b8"
                        fontSize={11}
                        angle={-25}
                        textAnchor="end"
                        interval={0}
                        height={50}
                      />
                      <YAxis stroke="#94a3b8" fontSize={11} />
                      <Tooltip
                        contentStyle={{
                          backgroundColor: "#0f172a",
                          borderColor: "#334155",
                          borderRadius: "0.5rem",
                          color: "#f8fafc",
                          fontSize: "12px",
                        }}
                      />
                      <Bar dataKey="value" fill="#6366f1" radius={[4, 4, 0, 0]}>
                        {chartData.map((_, index) => (
                          <Cell key={`cell-${index}`} fill={CHART_PALETTE[index % CHART_PALETTE.length]} />
                        ))}
                      </Bar>
                    </BarChart>
                  ) : effectiveChartType === "line" ? (
                    <LineChart data={chartData} margin={{ top: 10, right: 20, left: 10, bottom: 40 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#334155" opacity={0.5} />
                      <XAxis
                        dataKey="name"
                        stroke="#94a3b8"
                        fontSize={11}
                        angle={-25}
                        textAnchor="end"
                        interval={0}
                        height={50}
                      />
                      <YAxis stroke="#94a3b8" fontSize={11} />
                      <Tooltip
                        contentStyle={{
                          backgroundColor: "#0f172a",
                          borderColor: "#334155",
                          borderRadius: "0.5rem",
                          color: "#f8fafc",
                          fontSize: "12px",
                        }}
                      />
                      <Line
                        type="monotone"
                        dataKey="value"
                        stroke="#6366f1"
                        strokeWidth={2.5}
                        dot={{ fill: "#6366f1", r: 4 }}
                        activeDot={{ r: 6 }}
                      />
                    </LineChart>
                  ) : (
                    <PieChart>
                      <Tooltip
                        contentStyle={{
                          backgroundColor: "#0f172a",
                          borderColor: "#334155",
                          borderRadius: "0.5rem",
                          color: "#f8fafc",
                          fontSize: "12px",
                        }}
                      />
                      <Legend wrapperStyle={{ fontSize: "11px", color: "#94a3b8" }} />
                      <Pie
                        data={chartData}
                        dataKey="value"
                        nameKey="name"
                        cx="50%"
                        cy="50%"
                        outerRadius={100}
                        innerRadius={45}
                        paddingAngle={3}
                        label={({ name, percent }: { name?: string; percent?: number }) =>
                          `${name ?? "N/A"} (${((percent ?? 0) * 100).toFixed(0)}%)`
                        }
                      >
                        {chartData.map((_, index) => (
                          <Cell key={`cell-${index}`} fill={CHART_PALETTE[index % CHART_PALETTE.length]} />
                        ))}
                      </Pie>
                    </PieChart>
                  )}
                </ResponsiveContainer>
              </div>
            </div>
          )}

          {/* Results Data Table */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/70 p-5 space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-800 pb-4">
              <div className="flex items-center space-x-2">
                <h3 className="text-sm font-semibold text-slate-200">
                  Detailed Query Records
                </h3>
                <span className="text-xs px-2 py-0.5 rounded-full bg-slate-800 text-slate-400 font-mono">
                  {filteredRows.length} rows {filteredRows.length !== currentResponse.rowCount && `(filtered from ${currentResponse.rowCount})`}
                </span>
              </div>

              <div className="flex flex-wrap items-center gap-3">
                <input
                  type="text"
                  placeholder="Filter results..."
                  value={tableSearch}
                  onChange={(e) => {
                    setTableSearch(e.target.value);
                    setCurrentPage(1);
                  }}
                  className="rounded-lg border border-slate-700 bg-slate-800 px-3 py-1.5 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500 w-44"
                />

                <button
                  type="button"
                  onClick={handleExportCsv}
                  className="flex items-center space-x-1.5 rounded-lg border border-slate-700 bg-slate-800 px-3 py-1.5 text-xs font-medium text-slate-200 hover:bg-slate-700 hover:text-white transition-all"
                >
                  <svg className="h-3.5 w-3.5 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                  </svg>
                  <span>Export CSV</span>
                </button>
              </div>
            </div>

            <div className="overflow-x-auto border border-slate-800/80 rounded-lg">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="border-b border-slate-800 bg-slate-950/60 text-slate-400 uppercase tracking-wider text-[11px]">
                    <th className="py-2.5 px-3 w-12 text-slate-500 font-mono">#</th>
                    {currentResponse.columns.map((col) => (
                      <th key={col} className="py-2.5 px-3 font-semibold text-slate-300">
                        {col}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/50 font-mono text-slate-300">
                  {paginatedRows.map((row, rowIdx) => {
                    const globalIndex = (currentPage - 1) * pageSize + rowIdx + 1;
                    return (
                      <tr key={rowIdx} className="hover:bg-slate-800/30 transition-colors">
                        <td className="py-2 px-3 text-slate-600 font-sans text-[11px]">{globalIndex}</td>
                        {currentResponse.columns.map((col) => (
                          <td key={col} className="py-2 px-3 truncate max-w-xs">
                            {row[col] !== null && row[col] !== undefined ? String(row[col]) : "—"}
                          </td>
                        ))}
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            <div className="flex flex-wrap items-center justify-between gap-3 pt-2 text-xs text-slate-400">
              <div className="flex items-center space-x-2">
                <span>Rows per page:</span>
                <select
                  value={pageSize}
                  onChange={(e) => {
                    setPageSize(Number(e.target.value));
                    setCurrentPage(1);
                  }}
                  className="rounded border border-slate-700 bg-slate-800 px-2 py-0.5 text-xs text-slate-200"
                >
                  <option value={10}>10</option>
                  <option value={15}>15</option>
                  <option value={25}>25</option>
                  <option value={50}>50</option>
                </select>
              </div>

              <div className="flex items-center space-x-3">
                <span>
                  Page {currentPage} of {totalPages}
                </span>
                <div className="inline-flex space-x-1">
                  <button
                    type="button"
                    onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                    disabled={currentPage <= 1}
                    className="px-2 py-1 rounded border border-slate-700 bg-slate-800 text-slate-300 hover:bg-slate-700 disabled:opacity-40"
                  >
                    Previous
                  </button>
                  <button
                    type="button"
                    onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                    disabled={currentPage >= totalPages}
                    className="px-2 py-1 rounded border border-slate-700 bg-slate-800 text-slate-300 hover:bg-slate-700 disabled:opacity-40"
                  >
                    Next
                  </button>
                </div>
              </div>
            </div>
          </div>

          {/* Follow-up Question Input Form */}
          <div className="rounded-2xl border border-indigo-500/30 bg-slate-900/90 p-5 space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold uppercase tracking-wider text-indigo-400">
                Continue Conversation (Session: {currentResponse.sessionId.substring(0, 8)}...)
              </span>
              <span className="text-[11px] text-slate-500">
                Maintains context from previous queries
              </span>
            </div>

            <form onSubmit={handleFollowUpSubmit} className="relative flex items-center">
              <input
                type="text"
                value={followUpQuestion}
                onChange={(e) => setFollowUpQuestion(e.target.value)}
                disabled={loading}
                placeholder="Ask follow-up (e.g. 'Break it down by category', 'Filter for delivered only')..."
                className="w-full rounded-xl border border-slate-700 bg-slate-950 px-4 py-3 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500 pr-28"
              />
              <button
                type="submit"
                disabled={loading || !followUpQuestion.trim()}
                className="absolute right-2 flex items-center space-x-1.5 rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white hover:bg-indigo-500 disabled:opacity-40 transition-all"
              >
                <span>Send</span>
                <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                </svg>
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
