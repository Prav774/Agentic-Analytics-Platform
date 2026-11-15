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
