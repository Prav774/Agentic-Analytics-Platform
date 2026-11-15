export type DatasourceStatus = "CONNECTED" | "UNAVAILABLE";

export interface DatasourceSummary {
  id: string;
  name: string;
  description: string;
  status: DatasourceStatus;
  tools: string[];
}

export interface TableItem {
  schema: string;
  tableName: string;
}

export interface TablesResponse {
  schema: string;
  tables: TableItem[];
}

export interface ColumnDefinition {
  name: string;
  type: string;
  jdbcType: number;
  nullable: boolean;
  default: any;
  ordinalPosition: number;
  primaryKey: boolean;
}

export interface TableSchemaResponse {
  schema: string;
  tableName: string;
  columns: ColumnDefinition[];
}

export interface RelationshipItem {
  constraintName: string;
  sourceSchema: string;
  sourceTable: string;
  sourceColumn: string;
  targetSchema: string;
  targetTable: string;
  targetColumn: string;
  sequence: number;
}

export interface RelationshipsResponse {
  schema: string;
  relationships: RelationshipItem[];
}

export interface QueryRequestPayload {
  sql: string;
  rowLimit?: number;
}

export interface QueryResultResponse {
  columns: string[];
  rows: Record<string, any>[];
  rowCount: number;
  rowLimit: number;
  truncated: boolean;
  executionTimeMs?: number;
}

export interface ApiErrorResponse {
  status: number;
  error: string;
  message: string;
  timestamp: string;
}

export interface AnalyticsQueryRequestPayload {
  question: string;
  datasourceId?: string | null;
  sessionId?: string | null;
}

export interface QueryIntentInfo {
  datasourceId: string | null;
  businessMetric: string | null;
  dimensions: string[];
  filters: string[];
  dateRange: string | null;
  aggregation: string | null;
  analysisType: string;
  ambiguous: boolean;
  clarificationMessage: string | null;
}

export interface KpiItemInfo {
  label: string;
  value: string;
  rawValue?: number;
  format?: string;
}

export interface VisualizationConfigInfo {
  chartType: "kpi" | "bar" | "line" | "pie" | "table";
  xAxisColumn: string | null;
  yAxisColumn: string | null;
  title: string;
  kpis: KpiItemInfo[];
}

export interface EvidenceInsightInfo {
  summary: string;
  observations: string[];
  trends: string[];
  comparisons: string[];
  recommendations: string[];
}

export interface ExecutionMetadataInfo {
  executionTimeMs: number;
  llmModel: string;
  databaseEngine: string;
}

export interface AnalyticsQueryResponseData {
  sessionId: string;
  question: string;
  datasourceId: string;
  datasourceName: string;
  intent: QueryIntentInfo;
  sql: string | null;
  columns: string[];
  rows: Record<string, any>[];
  rowCount: number;
  truncated: boolean;
  visualization: VisualizationConfigInfo;
  insights: EvidenceInsightInfo;
  metadata: ExecutionMetadataInfo;
  clarificationNeeded: boolean;
  clarificationPrompt: string | null;
}
