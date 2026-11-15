import React, { createContext, useContext, useState, useEffect, type ReactNode } from "react";
import type { UserProfile, LoginPayload, RegisterPayload } from "../types/api";
import { api, extractErrorMessage } from "../services/api";

interface AuthContextType {
  user: UserProfile | null;
  token: string | null;
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null;
  login: (payload: LoginPayload) => Promise<void>;
  register: (payload: RegisterPayload) => Promise<void>;
  logout: () => void;
  clearError: () => void;
  isAdmin: boolean;
  isAnalyst: boolean;
  isViewer: boolean;
  canExecuteManualSql: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [token, setToken] = useState<string | null>(() => localStorage.getItem("auth_token"));
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    const initAuth = async () => {
      const storedToken = localStorage.getItem("auth_token");
      if (storedToken) {
        try {
          const profile = await api.getCurrentUser();
          if (isMounted) {
            setUser(profile);
            setToken(storedToken);
          }
        } catch {
          if (isMounted) {
            localStorage.removeItem("auth_token");
            setUser(null);
            setToken(null);
          }
        }
      }
      if (isMounted) {
        setLoading(false);
      }
    };

    initAuth();

    const handleUnauthorized = () => {
      if (isMounted) {
        setUser(null);
        setToken(null);
      }
    };

    window.addEventListener("auth:unauthorized", handleUnauthorized);
    return () => {
      isMounted = false;
      window.removeEventListener("auth:unauthorized", handleUnauthorized);
    };
  }, []);

  const login = async (payload: LoginPayload) => {
    setError(null);
    try {
      const auth = await api.login(payload);
      localStorage.setItem("auth_token", auth.token);
      setToken(auth.token);
      setUser(auth.user);
    } catch (err) {
      const msg = extractErrorMessage(err);
      setError(msg);
      throw err;
    }
  };

  const register = async (payload: RegisterPayload) => {
    setError(null);
    try {
      const auth = await api.register(payload);
      localStorage.setItem("auth_token", auth.token);
      setToken(auth.token);
      setUser(auth.user);
    } catch (err) {
      const msg = extractErrorMessage(err);
      setError(msg);
      throw err;
    }
  };

  const logout = () => {
    localStorage.removeItem("auth_token");
    setUser(null);
    setToken(null);
    setError(null);
  };

  const role = user?.role;
  const isAdmin = role === "ADMIN";
  const isAnalyst = role === "ANALYST";
  const isViewer = role === "VIEWER";
  const canExecuteManualSql = isAdmin || isAnalyst;

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!user && !!token,
        loading,
        error,
        login,
        register,
        logout,
        clearError: () => setError(null),
        isAdmin,
        isAnalyst,
        isViewer,
        canExecuteManualSql,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
};
