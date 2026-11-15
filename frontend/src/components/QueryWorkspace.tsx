import React, { useState, useEffect } from "react";
import type { QueryResultResponse } from "../types/api";

interface ExampleQuery {
  title: string;
  description: string;
  sql: string;
}

const CRM_EXAMPLES: ExampleQuery[] = [
  {
    title: "Customers by Industry",
    description: "Count and distribution of enterprise customers across industries",
    sql: "SELECT industry, COUNT(*) AS customer_count FROM customers GROUP BY industry ORDER BY customer_count DESC;",
  },
  {
    title: "Pipeline by Stage",
    description: "Total opportunity deal value and count per sales stage",
    sql: "SELECT stage, SUM(amount) AS total_value, COUNT(*) AS count FROM opportunities GROUP BY stage ORDER BY total_value DESC;",
  },
  {
    title: "Lead Status Breakdown",
    description: "Distribution of inbound and qualified sales leads",
    sql: "SELECT status, COUNT(*) AS lead_count FROM leads GROUP BY status ORDER BY lead_count DESC;",
  },
  {
    title: "Activities by Type",
    description: "Volume of calls, emails, and meetings logged across accounts",
    sql: "SELECT activity_type, COUNT(*) AS activity_count FROM activities GROUP BY activity_type ORDER BY activity_count DESC;",
  },
  {
    title: "Top Accounts by Opportunity Pipeline",
    description: "Cross-table join between customers and opportunities",
    sql: "SELECT c.company, c.industry, COUNT(o.id) AS opportunity_count, SUM(o.amount) AS pipeline_value FROM customers c JOIN opportunities o ON c.id = o.customer_id GROUP BY c.id, c.company, c.industry ORDER BY pipeline_value DESC LIMIT 10;",
  },
];

const ECOMMERCE_EXAMPLES: ExampleQuery[] = [
  {
    title: "Orders & Revenue by Status",
    description: "Aggregate order counts and gross merchandise revenue by order status",
    sql: "SELECT order_status, COUNT(*) AS order_count, SUM(total_amount) AS revenue FROM orders GROUP BY order_status ORDER BY revenue DESC;",
  },
  {
    title: "Products & Pricing by Category",
    description: "Product inventory count and average pricing per category",
    sql: "SELECT category, COUNT(*) AS product_count, ROUND(AVG(price), 2) AS avg_price FROM products GROUP BY category ORDER BY product_count DESC;",
  },
  {
    title: "Payment Method Breakdown",
    description: "Transaction volumes and settled amounts by payment method",
    sql: "SELECT payment_method, COUNT(*) AS transactions, SUM(amount) AS total_paid FROM payments GROUP BY payment_method ORDER BY total_paid DESC;",
  },
  {
    title: "Top Products by Revenue",
    description: "Join order_items and products to identify best-selling inventory",
    sql: "SELECT p.product_name, p.category, SUM(oi.quantity) AS total_sold, SUM(oi.quantity * oi.unit_price) AS total_revenue FROM order_items oi JOIN products p ON oi.product_id = p.id GROUP BY p.id, p.product_name, p.category ORDER BY total_revenue DESC LIMIT 10;",
  },
  {
    title: "Customers by Country",
    description: "Geographic distribution of global customers",
    sql: "SELECT country, COUNT(*) AS customer_count FROM customers GROUP BY country ORDER BY customer_count DESC;",
  },
];

interface QueryWorkspaceProps {
  datasourceId: string;
  datasourceName: string;
  isDatasourceConnected: boolean;
  sql: string;
  setSql: (sql: string) => void;
  rowLimit: number;
  setRowLimit: (limit: number) => void;
  onExecuteQuery: () => void;
  loading: boolean;
  error: string | null;
  lastResult: QueryResultResponse | null;
}

