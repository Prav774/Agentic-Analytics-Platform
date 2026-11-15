import axios, { AxiosError } from "axios";
import type {
  DatasourceSummary,
  TablesResponse,
  TableSchemaResponse,
  RelationshipsResponse,
  QueryResultResponse,
  QueryRequestPayload,
  ApiErrorResponse,
  AnalyticsQueryRequestPayload,
  AnalyticsQueryResponseData,
  AuthResponse,
  UserProfile,
  LoginPayload,
  RegisterPayload,
} from "../types/api";

const resolvedBaseUrl =
  typeof import.meta.env.VITE_API_BASE_URL === "string" &&
  import.meta.env.VITE_API_BASE_URL.trim() !== ""
    ? import.meta.env.VITE_API_BASE_URL.trim()
    : "http://127.0.0.1:8080";

export const apiClient = axios.create({
  baseURL: resolvedBaseUrl,
  headers: {
    "Content-Type": "application/json",
  },
  timeout: 45000,
});

// Request interceptor: Attach JWT Bearer token from localStorage
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("auth_token");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor: Dispatch unauthorized event on 401
apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      localStorage.removeItem("auth_token");
      window.dispatchEvent(new CustomEvent("auth:unauthorized"));
    }
    return Promise.reject(error);
  }
);

export const api = {
  getBaseUrl(): string {
    return resolvedBaseUrl;
  },

  // Authentication APIs
  async login(payload: LoginPayload): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>("/api/auth/login", payload);
    return response.data;
  },

  async register(payload: RegisterPayload): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>("/api/auth/register", payload);
    return response.data;
  },

  async getCurrentUser(): Promise<UserProfile> {
    const response = await apiClient.get<UserProfile>("/api/auth/me");
    return response.data;
  },

  // Datasource APIs
  async getDatasources(): Promise<DatasourceSummary[]> {
    const response = await apiClient.get<DatasourceSummary[]>("/api/datasources");
    return response.data;
  },

  async getTables(datasourceId: string): Promise<TablesResponse> {
    const response = await apiClient.get<TablesResponse>(
      `/api/datasources/${encodeURIComponent(datasourceId)}/tables`
    );
    return response.data;
  },

  async getTableSchema(
    datasourceId: string,
    tableName: string
  ): Promise<TableSchemaResponse> {
    const response = await apiClient.get<TableSchemaResponse>(
      `/api/datasources/${encodeURIComponent(datasourceId)}/tables/${encodeURIComponent(tableName)}`
    );
    return response.data;
  },

  async getRelationships(datasourceId: string): Promise<RelationshipsResponse> {
    const response = await apiClient.get<RelationshipsResponse>(
      `/api/datasources/${encodeURIComponent(datasourceId)}/relationships`
    );
    return response.data;
  },

  async executeQuery(
    datasourceId: string,
    sql: string,
    rowLimit = 200
  ): Promise<QueryResultResponse> {
    const startTime = performance.now();
    const payload: QueryRequestPayload = {
      sql,
      rowLimit,
    };
    const response = await apiClient.post<QueryResultResponse>(
      `/api/datasources/${encodeURIComponent(datasourceId)}/query`,
      payload
    );
    const duration = Math.round(performance.now() - startTime);
    return {
      ...response.data,
      executionTimeMs: duration,
    };
  },

  async executeNaturalLanguageQuery(
    payload: AnalyticsQueryRequestPayload
  ): Promise<AnalyticsQueryResponseData> {
    const response = await apiClient.post<AnalyticsQueryResponseData>(
      "/api/analytics/query",
      payload
    );
    return response.data;
  },
};

export function extractErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<ApiErrorResponse>;
    if (axiosError.response?.data?.message) {
      return axiosError.response.data.message;
    }
    if (axiosError.response?.status === 401) {
      return "Authentication required (401). Please sign in with your credentials.";
    }
    if (axiosError.response?.status === 403) {
      return "Access denied (403). Your role does not have permission for this operation.";
    }
    if (axiosError.response?.status === 404) {
      return `Resource not found (404): ${axiosError.config?.url ?? "endpoint"}`;
    }
    if (axiosError.response?.status === 503) {
      return "Datasource MCP Server is unavailable or unreachable (503).";
    }
    if (axiosError.response?.status === 504) {
      return "Query execution timed out (504). Please narrow your query or filter conditions.";
    }
    if (axiosError.code === "ECONNABORTED") {
      return "Request timed out after waiting for backend response.";
    }
    if (axiosError.code === "ERR_NETWORK") {
      return `Network error: Unable to reach backend at ${resolvedBaseUrl}. Check that the backend server is running.`;
    }
    return axiosError.message || "An unexpected HTTP error occurred.";
  }
  if (error instanceof Error) {
    return error.message;
  }
  return String(error || "Unknown error occurred.");
}
