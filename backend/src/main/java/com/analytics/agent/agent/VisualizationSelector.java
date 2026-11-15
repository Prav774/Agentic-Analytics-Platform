package com.analytics.agent.agent;

import com.analytics.agent.dto.KpiItem;
import com.analytics.agent.dto.QueryIntent;
import com.analytics.agent.dto.VisualizationConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class VisualizationSelector {

    public VisualizationConfig selectVisualization(
            String question,
            QueryIntent intent,
            List<String> columns,
            List<Map<String, Object>> rows) {

        if (rows == null || rows.isEmpty() || columns == null || columns.isEmpty()) {
            return new VisualizationConfig("table", null, null, "Query Results", List.of());
        }

        List<String> numericCols = new ArrayList<>();
        List<String> categoryCols = new ArrayList<>();
        List<String> dateCols = new ArrayList<>();

        for (String col : columns) {
            if (isDateColumn(col, rows)) {
                dateCols.add(col);
            } else if (isNumericColumn(col, rows)) {
                numericCols.add(col);
            } else {
                categoryCols.add(col);
            }
        }

        // Compute KPI cards for numeric columns
        List<KpiItem> kpiItems = computeKpis(numericCols, rows);

        // Case 1: Single-row aggregate result (e.g. SELECT SUM(amount), COUNT(*)...)
        if (rows.size() == 1 && !numericCols.isEmpty() && categoryCols.isEmpty() && dateCols.isEmpty()) {
            return new VisualizationConfig("kpi", null, numericCols.get(0), "Summary Metric", kpiItems);
        }

        // Case 2: Time-series / trend (has date column and numeric column)
        if (!dateCols.isEmpty() && !numericCols.isEmpty()) {
            String xCol = dateCols.get(0);
            String yCol = numericCols.get(0);
            String title = formatChartTitle(yCol, xCol);
            return new VisualizationConfig("line", xCol, yCol, title, kpiItems);
        }

        // Case 3: Categorical breakdown (has category column and numeric column)
        if (!categoryCols.isEmpty() && !numericCols.isEmpty()) {
            String xCol = categoryCols.get(0);
            String yCol = numericCols.get(0);
            String title = formatChartTitle(yCol, xCol);

            // Pie chart for small distributions (e.g., status, payment method <= 6 items)
            boolean isDistribution = xCol.toLowerCase().contains("status")
                    || xCol.toLowerCase().contains("stage")
                    || xCol.toLowerCase().contains("method");

            if (rows.size() <= 6 && isDistribution) {
                return new VisualizationConfig("pie", xCol, yCol, title, kpiItems);
            }

            return new VisualizationConfig("bar", xCol, yCol, title, kpiItems);
        }

        // Case 4: No numeric column or unsuitable for visual charts -> Tabular display
        return new VisualizationConfig("table", null, null, "Tabular Results (" + rows.size() + " rows)", kpiItems);
    }

    private boolean isDateColumn(String colName, List<Map<String, Object>> rows) {
        String lower = colName.toLowerCase();
        if (lower.contains("month") || lower.contains("date") || lower.contains("year")
                || lower.contains("day") || lower.contains("created_at") || lower.contains("order_date")) {
            return true;
        }
        return false;
    }

    private boolean isNumericColumn(String colName, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            Object val = row.get(colName);
            if (val != null) {
                if (val instanceof Number) return true;
                if (val instanceof String s) {
                    try {
                        Double.parseDouble(s.trim());
                        return true;
                    } catch (NumberFormatException ignored) {
                        return false;
                    }
                }
            }
        }
        return false;
    }

    private List<KpiItem> computeKpis(List<String> numericCols, List<Map<String, Object>> rows) {
        List<KpiItem> kpis = new ArrayList<>();
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);
        NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);

        for (String col : numericCols) {
            double sum = 0;
            int count = 0;
            for (Map<String, Object> r : rows) {
                Object val = r.get(col);
                if (val != null) {
                    double num = parseDouble(val);
                    sum += num;
                    count++;
                }
            }

            if (count > 0) {
                boolean isCurrency = isCurrencyColumn(col);
                String baseLabel = formatColumnLabel(col);
                String label = baseLabel.toLowerCase().startsWith("total") ? baseLabel : "Total " + baseLabel;
                String formatted = isCurrency ? currencyFormat.format(sum) : numberFormat.format(sum);
                kpis.add(new KpiItem(label, formatted, sum, isCurrency ? "currency" : "number"));

                if (rows.size() > 1) {
                    double avg = sum / count;
                    String avgLabel = baseLabel.toLowerCase().startsWith("avg") || baseLabel.toLowerCase().startsWith("average")
                            ? baseLabel : "Average " + baseLabel;
                    String avgFormatted = isCurrency ? currencyFormat.format(avg) : numberFormat.format(avg);
                    kpis.add(new KpiItem(avgLabel, avgFormatted, avg, isCurrency ? "currency" : "number"));
                }
            }

            // Keep top 3 KPIs
            if (kpis.size() >= 3) break;
        }

        return kpis;
    }

    private boolean isCurrencyColumn(String col) {
        String lower = col.toLowerCase();
        return lower.contains("revenue") || lower.contains("amount") || lower.contains("price")
                || lower.contains("value") || lower.contains("paid") || lower.contains("sales");
    }

    private double parseDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof BigDecimal bd) return bd.doubleValue();
        try {
            return Double.parseDouble(val.toString().trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String formatColumnLabel(String col) {
        String[] parts = col.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private String formatChartTitle(String yCol, String xCol) {
        return formatColumnLabel(yCol) + " by " + formatColumnLabel(xCol);
    }
}
