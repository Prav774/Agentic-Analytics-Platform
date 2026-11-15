import { useState, useEffect, useCallback } from "react";
import { Header } from "./components/Header";
import { Sidebar, type ActiveTab } from "./components/Sidebar";
import { AiAnalyticsWorkspace } from "./components/AiAnalyticsWorkspace";
import { QueryWorkspace } from "./components/QueryWorkspace";
import { AnalyticsView } from "./components/AnalyticsView";
import { SchemaExplorer } from "./components/SchemaExplorer";
import { DatasourcesOverview } from "./components/DatasourcesOverview";
import { api, extractErrorMessage } from "./services/api";
import type {
  DatasourceSummary,
  QueryResultResponse,
  AnalyticsQueryResponseData,
} from "./types/api";

export default function App() {
  const [datasources, setDatasources] = useState<DatasourceSummary[]>([]);
  const [selectedDatasourceId, setSelectedDatasourceId] = useState<string>("ecommerce");
  const [activeTab, setActiveTab] = useState<ActiveTab>("ai");
  const [systemLoading, setSystemLoading] = useState<boolean>(true);
  const [systemError, setSystemError] = useState<string | null>(null);

  // Manual SQL Query State
  const [sql, setSql] = useState<string>("");
  const [rowLimit, setRowLimit] = useState<number>(200);
  const [queryLoading, setQueryLoading] = useState<boolean>(false);
  const [queryError, setQueryError] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<QueryResultResponse | null>(null);

  // AI Agentic Analytics State
  const [aiLoading, setAiLoading] = useState<boolean>(false);
  const [aiError, setAiError] = useState<string | null>(null);
  const [currentAiResponse, setCurrentAiResponse] = useState<AnalyticsQueryResponseData | null>(null);
  const [aiHistory, setAiHistory] = useState<AnalyticsQueryResponseData[]>([]);
  const [aiSessionId, setAiSessionId] = useState<string | null>(null);

  const [tableCount, setTableCount] = useState<number | undefined>(undefined);

  // Fetch datasources from backend
  const loadDatasources = useCallback(async () => {
    setSystemLoading(true);
    setSystemError(null);
    try {
      const data = await api.getDatasources();
      setDatasources(data);
      if (data.length > 0) {
        setSelectedDatasourceId((prev) => {
          const exists = data.some((d) => d.id === prev);
          return exists ? prev : data[0].id;
        });
      }
    } catch (err) {
      setSystemError(extractErrorMessage(err));
    } finally {
      setSystemLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDatasources();
  }, [loadDatasources]);

  // Load table count for the selected datasource
  useEffect(() => {
    if (!selectedDatasourceId) return;
    let isMounted = true;
    const fetchTableCount = async () => {
      try {
        const resp = await api.getTables(selectedDatasourceId);
        if (isMounted) {
          setTableCount(resp.tables?.length ?? 0);
        }
      } catch {
        if (isMounted) setTableCount(undefined);
      }
    };
    fetchTableCount();
    return () => {
      isMounted = false;
    };
  }, [selectedDatasourceId]);

  const handleSelectDatasource = (id: string) => {
    if (id !== selectedDatasourceId) {
      setSelectedDatasourceId(id);
      setQueryError(null);
      setLastResult(null);
      setSql("");
    }
  };

  // Execute Manual SQL Query
  const handleExecuteQuery = async () => {
    if (!sql.trim() || !selectedDatasourceId) return;

    setQueryLoading(true);
    setQueryError(null);
    try {
      const result = await api.executeQuery(selectedDatasourceId, sql, rowLimit);
      setLastResult(result);
    } catch (err) {
      setQueryError(extractErrorMessage(err));
    } finally {
      setQueryLoading(false);
    }
  };

  // Execute AI Natural Language Query
  const handleExecuteAiQuery = async (question: string, datasourceId?: string) => {
    setAiLoading(true);
    setAiError(null);

    const dsId = datasourceId || selectedDatasourceId;
    try {
      const response = await api.executeNaturalLanguageQuery({
        question,
        datasourceId: dsId,
        sessionId: aiSessionId,
      });

      setCurrentAiResponse(response);
      setAiSessionId(response.sessionId);
      setAiHistory((prev) => [...prev, response]);

      if (response.datasourceId && response.datasourceId !== selectedDatasourceId) {
        setSelectedDatasourceId(response.datasourceId);
      }
    } catch (err) {
      setAiError(extractErrorMessage(err));
    } finally {
      setAiLoading(false);
    }
  };

  const handleResetAiSession = () => {
    setAiSessionId(null);
    setCurrentAiResponse(null);
    setAiHistory([]);
    setAiError(null);
  };

  const handleOpenInManualWorkspace = (querySql: string, dsId: string) => {
    setSql(querySql);
    setSelectedDatasourceId(dsId);
    setActiveTab("manual-sql");
    setQueryError(null);
  };

  // Shortcut from SchemaExplorer
  const handleUseTableInQuery = (tableName: string) => {
    setSql(`SELECT * FROM ${tableName} LIMIT 50;`);
    setActiveTab("manual-sql");
    setQueryError(null);
  };

  const currentDatasource = datasources.find((d) => d.id === selectedDatasourceId);
  const isDatasourceConnected = currentDatasource?.status === "CONNECTED";

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans selection:bg-indigo-500 selection:text-white">
      {/* Top Navigation Header */}
      <Header
        datasources={datasources}
        loading={systemLoading}
        onRefresh={loadDatasources}
        apiBaseUrl={api.getBaseUrl()}
      />

      {/* Main Layout */}
      <div className="flex-1 flex flex-col lg:flex-row">
        {/* Left Sidebar */}
        <Sidebar
          datasources={datasources}
          selectedDatasourceId={selectedDatasourceId}
          onSelectDatasource={handleSelectDatasource}
          activeTab={activeTab}
          onSelectTab={setActiveTab}
          tableCount={tableCount}
        />

        {/* Content Body Area */}
        <main className="flex-1 p-4 lg:p-6 space-y-6 max-w-7xl mx-auto w-full">
          {/* Backend Connection Error Banner */}
          {systemError && (
            <div className="rounded-xl border border-rose-500/40 bg-rose-500/10 p-5 text-rose-300 text-sm space-y-2">
              <div className="flex items-center space-x-2 font-bold text-rose-200">
                <svg className="h-5 w-5 shrink-0 text-rose-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
                <span>Unable to reach Backend API ({api.getBaseUrl()})</span>
              </div>
              <p className="font-mono text-xs pl-7 text-rose-300 leading-relaxed">{systemError}</p>
              <div className="pl-7 pt-2 flex items-center space-x-3">
                <button
                  type="button"
                  onClick={loadDatasources}
                  className="px-3 py-1 rounded-lg bg-rose-600 text-white text-xs font-semibold hover:bg-rose-500 transition-colors"
                >
                  Retry Connection
                </button>
              </div>
            </div>
          )}

          {/* Tab 1: AI Natural Language Analytics Workspace */}
          {activeTab === "ai" && (
            <AiAnalyticsWorkspace
              datasources={datasources}
              selectedDatasourceId={selectedDatasourceId}
              onSelectDatasource={handleSelectDatasource}
              onExecuteAiQuery={handleExecuteAiQuery}
              loading={aiLoading}
              currentResponse={currentAiResponse}
              conversationHistory={aiHistory}
              onResetSession={handleResetAiSession}
              onOpenInManualWorkspace={handleOpenInManualWorkspace}
              error={aiError}
            />
          )}

          {/* Tab 2: Manual SQL Query Workspace */}
          {activeTab === "manual-sql" && (
            <div className="space-y-6">
              <QueryWorkspace
                datasourceId={selectedDatasourceId}
                datasourceName={currentDatasource?.name || selectedDatasourceId}
                isDatasourceConnected={isDatasourceConnected}
                sql={sql}
                setSql={setSql}
                rowLimit={rowLimit}
                setRowLimit={setRowLimit}
                onExecuteQuery={handleExecuteQuery}
                loading={queryLoading}
                error={queryError}
                lastResult={lastResult}
              />

              <AnalyticsView result={lastResult} loading={queryLoading} />
            </div>
          )}

          {/* Tab 3: Schema Explorer */}
          {activeTab === "schema" && (
            <SchemaExplorer
              datasourceId={selectedDatasourceId}
              datasourceName={currentDatasource?.name || selectedDatasourceId}
              onUseTableInQuery={handleUseTableInQuery}
            />
          )}

          {/* Tab 4: Datasources Overview */}
          {activeTab === "datasources" && (
            <DatasourcesOverview
              datasources={datasources}
              selectedDatasourceId={selectedDatasourceId}
              onSelectDatasource={handleSelectDatasource}
              onRefresh={loadDatasources}
              loading={systemLoading}
            />
          )}
        </main>
      </div>
    </div>
  );
}
