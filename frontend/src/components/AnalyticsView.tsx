import React, { useState, useMemo } from "react";
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
import type { QueryResultResponse } from "../types/api";

interface AnalyticsViewProps {
  result: QueryResultResponse | null;
  loading: boolean;
}

type ChartType = "bar" | "line" | "pie" | "table-only";

const CHART_COLORS = [
  "#6366f1", // Indigo
  "#10b981", // Emerald
  "#f59e0b", // Amber
  "#06b6d4", // Cyan
  "#8b5cf6", // Purple
  "#ec4899", // Pink
  "#14b8a6", // Teal
  "#f97316", // Orange
  "#3b82f6", // Blue
  "#84cc16", // Lime
];

function formatValue(val: any): string {
  if (val === null || val === undefined) return "—";
  if (typeof val === "number") {
    return Number.isInteger(val) ? val.toLocaleString() : val.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
  if (typeof val === "boolean") return val ? "True" : "False";
  if (typeof val === "object") return JSON.stringify(val);
  return String(val);
}

function isNumericValue(val: any): boolean {
  if (typeof val === "number") return !Number.isNaN(val);
  if (typeof val === "string") {
    const trimmed = val.trim();
    if (trimmed === "") return false;
    const num = Number(trimmed);
    return !Number.isNaN(num);
  }
  return false;
}

function parseNumber(val: any): number {
  if (typeof val === "number") return val;
  const parsed = Number(val);
  return Number.isNaN(parsed) ? 0 : parsed;
}

export const AnalyticsView: React.FC<AnalyticsViewProps> = ({ result, loading }) => {
  const [chartType, setChartType] = useState<ChartType>("bar");
  const [selectedKeyCol, setSelectedKeyCol] = useState<string>("");
  const [selectedMetricCol, setSelectedMetricCol] = useState<string>("");
  const [tableSearch, setTableSearch] = useState<string>("");
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [pageSize, setPageSize] = useState<number>(15);

  // Column type classification
  const { numericColumns, categoricalColumns } = useMemo(() => {
    if (!result || !result.columns || result.rows.length === 0) {
      return { numericColumns: [], categoricalColumns: [] };
    }

    const numericCols: string[] = [];
    const catCols: string[] = [];

    result.columns.forEach((col) => {
      // Check first non-null sample row
      const sample = result.rows.find((r) => r[col] !== null && r[col] !== undefined);
      if (sample && isNumericValue(sample[col])) {
        numericCols.push(col);
      } else {
        catCols.push(col);
      }
    });

    return { numericColumns: numericCols, categoricalColumns: catCols };
  }, [result]);

  // Determine active category and metric columns
  const activeKeyCol = useMemo(() => {
    if (selectedKeyCol && result?.columns.includes(selectedKeyCol)) {
      return selectedKeyCol;
    }
    return categoricalColumns[0] || result?.columns[0] || "";
  }, [selectedKeyCol, categoricalColumns, result]);

  const activeMetricCol = useMemo(() => {
    if (selectedMetricCol && numericColumns.includes(selectedMetricCol)) {
      return selectedMetricCol;
    }
    return numericColumns[0] || "";
  }, [selectedMetricCol, numericColumns]);

  // KPI Calculations strictly from query result
  const kpiData = useMemo(() => {
    if (!result || result.rows.length === 0) return null;

    const rowCount = result.rowCount;
    if (numericColumns.length === 0) {
      return {
        rowCount,
        metrics: [],
      };
    }

    const metrics = numericColumns.slice(0, 3).map((col) => {
      let sum = 0;
      let min = Infinity;
      let max = -Infinity;
      let count = 0;

      result.rows.forEach((r) => {
        const val = r[col];
        if (isNumericValue(val)) {
          const num = parseNumber(val);
          sum += num;
          if (num < min) min = num;
          if (num > max) max = num;
          count++;
        }
      });

      const avg = count > 0 ? sum / count : 0;
      const isCurrency =
        col.toLowerCase().includes("amount") ||
        col.toLowerCase().includes("revenue") ||
        col.toLowerCase().includes("price") ||
        col.toLowerCase().includes("value") ||
        col.toLowerCase().includes("paid");

      return {
        columnName: col,
        sum,
        avg,
        min: count > 0 ? min : 0,
        max: count > 0 ? max : 0,
        isCurrency,
      };
    });

    return {
      rowCount,
      metrics,
    };
  }, [result, numericColumns]);

  // Data formatted for Recharts
  const chartData = useMemo(() => {
    if (!result || result.rows.length === 0 || !activeKeyCol || !activeMetricCol) {
      return [];
    }

    // Limit to top 25 data points for readable chart rendering
    return result.rows.slice(0, 25).map((row) => ({
      name: String(row[activeKeyCol] ?? "N/A"),
      value: parseNumber(row[activeMetricCol]),
    }));
  }, [result, activeKeyCol, activeMetricCol]);

  // Filtered & Paginated Table Rows
  const filteredRows = useMemo(() => {
    if (!result) return [];
    if (!tableSearch.trim()) return result.rows;
    const query = tableSearch.toLowerCase();
    return result.rows.filter((row) =>
      result.columns.some((col) => {
        const val = row[col];
        return val !== null && val !== undefined && String(val).toLowerCase().includes(query);
      })
    );
  }, [result, tableSearch]);

  const totalPages = Math.max(1, Math.ceil(filteredRows.length / pageSize));
  const paginatedRows = useMemo(() => {
    const start = (currentPage - 1) * pageSize;
    return filteredRows.slice(start, start + pageSize);
  }, [filteredRows, currentPage, pageSize]);

  // CSV Exporter
  const handleExportCSV = () => {
    if (!result || result.rows.length === 0) return;
    const headers = result.columns.join(",");
    const rows = result.rows.map((r) =>
      result.columns
        .map((col) => {
          const val = r[col];
          if (val === null || val === undefined) return "";
          const str = String(val).replace(/"/g, '""');
          return `"${str}"`;
        })
        .join(",")
    );
    const csvContent = [headers, ...rows].join("\n");
    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.setAttribute("href", url);
    link.setAttribute("download", `query_export_${Date.now()}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  // 1. Loading State
  if (loading) {
    return (
      <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-12 text-center text-slate-400 space-y-3">
        <svg className="animate-spin h-8 w-8 mx-auto text-indigo-500" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
        </svg>
        <p className="text-sm font-medium">Fetching and compiling analytics data...</p>
      </div>
    );
  }

  // 2. Empty State (No query run yet)
  if (!result) {
    return (
      <div className="rounded-xl border border-slate-800/80 bg-slate-900/40 p-12 text-center space-y-4">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
          <svg className="h-7 w-7" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
          </svg>
        </div>
        <div className="max-w-md mx-auto">
          <h3 className="text-base font-semibold text-slate-200">
            Analytics & Visualizations Ready
          </h3>
          <p className="text-xs text-slate-400 mt-1 leading-relaxed">
            Execute a query from the editor above to generate live charts, compute aggregate KPI cards, and explore raw tabular rows. No sample or mock metrics are shown.
          </p>
        </div>
      </div>
    );
  }

  // 3. Zero Rows State
  if (result.rows.length === 0) {
    return (
      <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-8 text-center text-slate-400">
        <p className="text-sm font-medium text-slate-300">
          Query executed successfully, but returned 0 rows.
        </p>
        <p className="text-xs text-slate-500 mt-1">
          Try broadening the filter criteria or querying a different table.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Real KPI Cards computed from actual query results */}
      {kpiData && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* Total Rows Card */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/70 p-4 space-y-1">
            <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">
              Total Records
            </span>
            <div className="text-2xl font-bold text-slate-100 font-mono">
              {kpiData.rowCount.toLocaleString()}
            </div>
            <p className="text-[11px] text-slate-400">
              Matched and returned rows
            </p>
          </div>

          {/* Dynamic Numeric Metrics Cards */}
          {kpiData.metrics.map((metric) => (
            <React.Fragment key={metric.columnName}>
              <div className="rounded-xl border border-slate-800 bg-slate-900/70 p-4 space-y-1">
                <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-400 truncate block">
                  Total {metric.columnName}
                </span>
                <div className="text-2xl font-bold text-indigo-400 font-mono truncate">
                  {metric.isCurrency ? "$" : ""}
                  {metric.sum.toLocaleString(undefined, {
                    minimumFractionDigits: metric.isCurrency ? 2 : 0,
                    maximumFractionDigits: metric.isCurrency ? 2 : 2,
                  })}
                </div>
                <p className="text-[11px] text-slate-400">
                  Aggregate sum across result set
                </p>
              </div>

              <div className="rounded-xl border border-slate-800 bg-slate-900/70 p-4 space-y-1">
                <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-400 truncate block">
                  Average {metric.columnName}
                </span>
                <div className="text-2xl font-bold text-emerald-400 font-mono truncate">
                  {metric.isCurrency ? "$" : ""}
                  {metric.avg.toLocaleString(undefined, {
                    minimumFractionDigits: metric.isCurrency ? 2 : 0,
                    maximumFractionDigits: 2,
                  })}
                </div>
                <p className="text-[11px] text-slate-400">
                  Mean average per row
                </p>
              </div>
            </React.Fragment>
          ))}
        </div>
      )}

      {/* Chart Visualization Section (if numeric column exists) */}
      {numericColumns.length > 0 && chartType !== "table-only" && (
        <div className="rounded-xl border border-slate-800 bg-slate-900/70 p-5 space-y-4">
          {/* Controls Bar */}
          <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-800 pb-4">
            <div className="flex items-center space-x-2">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Visual Analytics
              </span>
              <span className="text-xs text-slate-500 font-mono">
                ({activeMetricCol} by {activeKeyCol})
              </span>
            </div>

            <div className="flex flex-wrap items-center gap-3">
              {/* Category / X-Axis Column Selector */}
              <div className="flex items-center space-x-1.5 text-xs text-slate-400">
                <span>Category:</span>
                <select
                  value={activeKeyCol}
                  onChange={(e) => setSelectedKeyCol(e.target.value)}
                  className="rounded-lg border border-slate-700 bg-slate-800 px-2 py-1 text-xs text-slate-200 focus:outline-none focus:border-indigo-500"
                >
                  {result.columns.map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
              </div>

              {/* Metric / Y-Axis Column Selector */}
              <div className="flex items-center space-x-1.5 text-xs text-slate-400">
                <span>Metric:</span>
                <select
                  value={activeMetricCol}
                  onChange={(e) => setSelectedMetricCol(e.target.value)}
                  className="rounded-lg border border-slate-700 bg-slate-800 px-2 py-1 text-xs text-slate-200 focus:outline-none focus:border-indigo-500"
                >
                  {numericColumns.map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
              </div>

              {/* Chart Type Selector */}
              <div className="inline-flex rounded-lg border border-slate-700 bg-slate-800 p-0.5">
                <button
                  type="button"
                  onClick={() => setChartType("bar")}
                  className={`px-2.5 py-1 text-xs font-medium rounded-md transition-colors ${
                    chartType === "bar"
                      ? "bg-indigo-600 text-white"
                      : "text-slate-400 hover:text-slate-200"
                  }`}
                >
                  Bar
                </button>
                <button
                  type="button"
                  onClick={() => setChartType("line")}
                  className={`px-2.5 py-1 text-xs font-medium rounded-md transition-colors ${
                    chartType === "line"
                      ? "bg-indigo-600 text-white"
                      : "text-slate-400 hover:text-slate-200"
                  }`}
                >
                  Line
                </button>
                <button
                  type="button"
                  onClick={() => setChartType("pie")}
                  className={`px-2.5 py-1 text-xs font-medium rounded-md transition-colors ${
                    chartType === "pie"
                      ? "bg-indigo-600 text-white"
                      : "text-slate-400 hover:text-slate-200"
                  }`}
                >
                  Pie
                </button>
              </div>
            </div>
          </div>

          {/* Recharts Render Area */}
          <div className="w-full h-80 pt-2">
            <ResponsiveContainer width="100%" height="100%">
              {chartType === "bar" ? (
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
                    formatter={(value: any) => [formatValue(value), activeMetricCol]}
                  />
                  <Bar dataKey="value" name={activeMetricCol} fill="#6366f1" radius={[4, 4, 0, 0]}>
                    {chartData.map((_, index) => (
                      <Cell key={`cell-${index}`} fill={CHART_COLORS[index % CHART_COLORS.length]} />
                    ))}
                  </Bar>
                </BarChart>
              ) : chartType === "line" ? (
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
                    formatter={(value: any) => [formatValue(value), activeMetricCol]}
                  />
                  <Line
                    type="monotone"
                    dataKey="value"
                    name={activeMetricCol}
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
                    formatter={(value: any) => [formatValue(value), activeMetricCol]}
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
                      <Cell key={`cell-${index}`} fill={CHART_COLORS[index % CHART_COLORS.length]} />
                    ))}
                  </Pie>
                </PieChart>
              )}
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* Raw Data Table Section */}
      <div className="rounded-xl border border-slate-800 bg-slate-900/70 p-5 space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-800 pb-4">
          <div className="flex items-center space-x-2">
            <h3 className="text-sm font-semibold text-slate-200">
              Query Result Records
            </h3>
            <span className="text-xs px-2 py-0.5 rounded-full bg-slate-800 text-slate-400 font-mono">
              {filteredRows.length} rows {filteredRows.length !== result.rowCount && `(filtered from ${result.rowCount})`}
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            {/* Search Input */}
            <input
              type="text"
              placeholder="Search in results..."
              value={tableSearch}
              onChange={(e) => {
                setTableSearch(e.target.value);
                setCurrentPage(1);
              }}
              className="rounded-lg border border-slate-700 bg-slate-800 px-3 py-1.5 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500 w-44"
            />

            {/* CSV Export Button */}
            <button
              type="button"
              onClick={handleExportCSV}
              className="flex items-center space-x-1.5 rounded-lg border border-slate-700 bg-slate-800 px-3 py-1.5 text-xs font-medium text-slate-200 hover:bg-slate-700 hover:text-white transition-all"
            >
              <svg className="h-3.5 w-3.5 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
              </svg>
              <span>Export CSV</span>
            </button>
          </div>
        </div>

        {/* Scrollable Data Table */}
        <div className="overflow-x-auto border border-slate-800/80 rounded-lg">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-950/60 text-slate-400 uppercase tracking-wider text-[11px]">
                <th className="py-2.5 px-3 w-12 text-slate-500 font-mono">#</th>
                {result.columns.map((col) => {
                  const isNum = numericColumns.includes(col);
                  return (
                    <th key={col} className={`py-2.5 px-3 font-semibold ${isNum ? "text-right text-indigo-300" : "text-slate-300"}`}>
                      {col}
                    </th>
                  );
                })}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/50 font-mono text-slate-300">
              {paginatedRows.map((row, rowIdx) => {
                const globalIndex = (currentPage - 1) * pageSize + rowIdx + 1;
                return (
                  <tr key={rowIdx} className="hover:bg-slate-800/30 transition-colors">
                    <td className="py-2 px-3 text-slate-600 font-sans text-[11px]">{globalIndex}</td>
                    {result.columns.map((col) => {
                      const isNum = numericColumns.includes(col);
                      return (
                        <td key={col} className={`py-2 px-3 truncate max-w-xs ${isNum ? "text-right text-slate-200" : ""}`}>
                          {formatValue(row[col])}
                        </td>
                      );
                    })}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {/* Pagination Bar */}
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
              <option value={100}>100</option>
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
                className="px-2 py-1 rounded border border-slate-700 bg-slate-800 text-slate-300 hover:bg-slate-700 disabled:opacity-40 disabled:hover:bg-slate-800"
              >
                Previous
              </button>
              <button
                type="button"
                onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                disabled={currentPage >= totalPages}
                className="px-2 py-1 rounded border border-slate-700 bg-slate-800 text-slate-300 hover:bg-slate-700 disabled:opacity-40 disabled:hover:bg-slate-800"
              >
                Next
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