export const QueryWorkspace: React.FC<QueryWorkspaceProps> = ({
  datasourceId,
  datasourceName,
  isDatasourceConnected,
  sql,
  setSql,
  rowLimit,
  setRowLimit,
  onExecuteQuery,
  loading,
  error,
  lastResult,
}) => {
  const [copied, setCopied] = useState<boolean>(false);

  const examples = datasourceId === "crm" ? CRM_EXAMPLES : ECOMMERCE_EXAMPLES;

  // Auto-populate default query if SQL is empty
  useEffect(() => {
    if (!sql.trim() && examples.length > 0) {
      setSql(examples[0].sql);
    }
  }, [datasourceId]);

  const handleCopy = () => {
    navigator.clipboard.writeText(sql);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    // Ctrl+Enter or Cmd+Enter to execute query
    if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
      e.preventDefault();
      if (!loading && isDatasourceConnected) {
        onExecuteQuery();
      }
    }
  };

  return (
    <div className="space-y-4">
      {/* Editor & Action Header */}
      <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-4 space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center space-x-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
              SQL Query Editor
            </span>
            <span className="text-xs font-mono px-2 py-0.5 rounded bg-slate-800 text-slate-300">
              {datasourceName}
            </span>
            <span className="hidden sm:inline-block text-[11px] text-slate-500">
              (Press <kbd className="px-1.5 py-0.5 rounded bg-slate-800 text-slate-300 text-[10px]">Ctrl+Enter</kbd> to execute)
            </span>
          </div>

          <div className="flex items-center space-x-2">
            {/* Row Limit Selector */}
            <div className="flex items-center space-x-1 text-xs text-slate-400">
              <span>Limit:</span>
              <select
                value={rowLimit}
                onChange={(e) => setRowLimit(Number(e.target.value))}
                className="rounded-lg border border-slate-700 bg-slate-800 px-2 py-1 text-xs text-slate-200 focus:outline-none focus:border-indigo-500"
              >
                <option value={50}>50 rows</option>
                <option value={100}>100 rows</option>
                <option value={200}>200 rows</option>
                <option value={500}>500 rows</option>
                <option value={1000}>1000 rows</option>
              </select>
            </div>

            {/* Copy button */}
            <button
              type="button"
              onClick={handleCopy}
              className="px-2.5 py-1 text-xs rounded-lg border border-slate-700 bg-slate-800 text-slate-300 hover:text-white hover:bg-slate-700 transition-colors"
              title="Copy SQL to Clipboard"
            >
              {copied ? "Copied!" : "Copy"}
            </button>

            {/* Clear button */}
            <button
              type="button"
              onClick={() => setSql("")}
              className="px-2.5 py-1 text-xs rounded-lg border border-slate-700 bg-slate-800 text-slate-400 hover:text-slate-200 hover:bg-slate-700 transition-colors"
            >
              Clear
            </button>

            {/* Execute Button */}
            <button
              type="button"
              onClick={onExecuteQuery}
              disabled={loading || !isDatasourceConnected || !sql.trim()}
              className="flex items-center space-x-2 rounded-lg bg-indigo-600 px-4 py-1.5 text-xs font-semibold text-white hover:bg-indigo-500 shadow-md shadow-indigo-600/20 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
            >
              {loading ? (
                <>
                  <svg className="animate-spin h-3.5 w-3.5" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
                  </svg>
                  <span>Executing...</span>
                </>
              ) : (
                <>
                  <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  <span>Run Query</span>
                </>
              )}
            </button>
          </div>
        </div>

        {/* Query Input Box */}
        <div className="relative">
          <textarea
            value={sql}
            onChange={(e) => setSql(e.target.value)}
            onKeyDown={handleKeyDown}
            rows={4}
            placeholder={`Enter read-only SQL query for ${datasourceName} (e.g. SELECT * FROM ...)`}
            className="w-full rounded-lg border border-slate-700 bg-slate-950 p-3 font-mono text-xs text-indigo-100 placeholder-slate-600 focus:outline-none focus:border-indigo-500 resize-y"
          />
        </div>

        {/* Quick Example Queries */}
        <div className="space-y-1.5 pt-1">
          <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">
            Suggested Queries for {datasourceName}:
          </span>
          <div className="flex flex-wrap gap-2">
            {examples.map((ex, idx) => (
              <button
                key={idx}
                type="button"
                onClick={() => setSql(ex.sql)}
                className="rounded-md border border-slate-800 bg-slate-800/60 px-2.5 py-1 text-[11px] text-slate-300 hover:border-indigo-500/50 hover:bg-slate-800 hover:text-white transition-all text-left"
                title={`${ex.description}\n\n${ex.sql}`}
              >
                <span className="font-medium">{ex.title}</span>
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Query Error Alert */}
      {error && (
        <div className="rounded-xl border border-rose-500/30 bg-rose-500/10 p-4 text-rose-300 text-xs space-y-1">
          <div className="flex items-center space-x-2 font-semibold text-rose-200">
            <svg className="h-4 w-4 shrink-0 text-rose-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>Query Execution Error</span>
          </div>
          <p className="font-mono pl-6 text-rose-300 leading-relaxed break-words">{error}</p>
        </div>
      )}

      {/* Query Execution Metadata Banner */}
      {lastResult && !error && (
        <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-slate-800 bg-slate-900/40 px-3.5 py-2 text-xs text-slate-400">
          <div className="flex items-center space-x-4">
            <span className="flex items-center space-x-1.5 text-emerald-400">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-400"></span>
              <span className="font-semibold text-slate-200">{lastResult.rowCount} rows</span> returned
            </span>
            <span>
              Columns: <span className="text-slate-200 font-mono">{lastResult.columns.length}</span>
            </span>
            {typeof lastResult.executionTimeMs === "number" && (
              <span>
                Elapsed: <span className="text-slate-200 font-mono">{lastResult.executionTimeMs}ms</span>
              </span>
            )}
            {lastResult.truncated && (
              <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-amber-500/10 text-amber-300 border border-amber-500/20">
                Truncated at {lastResult.rowLimit} rows limit
              </span>
            )}
          </div>
          <span className="text-[11px] font-mono text-slate-500">
            via MCP execute_readonly_query
          </span>
        </div>
      )}
    </div>
  );
};
